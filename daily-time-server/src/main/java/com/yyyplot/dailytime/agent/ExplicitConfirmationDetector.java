package com.yyyplot.dailytime.agent;

import org.springframework.stereotype.Component;

/** 对服务端保存的原始用户消息做动作专属确认，避免复用混合关键词。 */
@Component
public class ExplicitConfirmationDetector {
    public boolean approves(String message) {
        String text = normalizedStatement(message);
        return text != null
                && !text.contains("不通过")
                && !text.contains("不采用")
                && (text.contains("通过") || text.contains("采用"));
    }

    public boolean cancels(String message) {
        String text = normalizedStatement(message);
        return text != null && (text.contains("取消") || text.contains("不采用"));
    }

    public boolean recreatesAdoptedSentence(String message) {
        String text = normalizedStatement(message);
        return text != null && (text.contains("重新制作") || text.contains("重新生成"));
    }

    private String normalizedStatement(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String text = message.trim();
        if (text.contains("?")
                || text.contains("？")
                || text.contains("吗")
                || text.contains("是否")
                || text.contains("能不能")
                || text.contains("可不可以")) {
            return null;
        }
        return text;
    }
}
