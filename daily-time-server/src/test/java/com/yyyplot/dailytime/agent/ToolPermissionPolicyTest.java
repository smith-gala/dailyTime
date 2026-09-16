package com.yyyplot.dailytime.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.enums.TaskStatus;

import org.junit.jupiter.api.Test;

class ToolPermissionPolicyTest {
    private final ToolPermissionPolicy policy = new ToolPermissionPolicy();

    @Test
    void permitsReviewOnlyWhileWaiting() {
        assertThatCode(() -> policy.requireAllowed("approve_task", TaskStatus.AWAITING_REVIEW))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAllowed("approve_task", TaskStatus.RENDERING))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> policy.requireAllowed("cancel_task", TaskStatus.AWAITING_REVIEW))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireAllowed("cancel_task", TaskStatus.DOWNLOADING))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blocksUnknownShellTool() {
        assertThatThrownBy(() -> policy.requireKnown("execute_shell"))
                .isInstanceOf(BusinessException.class);
    }

}
