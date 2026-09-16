package com.yyyplot.dailytime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
