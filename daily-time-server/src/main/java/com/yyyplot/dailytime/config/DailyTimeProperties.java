package com.yyyplot.dailytime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;



/*
* 由 Spring Boot 完成配置绑定，并将配置对象注册为 Spring Bean。
* 使用对象接住配置文件里面的值
* */
@ConfigurationProperties("daily-time")
public record DailyTimeProperties(
        String projectRoot,
        String outputDir,
        String taskDir,
        boolean agentEnabled,
        Worker worker) {

    public record Worker(
            String nodeCommand,
            String pythonCommand,
            String downloaderScript,
            String rendererScript,
            Duration downloadTimeout,
            Duration renderTimeout,
            int clipCount,
            int retries) {
    }
}
