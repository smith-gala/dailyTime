package com.yyyplot.dailytime.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.config.DailyTimeProperties;
import com.yyyplot.dailytime.enums.WorkerErrorCode;
import com.yyyplot.dailytime.worker.protocol.RenderWorkerRequest;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;
import com.yyyplot.dailytime.worker.protocol.WorkerResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class VideoRenderWorkerClient extends AbstractProcessWorkerClient
        implements WorkerClient<RenderWorkerRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoRenderWorkerClient.class);

    private final DailyTimeProperties properties;

    public VideoRenderWorkerClient(
            ObjectMapper objectMapper,
            DailyTimeProperties properties) {
        super(objectMapper, Path.of(properties.projectRoot()));
        this.properties = properties;
    }

    @Override
    public WorkerResult execute(
            RenderWorkerRequest request,
            Consumer<WorkerProgressEvent> progressConsumer) {
        return executeInternal(request, progressConsumer);
    }

    private WorkerResult executeInternal(
            RenderWorkerRequest request,
            Consumer<WorkerProgressEvent> progressConsumer) {
        try {
            Path requestFile = writeRenderRequest(request);
            List<String> command = createRenderCommand(requestFile);
            WorkerProcessResult processResult =
                    run(
                            command,
                            request.taskDirectory(),
                            properties.worker().renderTimeout(),
                            progressConsumer);
            JsonNode finalEvent = processResult.finalEvent();
            if (finalEvent != null && "error".equals(finalEvent.path("type").asText())) {
                throw toWorkerException(finalEvent);
            }
            if (processResult.exitCode() != 0) {
                throw new BusinessException(ErrorCode.MEDIA_RENDER_FAILED, "渲染 Worker 执行失败");
            }
            if (finalEvent == null || !"result".equals(finalEvent.path("type").asText())) {
                throw new BusinessException(
                        ErrorCode.INVALID_WORKER_RESPONSE, "渲染 Worker 未返回最终结果");
            }
            JsonNode payload = finalEvent.path("data");
            validateOutputPath(payload);
            return new WorkerResult(payload);
        } catch (BusinessException | WorkerExecutionException workerException) {
            throw workerException;
        } catch (IOException | RuntimeException requestException) {
            throw new BusinessException(
                    ErrorCode.WORKER_PROCESS_FAILED, "无法创建渲染请求", requestException);
        }
    }

    private Path writeRenderRequest(RenderWorkerRequest request) throws IOException {
        Files.createDirectories(request.taskDirectory());
        Path requestFile = request.taskDirectory().resolve("render-request.json");
        Map<String, Object> requestPayload = createRequestPayload(request);
        objectMapper.writeValue(requestFile.toFile(), requestPayload);
        return requestFile;
    }

    private Map<String, Object> createRequestPayload(RenderWorkerRequest request) {
        Map<String, Object> content =
                Map.of(
                        "sentence",
                        request.sentence(),
                        "chineseTranslation",
                        request.content().chineseTranslation(),
                        "phonetic",
                        request.content().phonetic(),
                        "videoDescription",
                        request.content().explanation());
        List<Path> sourcePaths = request.sources();
        List<String> sourceFiles = new ArrayList<>(sourcePaths.size());
        for (Path sourcePath : sourcePaths) {
            sourceFiles.add(sourcePath.toString());
        }
        return Map.of(
                "taskId",
                request.taskId(),
                "content",
                content,
                "sources",
                sourceFiles,
                "output",
                request.output().toString(),
                "musicTag",
                request.musicTag().name());
    }

    private List<String> createRenderCommand(Path requestFile) {
        return List.of(
                properties.worker().pythonCommand(),
                properties.worker().rendererScript(),
                "--request-file",
                requestFile.toString());
    }

    private void validateOutputPath(JsonNode payload) {
        JsonNode outputNode = payload.path("output");
        String output = outputNode.asText("");
        if (output.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_WORKER_RESPONSE, "渲染 Worker 未返回成片路径");
        }
        Path workerOutputFile = Path.of(output);
        Path absoluteOutputFile = workerOutputFile.toAbsolutePath();
        Path outputFile = absoluteOutputFile.normalize();
        Path outputDirectory = Path.of(properties.projectRoot()).resolve(properties.outputDir());
        requireInside(outputDirectory, outputFile);
    }

    private WorkerExecutionException toWorkerException(JsonNode event) {
        WorkerErrorCode errorCode;
        try {
            errorCode = WorkerErrorCode.valueOf(event.path("code").asText());
        } catch (IllegalArgumentException invalidErrorCode) {
            LOGGER.warn("渲染 Worker 返回未知错误码：{}", event.path("code").asText());
            errorCode = WorkerErrorCode.FFMPEG_FAILED;
        }
        return new WorkerExecutionException(
                errorCode,
                event.path("message").asText("渲染失败"));
    }
}
