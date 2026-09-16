import { mkdir, rename, rm, stat } from "node:fs/promises";
import path from "node:path";

import {
  CdpError,
  connectToBrowser,
  createDedicatedPlayPhrasePage,
  delay,
  evaluate,
} from "./cdp.mjs";

const PLAYPHRASE_URL = "https://www.playphrase.me/";
const POSITIVE_RESULT_STABLE_MS = 750;
const ZERO_RESULT_STABLE_MS = 10_000;
const ZERO_RESULT_CONFIRMATION_ATTEMPTS = 2;
const ZERO_RESULT_RETRY_DELAY_MS = 1_000;

export function requiredCounterStableMs(total) {
  return total > 0 ? POSITIVE_RESULT_STABLE_MS : ZERO_RESULT_STABLE_MS;
}

export class PlayPhraseError extends Error {
  constructor(code, message, details = undefined) {
    super(message);
    this.name = "PlayPhraseError";
    this.code = code;
    this.details = details;
  }
}

export function parseCounter(value) {
  const match = String(value ?? "").trim().match(/^(\d[\d,]*)\s*\/\s*(\d[\d,]*)$/);
  if (!match) return null;
  return {
    current: Number(match[1].replaceAll(",", "")),
    total: Number(match[2].replaceAll(",", "")),
  };
}

