package com.yyyplot.dailytime.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.enums.TaskStatus;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Set;

class TaskStateMachineTest {
    private final TaskStateMachine machine =
            new TaskStateMachine(org.mockito.Mockito.mock(ApplicationEventPublisher.class));

    @Test
    void checksEveryStatusPair() {
        Map<TaskStatus, Set<TaskStatus>> expected =
                Map.ofEntries(
                        Map.entry(
                                TaskStatus.QUEUED,
                                Set.of(TaskStatus.ANALYZING, TaskStatus.FAILED)),
                        Map.entry(
                                TaskStatus.ANALYZING,
                                Set.of(TaskStatus.DOWNLOADING, TaskStatus.FAILED)),
                        Map.entry(
                                TaskStatus.DOWNLOADING,
                                Set.of(TaskStatus.RENDERING, TaskStatus.FAILED)),
                        Map.entry(
                                TaskStatus.RENDERING,
                                Set.of(TaskStatus.AWAITING_REVIEW, TaskStatus.FAILED)),
                        Map.entry(
                                TaskStatus.AWAITING_REVIEW,
                                Set.of(TaskStatus.ADOPTED, TaskStatus.CANCELED)),
                        Map.entry(TaskStatus.ADOPTED, Set.of()),
                        Map.entry(TaskStatus.CANCELED, Set.of()),
                        Map.entry(TaskStatus.FAILED, Set.of()));
        for (TaskStatus sourceStatus : TaskStatus.values()) {
            for (TaskStatus targetStatus : TaskStatus.values()) {
                boolean expectedTransition = expected.get(sourceStatus).contains(targetStatus);
                assertThat(machine.canTransition(sourceStatus, targetStatus))
                        .as(sourceStatus + " -> " + targetStatus)
                        .isEqualTo(expectedTransition);
            }
        }
    }

    @Test
    void rejectsTerminalTransition() {
        assertThatThrownBy(() -> machine.requireTransition(TaskStatus.ADOPTED, TaskStatus.QUEUED))
                .isInstanceOf(BusinessException.class);
    }
}
