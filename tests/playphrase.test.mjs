import assert from "node:assert/strict";
import { rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import { CdpError, createDedicatedPlayPhrasePage } from "../automation/playphrase/cdp.mjs";
import {
  exactPhraseQuery,
  parseCounter,
  PlayPhraseDownloader,
  requiredCounterStableMs,
} from "../automation/playphrase/downloader.mjs";

test("parseCounter reads the player result counter", () => {
  assert.deepEqual(parseCounter("5/46580"), { current: 5, total: 46580 });
  assert.deepEqual(parseCounter("1 / 1,234"), { current: 1, total: 1234 });
});

test("parseCounter rejects surrounding page text", () => {
  assert.equal(parseCounter("results 5/46580"), null);
  assert.equal(parseCounter(""), null);
});

test("exactPhraseQuery wraps the sentence in straight double quotes", () => {
  assert.equal(exactPhraseQuery("I got my period"), '"I got my period"');
  assert.equal(exactPhraseQuery('  "I got my period"  '), '"I got my period"');
  assert.equal(exactPhraseQuery("“I got my period”"), '"I got my period"');
});

test("zero search results require a longer stability period", () => {
  assert.equal(requiredCounterStableMs(199), 750);
  assert.equal(requiredCounterStableMs(1), 750);
  assert.equal(requiredCounterStableMs(0), 10_000);
});

test("createDedicatedPlayPhrasePage always creates a new target and marks ownership", async () => {
  const calls = [];
  const connection = {
    async call(method, params = {}, options = {}) {
      calls.push({ method, params, options });
      if (method === "Target.createTarget") return { targetId: "new-target" };
      if (method === "Target.attachToTarget") return { sessionId: "new-session" };
      return {};
    },
  };

  const page = await createDedicatedPlayPhrasePage(connection);
  assert.deepEqual(page, {
    sessionId: "new-session",
    targetId: "new-target",
    url: "https://www.playphrase.me/",
    createdByDownloader: true,
  });
  assert.equal(calls[0].method, "Target.createTarget");
  assert.equal(calls.some(({ method }) => method === "Target.getTargets"), false);
});

test("downloader close only closes targets it created and leaves Browser running", async () => {
  const closedTargets = [];
  let browserCloseCalled = false;
  const connection = {
    async call(method, params = {}) {
      if (method === "Target.closeTarget") closedTargets.push(params.targetId);
      return { success: true };
    },
    close() {},
  };
  const downloader = new PlayPhraseDownloader({ outputDir: ".test-output" });
  downloader.connection = connection;
  downloader.targetId = "owned-target";
  downloader.sessionId = "owned-session";
  downloader.createdTargetIds.add("owned-target");
  downloader.browserProcess = { close: () => { browserCloseCalled = true; } };

  await downloader.close();
  assert.deepEqual(closedTargets, ["owned-target"]);
  assert.equal(browserCloseCalled, false);
});

test("separate downloader instances track separate page ownership", () => {
  const first = new PlayPhraseDownloader({ outputDir: ".test-output-1" });
  const second = new PlayPhraseDownloader({ outputDir: ".test-output-2" });
  first.createdTargetIds.add("target-1");
  second.createdTargetIds.add("target-2");
  assert.deepEqual([...first.createdTargetIds], ["target-1"]);
  assert.deepEqual([...second.createdTargetIds], ["target-2"]);
});

test("connect recreates its dedicated page once after a stale CDP session", async () => {
  const closedTargets = [];
  const logs = [];
  let runtimeCalls = 0;
  let createdPages = 0;
  const connection = {
    async call(method, params = {}) {
      if (method === "Runtime.evaluate") {
        runtimeCalls += 1;
        if (runtimeCalls === 1) throw new CdpError("session closed");
        return {
          result: {
            value: {
              href: "https://www.playphrase.me/",
              ready: "complete",
              hasSearch: true,
            },
          },
        };
      }
      if (method === "Target.closeTarget") {
        closedTargets.push(params.targetId);
        return { success: true };
      }
      return {};
    },
    onEvent() { return () => {}; },
    close() {},
  };
  const outputDir = path.join(tmpdir(), `daily-time-playphrase-test-${process.pid}`);
  const downloader = new PlayPhraseDownloader({
    outputDir,
    connectBrowser: async () => connection,
    createPage: async () => {
      createdPages += 1;
      return {
        sessionId: `session-${createdPages}`,
        targetId: `target-${createdPages}`,
        url: "https://www.playphrase.me/",
        createdByDownloader: true,
      };
    },
    logger: (message) => logs.push(message),
  });

  await downloader.connect();
  assert.equal(createdPages, 2);
  assert.deepEqual(closedTargets, ["target-1"]);
  assert.equal(logs.some((line) => line.includes("重新创建一次")), true);
  await downloader.close();
  assert.deepEqual(closedTargets, ["target-1", "target-2"]);
  await rm(outputDir, { recursive: true, force: true });
});