export function exactPhraseQuery(value) {
  const normalized = String(value ?? "").replace(/\s+/g, " ").trim();
  if (!normalized) return "";
  const withoutOuterQuotes = normalized
    .replace(/^["“”]\s*/, "")
    .replace(/\s*["“”]$/, "")
    .trim();
  return withoutOuterQuotes ? `"${withoutOuterQuotes}"` : "";
}

export class PlayPhraseDownloader {
  constructor({
    cdpUrl = "http://127.0.0.1:18800",
    outputDir,
    count = 5,
    retries = 3,
    timeoutMs = 30000,
    logger = () => {},
    connectBrowser = connectToBrowser,
    createPage = createDedicatedPlayPhrasePage,
  }) {
    if (!outputDir) throw new TypeError("outputDir 不能为空。");
    this.cdpUrl = cdpUrl;
    this.outputDir = path.resolve(outputDir);
    this.count = count;
    this.retries = retries;
    this.timeoutMs = timeoutMs;
    this.logger = logger;
    this.connectBrowser = connectBrowser;
    this.createPage = createPage;
    this.connection = null;
    this.sessionId = null;
    this.targetId = null;
    this.createdTargetIds = new Set();
    this.operationInProgress = false;
    this.unsubscribeTargetEvents = null;
    this.closingPopupTargets = new Set();
  }

  async connect() {
    await mkdir(this.outputDir, { recursive: true });
    await this.#connectBrowserConnection();
    try {
      await this.#createDedicatedPage();
      await this.#waitForPage();
    } catch (error) {
      if (!this.#isRecoverablePageError(error)) throw error;
      this.logger(`专用标签页初始化失败，重新创建一次：${error.message}`);
      await this.#replaceDedicatedPage();
    }
    return this;
  }

  async close() {
    this.unsubscribeTargetEvents?.();
    this.unsubscribeTargetEvents = null;
    let failedTargets = await this.#closeOwnedTargets();
    if (failedTargets.length) {
      try {
        this.connection?.close();
        this.connection = await this.connectBrowser(this.cdpUrl, {
          defaultTimeoutMs: this.timeoutMs,
        });
        failedTargets = await this.#closeOwnedTargets();
      } catch (error) {
        this.logger(`关闭专用标签页时无法重连 CDP：${error.message}`);
      }
    }
    if (failedTargets.length) {
      this.logger(`专用标签页关闭失败：${failedTargets.join(", ")}`);
    }
    this.connection?.close();
    this.connection = null;
    this.sessionId = null;
    this.targetId = null;
  }

  async probe(sentence) {
    return this.#runWithPageRecovery("probe", async () => {
      const counter = await this.#searchWithZeroResultConfirmation(sentence);
      return { sentence, ...counter };
    });
  }

  async download(sentence) {
    return this.#runWithPageRecovery("download", async () => {
      const initial = await this.#searchWithZeroResultConfirmation(sentence);
      if (initial.total < this.count) {
        return {
          status: "insufficient_results",
          sentence,
          requested: this.count,
          available: initial.total,
          files: [],
        };
      }
      if (initial.current !== 1) {
        throw new PlayPhraseError(
          "INDEX_MISMATCH",
          `搜索完成后播放器位置不是第 1 条：${initial.current}/${initial.total}`,
        );
      }

      const files = [];
      await this.#ensurePaused();
      for (let index = 1; index <= this.count; index += 1) {
        const counter = await this.#readCounter();
        if (counter.current !== index) {
          throw new PlayPhraseError(
            "INDEX_MISMATCH",
            `下载第 ${index} 条前，播放器位置异常：${counter.current}/${counter.total}`,
          );
        }
        await this.#ensurePaused();
        const file = await this.#downloadWithRetry(index);
        files.push(file);
        if (index === this.count) break;
        await this.#advance(index);
      }

      return {
        status: "success",
        sentence,
        requested: this.count,
        available: initial.total,
        files,
      };
    });
  }

  async #connectBrowserConnection() {
    this.unsubscribeTargetEvents?.();
    this.connection?.close();
    this.connection = await this.connectBrowser(this.cdpUrl, {
      defaultTimeoutMs: this.timeoutMs,
    });
    await this.connection.call("Target.setDiscoverTargets", { discover: true });
    this.unsubscribeTargetEvents = this.connection.onEvent((message) => {
      if (!["Target.targetCreated", "Target.targetInfoChanged"].includes(message.method)) return;
      const info = message.params?.targetInfo;
      if (!info || info.type !== "page" || info.openerId !== this.targetId) return;
      if (!this.#isBlockedPopupUrl(info.url) || this.closingPopupTargets.has(info.targetId)) return;
      this.closingPopupTargets.add(info.targetId);
      this.logger(`关闭当前任务打开的 Patreon 页面：${info.url}`);
      this.connection.call("Target.closeTarget", { targetId: info.targetId }).catch(() => {});
    });
    await this.connection.call("Browser.setDownloadBehavior", {
      behavior: "allowAndName",
      downloadPath: this.outputDir,
      eventsEnabled: true,
    });
  }

  async #createDedicatedPage() {
    const page = await this.createPage(this.connection);
    if (!page.createdByDownloader) {
      throw new PlayPhraseError("PAGE_OWNERSHIP_MISSING", "专用标签页没有下载器所有权标记。");
    }
    this.sessionId = page.sessionId;
    this.targetId = page.targetId;
    this.createdTargetIds.add(page.targetId);
    this.logger(`创建当前任务专用 PlayPhrase 标签页：${page.targetId}`);
    await this.#closeBlockedPopups();
  }

  async #replaceDedicatedPage() {
    await this.#closeOwnedTargets();
    try {
      await this.#createDedicatedPage();
      await this.#waitForPage();
    } catch (error) {
      this.logger(`原 CDP 连接无法重建标签页，正在重新连接 Browser：${error.message}`);
      await this.#connectBrowserConnection();
      await this.#closeOwnedTargets();
      await this.#createDedicatedPage();
      await this.#waitForPage();
    }
  }

  async #closeOwnedTargets() {
    const failed = [];
    for (const targetId of [...this.createdTargetIds]) {
      try {
        if (!this.connection) throw new Error("CDP 连接不存在");
        const result = await this.connection.call("Target.closeTarget", { targetId });
        if (result?.success === false) throw new Error("Target.closeTarget 返回 success=false");
        this.createdTargetIds.delete(targetId);
        this.logger(`关闭当前任务专用 PlayPhrase 标签页：${targetId}`);
      } catch {
        failed.push(targetId);
      }
    }
    if (!this.createdTargetIds.has(this.targetId)) {
      this.sessionId = null;
      this.targetId = null;
    }
    return failed;
  }

  async #runWithPageRecovery(label, operation) {
    if (this.operationInProgress) {
      throw new PlayPhraseError("DOWNLOADER_BUSY", "同一个下载器实例不能并发控制页面。");
    }
    this.operationInProgress = true;
    try {
      for (let attempt = 0; attempt < 2; attempt += 1) {
        try {
          return await operation();
        } catch (error) {
          if (attempt > 0 || !this.#isRecoverablePageError(error)) throw error;
          this.logger(`${label} 检测到页面或 CDP 会话失效，重新创建一次专用标签页：${error.message}`);
          await this.#replaceDedicatedPage();
        }
      }
      throw new PlayPhraseError("PAGE_RECOVERY_EXHAUSTED", `${label} 页面恢复重试已用尽。`);
    } finally {
      this.operationInProgress = false;
    }
  }

  #isRecoverablePageError(error) {
    if (error instanceof CdpError) return true;
    return ["PAGE_TIMEOUT", "SEARCH_TIMEOUT", "COUNTER_NOT_FOUND"].includes(error?.code)
      || /(?:target|session|context).*(?:closed|detached|not found|不存在|关闭)/i.test(error?.message ?? "");
  }

  async #waitForPage() {
    const deadline = Date.now() + this.timeoutMs;
    while (Date.now() < deadline) {
      const state = await evaluate(this.connection, this.sessionId, `(() => ({
        href: location.href,
        ready: document.readyState,
        hasSearch: Boolean(document.querySelector('#search-input')),
      }))()`);
      if (state.href.startsWith(PLAYPHRASE_URL) && state.ready === "complete" && state.hasSearch) {
        return;
      }
      await delay(250);
    }
    throw new PlayPhraseError("PAGE_TIMEOUT", "等待 PlayPhrase 页面加载超时。");
  }

  async #search(sentence) {
    const normalized = String(sentence ?? "").replace(/\s+/g, " ").trim();
    if (!normalized) throw new PlayPhraseError("INVALID_SENTENCE", "搜索句子不能为空。");
    const searchValue = exactPhraseQuery(normalized);
    this.logger(`精确搜索：${searchValue}`);
    await this.#ensurePaused().catch(() => {});
    const query = encodeURIComponent(searchValue).replace(/%20/g, "+");
    await evaluate(this.connection, this.sessionId, `(() => {
      location.hash = ${JSON.stringify(`#/search?q=${query}&language=en`)};
      return location.href;
    })()`);
    const loaded = this.connection.waitForEvent(
      "Page.loadEventFired",
      () => true,
      { sessionId: this.sessionId, timeoutMs: this.timeoutMs },
    );
    await this.connection.call("Page.reload", {}, { sessionId: this.sessionId });
    await loaded;
    await this.#waitForPage();

    const deadline = Date.now() + this.timeoutMs;
    let stableSince = null;
    let lastCounter = null;
    while (Date.now() < deadline) {
      const state = await this.#pageState();
      const valueMatches = state.query.trim().toLocaleLowerCase() === searchValue.toLocaleLowerCase();
      const isFirstResult = state.counter?.current === 1;
      if (valueMatches && state.counter) {
        await this.#ensurePaused().catch(() => {});
      }
      if (valueMatches && isFirstResult && state.counter) {
        const encoded = JSON.stringify(state.counter);
        if (encoded === lastCounter) {
          stableSince ??= Date.now();
          const requiredStableMs = requiredCounterStableMs(state.counter.total);
          if (Date.now() - stableSince >= requiredStableMs) {
            const verified = await this.#readCounter();
            if (verified.current === 1) {
              this.logger(`搜索结果数量：${verified.total}`);
              return verified;
            }
          }
        } else {
          lastCounter = encoded;
          stableSince = Date.now();
        }
      }
      await delay(100);
    }
    throw new PlayPhraseError("SEARCH_TIMEOUT", `等待搜索结果超时：${normalized}`);
  }

  async #searchWithZeroResultConfirmation(sentence) {
    for (let attempt = 1; attempt <= ZERO_RESULT_CONFIRMATION_ATTEMPTS; attempt += 1) {
      const searchCounter = await this.#search(sentence);
      const hasSearchResults = searchCounter.total > 0;
      const isLastAttempt = attempt === ZERO_RESULT_CONFIRMATION_ATTEMPTS;

      if (hasSearchResults || isLastAttempt) {
        if (!hasSearchResults) {
          this.logger(
            `连续 ${ZERO_RESULT_CONFIRMATION_ATTEMPTS} 次搜索结果均为 0，确认素材不足。`,
          );
        }
        return searchCounter;
      }

      this.logger(
        `第 ${attempt} 次搜索结果暂时为 0，等待后重新搜索确认。`,
      );
      await delay(ZERO_RESULT_RETRY_DELAY_MS);
    }

    throw new PlayPhraseError("SEARCH_CONFIRMATION_FAILED", "无法确认 PlayPhrase 搜索结果。");
  }

  async #pageState() {
    const value = await evaluate(this.connection, this.sessionId, `(() => {
      const counterText = document.querySelector('.search-result-count')?.textContent?.trim() || '';
      const videos = [...document.querySelectorAll('video')];
      let active = null;
      if (videos.length) {
        const rect = videos[0].getBoundingClientRect();
        active = document.elementsFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2)
          .find((element) => element.tagName === 'VIDEO') || null;
      }
      return {
        query: document.querySelector('#search-input')?.value || '',
        counterText,
        activeSrc: active?.currentSrc || '',
      };
    })()`);
    return { ...value, counter: parseCounter(value.counterText) };
  }

  async #readCounter() {
    const state = await this.#pageState();
    if (!state.counter) {
      throw new PlayPhraseError(
        "COUNTER_NOT_FOUND",
        `无法读取播放器结果计数：${state.counterText || "空"}`,
      );
    }
    return state.counter;
  }

  async #ensurePaused() {
    const result = await evaluate(this.connection, this.sessionId, `(() => {
      const videos = [...document.querySelectorAll('video')];
      if (!videos.length) return {found: false};
      const rect = videos[0].getBoundingClientRect();
      const active = document.elementsFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2)
        .find((element) => element.tagName === 'VIDEO');
      if (!active) return {found: false};
      const wasPaused = active.paused;
      if (!wasPaused) active.pause();
      return {found: true, wasPaused, paused: active.paused, src: active.currentSrc};
    })()`);
    if (!result.found || !result.paused) {
      throw new PlayPhraseError("PAUSE_FAILED", "无法安全暂停当前视频。");
    }
  }

  async #downloadWithRetry(index) {
    let lastError;
    for (let attempt = 1; attempt <= this.retries; attempt += 1) {
      try {
        this.logger(`下载第 ${index}/${this.count} 条（尝试 ${attempt}/${this.retries}）`);
        await this.#dismissBlockingModal();
        return await this.#downloadCurrent(index);
      } catch (error) {
        lastError = error;
        this.logger(`第 ${index} 条下载失败：${error.message}`);
        await this.#pressEscape();
        await delay(500);
      }
    }
    throw new PlayPhraseError(
      "DOWNLOAD_RETRIES_EXHAUSTED",
      `第 ${index} 条下载连续失败 ${this.retries} 次：${lastError?.message ?? "未知错误"}`,
      { cause: lastError?.details },
    );
  }

  async #downloadCurrent(index) {
    const existingDialog = await this.#findAxNode(
      "checkbox",
      /^Download original(?:\s|$)/i,
      false,
    );
    if (!existingDialog) {
      await this.#realClickSelector(".download-current-video-button");
      await this.#waitForDownloadDialog();
    }
    const checkbox = await this.#findAxNode("checkbox", /^Download original(?:\s|$)/i);
    const checked = this.#axProperty(checkbox, "checked");
    if (checked !== true) {
      await this.#clickAxNode(checkbox);
      await this.#waitForAxChecked(/^Download original(?:\s|$)/i);
    }
    const button = await this.#findAxNode("button", /^Download Original$/i);
    const { frameTree } = await this.connection.call("Page.getFrameTree", {}, {
      sessionId: this.sessionId,
    });
    const event = await this.#captureDownload(
      () => this.#clickAxNode(button),
      { index, frameId: frameTree?.frame?.id },
    );
    const source = path.join(this.outputDir, event.guid);
    const destination = path.join(this.outputDir, `${String(index).padStart(2, "0")}.mp4`);
    const sourceInfo = await stat(source).catch(() => null);
    if (!sourceInfo?.isFile() || sourceInfo.size <= 0) {
      throw new PlayPhraseError("DOWNLOAD_FILE_INVALID", `下载完成但 GUID 文件无效：${event.guid}`);
    }
    await rm(destination, { force: true });
    await rename(source, destination);
    const finalInfo = await stat(destination);
    return {
      index,
      path: destination,
      size: finalInfo.size,
      guid: event.guid,
      suggestedFilename: event.suggestedFilename,
    };
  }

  async #advance(previousIndex) {
    for (let attempt = 1; attempt <= this.retries; attempt += 1) {
      await this.#dismissBlockingModal();
      await this.#realClickForward();
      const deadline = Date.now() + this.timeoutMs;
      while (Date.now() < deadline) {
        const counter = await this.#readCounter().catch(() => null);
        if (counter?.current === previousIndex + 1) {
          await this.#dismissBlockingModal();
          await this.#ensurePaused();
          return;
        }
        await this.#dismissBlockingModal();
        await delay(200);
      }
      this.logger(`切换到第 ${previousIndex + 1} 条失败，准备重试 ${attempt}/${this.retries}`);
    }
    throw new PlayPhraseError(
      "ADVANCE_FAILED",
      `无法从第 ${previousIndex} 条切换到第 ${previousIndex + 1} 条。`,
    );
  }

  async #realClickForward() {
    const rect = await evaluate(this.connection, this.sessionId, `(() => {
      const image = [...document.querySelectorAll('.overlay-controls-icons .controls img')]
        .find((element) => (element.getAttribute('src') || '').endsWith('/forward.svg'));
      const target = image?.parentElement;
      if (!target) return null;
      const box = target.getBoundingClientRect();
      return box.width > 0 && box.height > 0
        ? {x: box.x + box.width / 2, y: box.y + box.height / 2}
        : null;
    })()`);
    if (!rect) throw new PlayPhraseError("FORWARD_NOT_FOUND", "找不到播放器下一条按钮。");
    await this.#dispatchMouseClick(rect.x, rect.y);
  }

  async #realClickSelector(selector) {
    const rect = await evaluate(this.connection, this.sessionId, `(() => {
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!element) return null;
      const style = getComputedStyle(element);
      const box = element.getBoundingClientRect();
      if (box.width <= 0 || box.height <= 0 || style.display === 'none' || style.visibility === 'hidden') return null;
      return {x: box.x + box.width / 2, y: box.y + box.height / 2};
    })()`);
    if (!rect) throw new PlayPhraseError("ELEMENT_NOT_FOUND", `找不到可点击元素：${selector}`);
    await this.#dispatchMouseClick(rect.x, rect.y);
  }

  async #dispatchMouseClick(x, y) {
    await this.connection.call("Input.dispatchMouseEvent", {
      type: "mouseMoved", x, y, button: "none",
    }, { sessionId: this.sessionId });
    await this.connection.call("Input.dispatchMouseEvent", {
      type: "mousePressed", x, y, button: "left", buttons: 1, clickCount: 1,
    }, { sessionId: this.sessionId });
    await this.connection.call("Input.dispatchMouseEvent", {
      type: "mouseReleased", x, y, button: "left", buttons: 0, clickCount: 1,
    }, { sessionId: this.sessionId });
  }

  async #waitForDownloadDialog() {
    const deadline = Date.now() + this.timeoutMs;
    while (Date.now() < deadline) {
      const checkbox = await this.#findAxNode("checkbox", /^Download original(?:\s|$)/i, false);
      const button = await this.#findAxNode("button", /^Download (?:Video|Original)$/i, false);
      if (checkbox && button) return;
      await delay(150);
    }
    throw new PlayPhraseError("DOWNLOAD_DIALOG_TIMEOUT", "下载设置弹窗没有出现。");
  }

  async #findAxNode(role, namePattern, required = true) {
    const { nodes = [] } = await this.connection.call(
      "Accessibility.getFullAXTree",
      {},
      { sessionId: this.sessionId },
    );
    const node = nodes.find((item) => {
      if (item.ignored) return false;
      const itemRole = String(item.role?.value ?? "").toLocaleLowerCase();
      const itemName = String(item.name?.value ?? "").trim();
      return itemRole === role.toLocaleLowerCase() && namePattern.test(itemName);
    });
    if (!node && required) {
      throw new PlayPhraseError("AX_NODE_NOT_FOUND", `找不到控件：${role} ${namePattern}`);
    }
    return node ?? null;
  }

  #axProperty(node, name) {
    const property = node?.properties?.find((item) => item.name === name);
    const value = property?.value?.value;
    if (value === "true") return true;
    if (value === "false") return false;
    return value;
  }

  async #clickAxNode(node) {
    if (!node?.backendDOMNodeId) {
      throw new PlayPhraseError("AX_NODE_NOT_CLICKABLE", "Accessibility 控件没有 backendDOMNodeId。");
    }
    const resolved = await this.connection.call("DOM.resolveNode", {
      backendNodeId: node.backendDOMNodeId,
    }, { sessionId: this.sessionId });
    const objectId = resolved.object?.objectId;
    if (!objectId) throw new PlayPhraseError("AX_NODE_NOT_CLICKABLE", "无法解析 Accessibility 控件。");
    await this.connection.call("Runtime.callFunctionOn", {
      objectId,
      functionDeclaration: "function () { this.click(); }",
      userGesture: true,
      returnByValue: true,
    }, { sessionId: this.sessionId });
  }

  async #waitForAxChecked(namePattern) {
    const deadline = Date.now() + this.timeoutMs;
    while (Date.now() < deadline) {
      const node = await this.#findAxNode("checkbox", namePattern, false);
      if (node && this.#axProperty(node, "checked") === true) return;
      await delay(100);
    }
    throw new PlayPhraseError("ORIGINAL_CHECK_FAILED", "无法选中 Download original。");
  }

  async #captureDownload(trigger, { index, frameId } = {}) {
    return new Promise((resolve, reject) => {
      let guid = null;
      let suggestedFilename = null;
      let receivedBytes = 0;
      let totalBytes = 0;
      let lastProgressBucket = -1;
      let settled = false;
      const finish = (callback, value) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        unsubscribe();
        callback(value);
      };
      const timer = setTimeout(() => {
        finish(reject, new PlayPhraseError("DOWNLOAD_TIMEOUT", "等待浏览器下载完成超时。", { guid }));
      }, this.timeoutMs);
      const unsubscribe = this.connection.onEvent((message) => {
        if (message.method === "Browser.downloadWillBegin" && !guid) {
          if (frameId && message.params.frameId !== frameId) return;
          guid = message.params.guid;
          suggestedFilename = message.params.suggestedFilename;
          this.logger(`浏览器开始下载：${suggestedFilename} (${guid})`);
          return;
        }
        if (message.method !== "Browser.downloadProgress" || !guid || message.params.guid !== guid) return;
        receivedBytes = message.params.receivedBytes ?? receivedBytes;
        totalBytes = message.params.totalBytes ?? totalBytes;
        if (totalBytes > 0) {
          const percent = Math.min(100, Math.floor((receivedBytes / totalBytes) * 10) * 10);
          if (percent > lastProgressBucket) {
            lastProgressBucket = percent;
            this.logger(`第 ${index}/${this.count} 条下载进度：${percent}% (${receivedBytes}/${totalBytes} bytes)`);
          }
        }
        if (message.params.state === "completed") {
          this.logger(`第 ${index}/${this.count} 条下载完成：${receivedBytes || totalBytes} bytes`);
          finish(resolve, { guid, suggestedFilename, receivedBytes, totalBytes });
        } else if (message.params.state === "canceled") {
          finish(reject, new PlayPhraseError("DOWNLOAD_CANCELED", "浏览器取消了下载。", { guid }));
        }
      });
      Promise.resolve()
        .then(trigger)
        .catch((error) => finish(reject, error));
    });
  }

  async #dismissBlockingModal() {
    const node = await this.#findAxNode(
      "button",
      /^(?:稍后再说|以后再说|Not now|Maybe later)$/i,
      false,
    );
    if (node) {
      this.logger("检测到高级功能弹窗，正在关闭。");
      await this.#clickAxNode(node);
      await delay(250);
      return true;
    }
    const visibleModal = await evaluate(this.connection, this.sessionId, `(() => {
      const candidates = [...document.querySelectorAll('[role="dialog"], dialog, [class*="modal" i]')];
      return candidates.some((element) => {
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
      });
    })()`);
    if (visibleModal) {
      await this.#pressEscape();
      await delay(250);
      return true;
    }
    return false;
  }

  #isBlockedPopupUrl(url) {
    return /^https:\/\/(?:www\.)?patreon\.com\//i.test(String(url ?? ""));
  }

  async #closeBlockedPopups() {
    const { targetInfos = [] } = await this.connection.call("Target.getTargets");
    for (const target of targetInfos) {
      if (target.type !== "page" || target.openerId !== this.targetId) continue;
      if (!this.#isBlockedPopupUrl(target.url)) continue;
      this.closingPopupTargets.add(target.targetId);
      this.logger(`清理当前任务已有 Patreon 弹页：${target.url}`);
      await this.connection.call("Target.closeTarget", { targetId: target.targetId }).catch(() => {});
    }
  }

  async #pressEscape() {
    for (const type of ["keyDown", "keyUp"]) {
      await this.connection.call("Input.dispatchKeyEvent", {
        type,
        key: "Escape",
        code: "Escape",
        windowsVirtualKeyCode: 27,
        nativeVirtualKeyCode: 27,
      }, { sessionId: this.sessionId }).catch(() => {});
    }
  }
}

export function serializeError(error) {
  if (error instanceof PlayPhraseError || error instanceof CdpError) {
    return {
      type: error.name,
      code: error.code ?? "CDP_ERROR",
      message: error.message,
      details: error.details,
    };
  }
  return {
    type: error?.name ?? "Error",
    code: "UNEXPECTED_ERROR",
    message: error?.message ?? String(error),
  };
}
