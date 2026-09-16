package com.yyyplot.dailytime.workflow;

import com.yyyplot.dailytime.agent.SentenceAnalysis;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.workflow.node.AnalyzeNode;
import com.yyyplot.dailytime.workflow.node.DownloadNode;
import com.yyyplot.dailytime.workflow.node.RenderNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class TaskWorkflowEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskWorkflowEngine.class);

    private final TaskService taskService;
    private final AnalyzeNode analyzeNode;
    private final DownloadNode downloadNode;
    private final RenderNode renderNode;

    public TaskWorkflowEngine(
            TaskService taskService,
            AnalyzeNode analyzeNode,
            DownloadNode downloadNode,
            RenderNode renderNode) {
        this.taskService = taskService;
        this.analyzeNode = analyzeNode;
        this.downloadNode = downloadNode;
        this.renderNode = renderNode;
    }

    public void execute(String taskId) {
        MDC.put("taskId", taskId);
        try {
            if (!taskService.claimTask(taskId)) {
                return;
            }
            AutomationTaskEntity task = taskService.requireEntity(taskId);

            MDC.put("stage", "ANALYZING");
            SentenceAnalysis analysis = analyzeNode.execute(task);
            taskService.markAnalyzed(taskId);

            MDC.put("stage", "DOWNLOADING");
            List<Path> files = downloadNode.execute(task);
            taskService.markDownloaded(taskId);

            MDC.put("stage", "RENDERING");
            Path videoFile = renderNode.execute(task, analysis, files);
            taskService.markAwaitingReview(taskId, videoFile);
        } catch (RuntimeException workflowError) {
            try {
                taskService.markFailed(taskId, workflowError);
            } catch (RuntimeException failureHandlingError) {
                LOGGER.error("任务失败状态写入失败，taskId={}", taskId, failureHandlingError);
            }
        } finally {
            MDC.remove("taskId");
            MDC.remove("stage");
        }
    }
}
