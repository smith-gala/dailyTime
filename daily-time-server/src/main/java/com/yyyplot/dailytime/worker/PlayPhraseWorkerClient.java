package com.yyyplot.dailytime.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.config.DailyTimeProperties;
import com.yyyplot.dailytime.enums.WorkerErrorCode;
import com.yyyplot.dailytime.worker.protocol.DownloadWorkerRequest;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;
import com.yyyplot.dailytime.worker.protocol.WorkerResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Component
public class PlayPhraseWorkerClient extends AbstractProcessWorkerClient
        implements WorkerClient<DownloadWorkerRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayPhraseWorkerClient.class);

    private final DailyTimeProperties properties;

    public PlayPhraseWorkerClient(
            ObjectMapper objectMapper,
            DailyTimeProperties properties) {
        super(objectMapper, Path.of(properties.projectRoot()));
        this.properties = properties;
    }

    @Override
    public WorkerResult execute(
            DownloadWorkerRequest request,
            Consumer<WorkerProgressEvent> progressConsumer) {
        return executeInternal(request, progressConsumer);
    }

    private WorkerResult executeInternal(
            DownloadWorkerRequest request,
            Consumer<WorkerProgressEvent> progressConsumer) {
        Path sourceDirectory = request.taskDirectory().resolve("sources");
        Path absoluteSourceDirectory = sourceDirectory.toAbsolutePath();
        Path outputDirectory = absoluteSourceDirectory.normalize();
        requireInside(request.taskDirectory(), outputDirectory);
        DailyTimeProperties.Worker workerProperties = properties.worker();
        Duration downloadTimeout = workerProperties.downloadTimeout();
        List<String> command =
                List.of(
                        workerProperties.nodeCommand(),
                        workerProperties.downloaderScript(),
                        "--sentence",
                        request.sentence(),
                        "--output-dir",
                        outputDirectory.toString(),
                        "--count",
                        String.valueOf(request.count()),
                        "--retries",
                        String.valueOf(workerProperties.retries()),
                        "--timeout-ms",
                        String.valueOf(downloadTimeout.toMillis()),
                        "--ndjson");
        WorkerProcessResult processResult =
                run(command, request.taskDirectory(), downloadTimeout, progressConsumer);
        JsonNode finalEvent = processResult.finalEvent();
        if (finalEvent == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_WORKER_RESPONSE, "下载 Worker 未返回结构化结果");
        }
        if ("error".equals(finalEvent.path("type").asText())) {
            throw toWorkerException(finalEvent, WorkerErrorCode.DOWNLOAD_FAILED);
        }
        JsonNode payload =
                "result".equals(finalEvent.path("type").asText())
                        ? finalEvent.path("data")
                        : finalEvent;
        boolean insufficient =
                processResult.exitCode() == 2
                        || "insufficient_results".equals(payload.path("status").asText());
        if (insufficient) {
            throw new WorkerExecutionException(
                    WorkerErrorCode.INSUFFICIENT_RESULTS, "下载阶段未获得固定数量的素材");
        }
        if (processResult.exitCode() != 0) {
            throw new BusinessException(ErrorCode.WORKER_PROCESS_FAILED, "下载 Worker 执行失败");
        }
        validateFiles(payload, request.taskDirectory());
        return new WorkerResult(payload);
    }

    private WorkerExecutionException toWorkerException(
            JsonNode event,
            WorkerErrorCode fallbackCode) {
        WorkerErrorCode errorCode;
        try {
            errorCode = WorkerErrorCode.valueOf(event.path("code").asText());
        } catch (IllegalArgumentException invalidErrorCode) {
            LOGGER.warn("下载 Worker 返回未知错误码：{}", event.path("code").asText());
            errorCode = fallbackCode;
        }
        return new WorkerExecutionException(
                errorCode,
                event.path("message").asText("Worker 执行失败"));
    }

    private void validateFiles(JsonNode payload, Path taskDirectory) {
        JsonNode files = payload.path("files");
        if (!files.isArray()) {
            files = payload.path("downloaded");
        }
        if (files.isArray()) {
            for (JsonNode fileNode : files) {
                String filePath =
                        fileNode.isTextual() ? fileNode.asText() : fileNode.path("path").asText();
                if (!filePath.isBlank()) {
                    Path workerFile = Path.of(filePath);
                    Path absoluteWorkerFile = workerFile.toAbsolutePath();
                    Path normalizedFile = absoluteWorkerFile.normalize();
                    requireInside(taskDirectory, normalizedFile);
                }
            }
        }
    }
}
