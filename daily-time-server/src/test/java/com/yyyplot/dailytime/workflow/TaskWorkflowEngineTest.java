package com.yyyplot.dailytime.workflow;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yyyplot.dailytime.agent.SentenceAnalysis;
import com.yyyplot.dailytime.entity.AutomationTaskEntity;
import com.yyyplot.dailytime.service.TaskService;
import com.yyyplot.dailytime.workflow.node.AnalyzeNode;
import com.yyyplot.dailytime.workflow.node.DownloadNode;
import com.yyyplot.dailytime.workflow.node.RenderNode;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.List;

class TaskWorkflowEngineTest {
    private static final String TASK_ID = "20260902_a1b2c3d4";

    @Test
    void executesOneStraightThroughWorkflowWithInMemoryResults() {
        TaskService taskService = mock(TaskService.class);
        AnalyzeNode analyzeNode = mock(AnalyzeNode.class);
        DownloadNode downloadNode = mock(DownloadNode.class);
        RenderNode renderNode = mock(RenderNode.class);
        AutomationTaskEntity task = new AutomationTaskEntity();
        task.setTaskId(TASK_ID);
        SentenceAnalysis analysis = mock(SentenceAnalysis.class);
        List<Path> files = List.of(Path.of("one.mp4"), Path.of("two.mp4"));
        Path videoFile = Path.of("result.mp4");
        when(taskService.claimTask(TASK_ID)).thenReturn(true);
        when(taskService.requireEntity(TASK_ID)).thenReturn(task);
        when(analyzeNode.execute(task)).thenReturn(analysis);
        when(downloadNode.execute(task)).thenReturn(files);
        when(renderNode.execute(task, analysis, files)).thenReturn(videoFile);

        new TaskWorkflowEngine(taskService, analyzeNode, downloadNode, renderNode)
                .execute(TASK_ID);

        InOrder order = inOrder(taskService, analyzeNode, downloadNode, renderNode);
        order.verify(taskService).claimTask(TASK_ID);
        order.verify(taskService).requireEntity(TASK_ID);
        order.verify(analyzeNode).execute(task);
        order.verify(taskService).markAnalyzed(TASK_ID);
        order.verify(downloadNode).execute(task);
        order.verify(taskService).markDownloaded(TASK_ID);
        order.verify(renderNode).execute(task, analysis, files);
        order.verify(taskService).markAwaitingReview(TASK_ID, videoFile);
    }

    @Test
    void exitsWhenAnotherWorkerAlreadyClaimedTask() {
        TaskService taskService = mock(TaskService.class);
        AnalyzeNode analyzeNode = mock(AnalyzeNode.class);
        DownloadNode downloadNode = mock(DownloadNode.class);
        RenderNode renderNode = mock(RenderNode.class);
        when(taskService.claimTask(TASK_ID)).thenReturn(false);

        new TaskWorkflowEngine(taskService, analyzeNode, downloadNode, renderNode)
                .execute(TASK_ID);

        verifyNoInteractions(analyzeNode, downloadNode, renderNode);
    }
}
