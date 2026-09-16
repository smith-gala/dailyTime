package com.yyyplot.dailytime.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.yyyplot.dailytime.agent.SentenceAnalysis;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.config.DailyTimeProperties;
import com.yyyplot.dailytime.dto.task.TaskProgressCommand;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.worker.VideoRenderWorkerClient;
import com.yyyplot.dailytime.worker.protocol.RenderWorkerRequest;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;
import com.yyyplot.dailytime.worker.protocol.WorkerResult;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class RenderNode {
    private static final int PROGRESS_START = 70;
    private static final int PROGRESS_RANGE = 24;
    private static final int PROGRESS_MAXIMUM = 94;

    private final VideoRenderWorkerClient workerClient;
    private final TaskService taskService;
    private final DailyTimeProperties properties;

    public RenderNode(
            VideoRenderWorkerClient workerClient,
            TaskService taskService,
            DailyTimeProperties properties) {
        this.workerClient = workerClient;
        this.taskService = taskService;
        this.properties = properties;
    }

    public Path execute(
            AutomationTaskEntity task,
            SentenceAnalysis analysis,
            List<Path> sourceFiles) {
        try {
            RenderWorkerRequest request = createRequest(task, analysis, sourceFiles);
            WorkerResult result =
                    workerClient.execute(request, event -> updateProgress(task.getTaskId(), event));
            String output = result.payload().path("output").asText("");
            if (output.isBlank()) {
                throw new BusinessException(
                        ErrorCode.INVALID_WORKER_RESPONSE, "渲染 Worker 未返回成片路径");
            }
            return Path.of(output).normalize();
        } catch (IOException ioError) {
            throw new BusinessException(ErrorCode.WORKER_PROCESS_FAILED, "无法创建渲染任务", ioError);
        }
    }

    private RenderWorkerRequest createRequest(
            AutomationTaskEntity task,
            SentenceAnalysis analysis,
            List<Path> sourceFiles)
            throws IOException {
        Path projectRoot = Path.of(properties.projectRoot());
        Path taskDirectory =
                projectRoot.resolve(properties.taskDir()).resolve(task.getTaskId()).normalize();
        Path outputFile =
                projectRoot
                        .resolve(properties.outputDir())
                        .resolve(task.getTaskId() + ".mp4")
                        .normalize();
        Files.createDirectories(outputFile.getParent());
        return new RenderWorkerRequest(
                task.getTaskId(),
                task.getSentence(),
                analysis,
                sourceFiles,
                taskDirectory,
                outputFile,
                analysis.musicTag());
    }

    private void updateProgress(String taskId, WorkerProgressEvent event) {
        int progress =
                Math.min(PROGRESS_MAXIMUM, PROGRESS_START + event.percent() * PROGRESS_RANGE / 100);
        taskService.updateProgress(
                new TaskProgressCommand(taskId, TaskStatus.RENDERING, progress, event.message()));
    }
}
