import path from "node:path";
import process from "node:process";

import { PlayPhraseDownloader, serializeError } from "../automation/playphrase/downloader.mjs";

function parseArgs(argv) {
  const result = {
    sentence: "",
    outputDir: "",
    count: 5,
    retries: 3,
    timeoutMs: 30000,
    cdpUrl: process.env.OPENCLAW_CDP_URL || "http://127.0.0.1:18800",
    probe: false,
    ndjson: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    const next = () => {
      index += 1;
      if (index >= argv.length) throw new Error(`参数缺少值：${value}`);
      return argv[index];
    };
    if (value === "--sentence") result.sentence = next();
    else if (value === "--output-dir") result.outputDir = next();
    else if (value === "--count") result.count = Number(next());
    else if (value === "--retries") result.retries = Number(next());
    else if (value === "--timeout-ms") result.timeoutMs = Number(next());
    else if (value === "--cdp-url") result.cdpUrl = next();
    else if (value === "--probe") result.probe = true;
    else if (value === "--ndjson") result.ndjson = true;
    else if (value === "--help" || value === "-h") result.help = true;
    else throw new Error(`未知参数：${value}`);
  }
  return result;
}

function usage() {
  return [
    "用法：",
    "  node scripts/download_playphrase.mjs --sentence <英文> --output-dir <目录>",
    "",
    "选项：",
    "  --probe              只搜索和读取结果数量，不下载",
    "  --count <数量>       默认 5",
    "  --retries <次数>     默认 3",
    "  --timeout-ms <毫秒>  默认 30000",
    "  --cdp-url <地址>     默认 http://127.0.0.1:18800",
    "  --ndjson             使用 Java Worker 的 NDJSON 协议输出",
  ].join("\n");
}

let options;
try {
  options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(usage());
    process.exit(0);
  }
  if (!options.sentence.trim()) throw new Error("必须提供 --sentence。");
  if (!options.probe && !options.outputDir.trim()) throw new Error("必须提供 --output-dir。");
  if (!Number.isInteger(options.count) || options.count < 1 || options.count > 20) {
    throw new Error("--count 必须是 1～20 的整数。");
  }
  if (!Number.isInteger(options.retries) || options.retries < 1 || options.retries > 10) {
    throw new Error("--retries 必须是 1～10 的整数。");
  }
} catch (error) {
  console.error(error.message);
  console.error(usage());
  process.exit(64);
}

const downloader = new PlayPhraseDownloader({
  cdpUrl: options.cdpUrl,
  outputDir: options.outputDir || path.join(process.cwd(), ".codex-tmp", "playphrase-probe"),
  count: options.count,
  retries: options.retries,
  timeoutMs: options.timeoutMs,
  logger: (message) => console.error(`[playphrase] ${message}`),
});

try {
  if (options.ndjson) {
    console.log(JSON.stringify({ type: "progress", percent: 5, stage: "DOWNLOAD_MEDIA", message: "正在连接 PlayPhrase" }));
  }
  await downloader.connect();
  const result = options.probe
    ? await downloader.probe(options.sentence)
    : await downloader.download(options.sentence);
  console.log(JSON.stringify(options.ndjson
    ? { type: "result", status: result.status === "insufficient_results" ? "INPUT_REQUIRED" : "SUCCESS", data: result }
    : result));
  process.exitCode = result.status === "insufficient_results" ? 2 : 0;
} catch (error) {
  const serialized = serializeError(error);
  console.log(JSON.stringify(options?.ndjson
    ? { type: "error", code: serialized.code || "DOWNLOAD_FAILED", message: serialized.message, retryable: true }
    : { status: "error", error: serialized }));
  process.exitCode = 1;
} finally {
  await downloader.close();
}
