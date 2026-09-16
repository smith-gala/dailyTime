package com.yyyplot.dailytime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExplicitConfirmationDetectorTest {
    private final ExplicitConfirmationDetector detector = new ExplicitConfirmationDetector();

    @Test
    void recognizesOnlyTheRequestedAction() {
        assertThat(detector.approves("这个成片通过")).isTrue();
        assertThat(detector.approves("采用这个视频")).isTrue();
        assertThat(detector.approves("不采用这个视频")).isFalse();
        assertThat(detector.cancels("取消这个任务")).isTrue();
        assertThat(detector.cancels("这个成片不采用")).isTrue();
    }

    @Test
    void doesNotTreatQuestionAsConfirmation() {
        assertThat(detector.approves("这个视频能通过吗？")).isFalse();
        assertThat(detector.cancels("任务怎么样了")).isFalse();
    }
}
