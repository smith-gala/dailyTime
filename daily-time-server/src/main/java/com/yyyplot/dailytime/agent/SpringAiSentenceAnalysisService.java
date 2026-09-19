package com.yyyplot.dailytime.agent;

import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


@Service
@ConditionalOnProperty(
        prefix = "daily-time",
        name = "agent-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SpringAiSentenceAnalysisService implements SentenceAnalysisService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SpringAiSentenceAnalysisService.class);
    private static final int MAXIMUM_MODEL_ATTEMPTS = 2;
    private static final String SYSTEM_PROMPT =
            "你负责分析一个英语口语句子。返回强类型结构：" +
                    "中文翻译、以 / 包裹的整句 IPA、自然讲解、恰好两条双语例句、固定"
                    + " MusicTag 枚举。不要输出额外说明。";

    /*
    声明了一个变量，我的 SpringAiService 对象里面，需要保存一个 ChatClient
    但它现在还没被赋值，因为有 final，Java 要求它创建对象时必须赋值（写构造器）
    */
    private final ChatClient chatClient;



    private final SentenceAnalysisValidator analysisValidator;
    private final String model;
    private final String requestEndpoint;

    public SpringAiSentenceAnalysisService(
            ChatClient.Builder builder,
            SentenceAnalysisValidator analysisValidator,
            @Value("${spring.ai.openai.chat.options.model:unknown}")
            String model,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}")
            String baseUrl,
            @Value("${spring.ai.openai.chat.completions-path:/v1/chat/completions}")
            String completionsPath) {
//      构造器注入
        this.chatClient = builder.build();

        this.analysisValidator = analysisValidator;
        this.model = model;
        this.requestEndpoint = buildRequestEndpoint(baseUrl, completionsPath);
    }

    @Override
    public SentenceAnalysis analyze(String sentence, String confirmedTranslation) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAXIMUM_MODEL_ATTEMPTS; attempt++) {
            try {
                SentenceAnalysis analysis = requestAnalysis(sentence, confirmedTranslation);
                return analysisValidator.validate(analysis, confirmedTranslation);
            } catch (RuntimeException requestException) {
                lastException = requestException;
                logRequestFailure(attempt, requestException);
            }
        }
        if (lastException instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(
                ErrorCode.MODEL_REQUEST_FAILED,
                "内容模型调用失败",
                lastException);
    }

    private SentenceAnalysis requestAnalysis(String sentence, String confirmedTranslation) {
        String userPrompt = createUserPrompt(sentence, confirmedTranslation);
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .entity(SentenceAnalysis.class);
    }

    private String createUserPrompt(String sentence, String confirmedTranslation) {
        if (confirmedTranslation == null || confirmedTranslation.isBlank()) {
            return "英文：" + sentence;
        }
        return "英文：" + sentence + "\n已确认中文（不可覆盖）：" + confirmedTranslation;
    }

    private void logRequestFailure(int attempt, RuntimeException requestException) {
        String exceptionType = requestException.getClass().getSimpleName();
        String exceptionMessage = requestException.getMessage();
        if (attempt < MAXIMUM_MODEL_ATTEMPTS) {
            LOGGER.warn(
                    "LLM 内容分析请求失败，将进行重试，attempt={}/{}, model={}, endpoint={}, errorType={}, errorMessage={}",
                    attempt,
                    MAXIMUM_MODEL_ATTEMPTS,
                    model,
                    requestEndpoint,
                    exceptionType,
                    exceptionMessage);
            return;
        }
        LOGGER.error(
                "LLM 内容分析请求最终失败，attempt={}/{}, model={}, endpoint={}, errorType={}, errorMessage={}",
                attempt,
                MAXIMUM_MODEL_ATTEMPTS,
                model,
                requestEndpoint,
                exceptionType,
                exceptionMessage,
                requestException);
    }

    private String buildRequestEndpoint(String baseUrl, String completionsPath) {
        String normalizedBaseUrl =
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedCompletionsPath =
                completionsPath.startsWith("/") ? completionsPath : "/" + completionsPath;
        return normalizedBaseUrl + normalizedCompletionsPath;
    }
}
