package com.yyyplot.dailytime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentPromptFactoryTest {
    private final AgentPromptFactory promptFactory = new AgentPromptFactory();

    @Test
    void injectsLatestConversationTaskIntoSystemPrompt() {
        String prompt = promptFactory.systemPrompt("20260904_a1b2c3d4");

        assertThat(prompt)
                .contains("当前会话最近任务 ID 是 20260904_a1b2c3d4")
                .contains("用户只说进度、通过或取消时")
                .contains("失败任务不能重试");
    }

    @Test
    void forbidsGuessingWhenConversationHasNoTask() {
        assertThat(promptFactory.systemPrompt(null)).contains("不得猜测");
    }
}
