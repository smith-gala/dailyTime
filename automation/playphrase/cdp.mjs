export class CdpError extends Error {
  constructor(message, details = undefined) {
    super(message);
    this.name = "CdpError";
    this.details = details;
  }
}

export class CdpConnection {
  constructor(url, { defaultTimeoutMs = 15000 } = {}) {
    this.url = url;
    this.defaultTimeoutMs = defaultTimeoutMs;
    this.socket = null;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Set();
  }

  async connect() {
    if (this.socket?.readyState === WebSocket.OPEN) return this;
    const socket = new WebSocket(this.url);
    this.socket = socket;
    await new Promise((resolve, reject) => {
      const timer = setTimeout(
        () => reject(new CdpError(`连接 CDP 超时：${this.url}`)),
        this.defaultTimeoutMs,
      );
      socket.addEventListener("open", () => {
        clearTimeout(timer);
        resolve();
      }, { once: true });
      socket.addEventListener("error", () => {
        clearTimeout(timer);
        reject(new CdpError(`无法连接 CDP：${this.url}`));
      }, { once: true });
    });
    socket.addEventListener("message", (event) => this.#handleMessage(event.data));
    socket.addEventListener("close", () => this.#handleClose());
    return this;
  }

  async call(method, params = {}, { sessionId, timeoutMs } = {}) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new CdpError("CDP 连接尚未建立。");
    }
    const id = this.nextId++;
    const message = { id, method, params };
    if (sessionId) message.sessionId = sessionId;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new CdpError(`CDP 调用超时：${method}`));
      }, timeoutMs ?? this.defaultTimeoutMs);
      this.pending.set(id, { resolve, reject, timer, method });
      this.socket.send(JSON.stringify(message));
    });
  }

  onEvent(handler) {
    this.listeners.add(handler);
    return () => this.listeners.delete(handler);
  }

  waitForEvent(method, predicate = () => true, { sessionId, timeoutMs } = {}) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        unsubscribe();
        reject(new CdpError(`等待 CDP 事件超时：${method}`));
      }, timeoutMs ?? this.defaultTimeoutMs);
      const unsubscribe = this.onEvent((message) => {
        if (message.method !== method) return;
        if (sessionId && message.sessionId !== sessionId) return;
        if (!predicate(message.params ?? {})) return;
        clearTimeout(timer);
        unsubscribe();
        resolve(message.params ?? {});
      });
    });
  }

  close() {
    if (this.socket && this.socket.readyState < WebSocket.CLOSING) {
      this.socket.close();
    }
  }

  #handleMessage(raw) {
    let message;
    try {
      message = JSON.parse(String(raw));
    } catch {
      return;
    }
    if (message.id) {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      clearTimeout(pending.timer);
      if (message.error) {
        pending.reject(
          new CdpError(`CDP 调用失败：${pending.method}: ${message.error.message}`, message.error),
        );
      } else {
        pending.resolve(message.result ?? {});
      }
      return;
    }
    for (const listener of this.listeners) listener(message);
  }

  #handleClose() {
    for (const { reject, timer, method } of this.pending.values()) {
      clearTimeout(timer);
      reject(new CdpError(`CDP 连接已关闭：${method}`));
    }
    this.pending.clear();
  }
}

export async function connectToBrowser(cdpHttpUrl, options = {}) {
  const base = cdpHttpUrl.replace(/\/$/, "");
  let version;
  try {
    const response = await fetch(`${base}/json/version`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    version = await response.json();
  } catch (error) {
    throw new CdpError(`OpenClaw Browser 未启动或 CDP 不可访问：${base}`, {
      cause: String(error),
    });
  }
  if (!version.webSocketDebuggerUrl) {
    throw new CdpError("CDP /json/version 没有返回 webSocketDebuggerUrl。");
  }
  return new CdpConnection(version.webSocketDebuggerUrl, options).connect();
}

async function attachTargetSession(connection, target) {
  const { sessionId } = await connection.call("Target.attachToTarget", {
    targetId: target.targetId,
    flatten: true,
  });
  for (const method of ["Page.enable", "Runtime.enable", "DOM.enable", "Accessibility.enable"]) {
    await connection.call(method, {}, { sessionId });
  }
  return { sessionId, targetId: target.targetId, url: target.url };
}

export async function createDedicatedPlayPhrasePage(
  connection,
  { url = "https://www.playphrase.me/" } = {},
) {
  const created = await connection.call("Target.createTarget", { url });
  if (!created.targetId) {
    throw new CdpError("CDP 创建 PlayPhrase 专用标签页时没有返回 targetId。");
  }
  const page = await attachTargetSession(connection, {
    targetId: created.targetId,
    url,
  });
  return { ...page, createdByDownloader: true };
}

export async function attachPlayPhrasePage(connection, { create = true } = {}) {
  const { targetInfos = [] } = await connection.call("Target.getTargets");
  let target = targetInfos.find(
    (item) => item.type === "page" && /^https:\/\/(?:www\.)?playphrase\.me\//i.test(item.url),
  );
  if (!target && create) {
    const created = await connection.call("Target.createTarget", {
      url: "https://www.playphrase.me/",
    });
    target = { targetId: created.targetId, url: "https://www.playphrase.me/" };
  }
  if (!target) throw new CdpError("找不到 PlayPhrase 页面。");
  return attachTargetSession(connection, target);
}

export async function evaluate(connection, sessionId, expression, {
  awaitPromise = true,
  returnByValue = true,
  timeoutMs,
} = {}) {
  const result = await connection.call("Runtime.evaluate", {
    expression,
    awaitPromise,
    returnByValue,
    userGesture: true,
  }, { sessionId, timeoutMs });
  if (result.exceptionDetails) {
    const description = result.exceptionDetails.exception?.description
      ?? result.exceptionDetails.text
      ?? "未知 JavaScript 异常";
    throw new CdpError(`页面脚本执行失败：${description}`, result.exceptionDetails);
  }
  return result.result?.value;
}

export async function delay(milliseconds) {
  await new Promise((resolve) => setTimeout(resolve, milliseconds));
}
