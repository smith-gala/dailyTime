package com.yyyplot.dailytime.workflow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.yyyplot.dailytime.config.DailyTimeProperties;
import com.yyyplot.dailytime.dto.task.TaskProgressCommand;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.enums.TaskStatus;
import com.yyyplot.dailytime.enums.WorkerErrorCode;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.worker.PlayPhraseWorkerClient;
import com.yyyplot.dailytime.worker.WorkerExecutionException;
import com.yyyplot.dailytime.worker.protocol.DownloadWorkerRequest;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;
import com.yyyplot.dailytime.worker.protocol.WorkerResult;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DownloadNode {
    private static final int PROGRESS_START = 40;
    private static final int PROGRESS_RANGE = 29;
    private static final int PROGRESS_MAXIMUM = 69;

    private final PlayPhraseWorkerClient workerClient;
    private final TaskService taskService;
    private final DailyTimeProperties properties;

    public DownloadNode(
            PlayPhraseWorkerClient workerClient,
            TaskService taskService,
            DailyTimeProperties properties) {
        this.workerClient = workerClient;
        this.taskService = taskService;
        this.properties = properties;
    }

    public List<Path> execute(AutomationTaskEntity task) {
        Path taskDirectory = taskDirectory(task.getTaskId());
        int clipCount = properties.worker().clipCount();
        DownloadWorkerRequest request =
                new DownloadWorkerRequest(
                        task.getTaskId(), task.getSentence(), taskDirectory, clipCount);
        WorkerResult result =
                workerClient.execute(request, event -> updateProgress(task.getTaskId(), event));
        List<Path> files = extractFiles(result.payload());
        if (files.size() < clipCount) {
            throw new WorkerExecutionException(
                    WorkerErrorCode.INSUFFICIENT_RESULTS,
                    "固定需要 " + clipCount + " 个素材，实际只下载到 " + files.size() + " 个");
        }
        return List.copyOf(files.subList(0, clipCount));
    }

    private List<Path> extractFiles(JsonNode payload) {
        JsonNode fileNodes = payload.path("files");
        if (!fileNodes.isArray()) {
            fileNodes = payload.path("downloaded");
        }
        List<Path> files = new ArrayList<>();
        if (!fileNodes.isArray()) {
            return files;
        }
        for (JsonNode fileNode : fileNodes) {
            String value = fileNode.isTextual() ? fileNode.asText() : fileNode.path("path").asText();
            if (!value.isBlank()) {
                files.add(Path.of(value));
            }
        }
        return files;
    }

    private Path taskDirectory(String taskId) {
        return Path.of(properties.projectRoot())
                .resolve(properties.taskDir())
                .resolve(taskId)
                .normalize();
    }

    private void updateProgress(String taskId, WorkerProgressEvent event) {
        int progress =
                Math.min(PROGRESS_MAXIMUM, PROGRESS_START + event.percent() * PROGRESS_RANGE / 100);
        taskService.updateProgress(
                new TaskProgressCommand(
                        taskId, TaskStatus.DOWNLOADING, progress, event.message()));
    }
}
