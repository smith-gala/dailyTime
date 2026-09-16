package com.yyyplot.dailytime.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.entity.NotificationOutboxEntity;
import com.yyyplot.dailytime.enums.NotificationType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class OpenClawFeishuNotificationSender implements FeishuNotificationSender {
    private static final Pattern FEISHU_OPEN_ID = Pattern.compile("ou_[A-Za-z0-9]+$");
    private static final int MAXIMUM_COMMAND_OUTPUT_LENGTH = 2000;

    private final ObjectMapper objectMapper;
    private final String openClawCommand;
    private final Duration timeout;

    public OpenClawFeishuNotificationSender(
            ObjectMapper objectMapper,
            @Value("${daily-time.notification.openclaw-command:D:/npm-global/openclaw.cmd}")
                    String openClawCommand,
            @Value("${daily-time.notification.send-timeout:2m}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.openClawCommand = openClawCommand;
        this.timeout = timeout;
    }

    @Override
    public void send(NotificationOutboxEntity notification) {
        String recipient = notification.getRecipientUserId();
        if (recipient == null || !FEISHU_OPEN_ID.matcher(recipient).matches()) {
            throw new IllegalArgumentException("通知收件人不是可信的飞书 ou_ 用户 ID");
        }

        JsonNode payload = parsePayload(notification.getPayloadJson());
        String message = payload.path("message").asText("");
        if (message.isBlank()) {
            throw new IllegalArgumentException("通知消息为空");
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("message");
        arguments.add("send");
        arguments.add("--channel");
        arguments.add("feishu");
        arguments.add("--target");
        arguments.add(recipient);
        arguments.add("--message");
        arguments.add(message);
        arguments.add("--json");

        if (notification.getNotificationType() == NotificationType.AWAITING_REVIEW) {
            String media = payload.path("media").asText("");
            Path mediaFile = media.isBlank() ? null : Path.of(media).normalize();
            if (mediaFile == null || !Files.isRegularFile(mediaFile)) {
                throw new IllegalStateException("待审核成片不存在或不是文件");
            }
            arguments.add("--media");
            arguments.add(mediaFile.toString());
        }

        execute(arguments);
    }

    private JsonNode parsePayload(String payloadJson) {
        try {
            return objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException parsingError) {
            throw new IllegalArgumentException("通知 Outbox payload 不是有效 JSON", parsingError);
        }
    }

    private void execute(List<String> arguments) {
        List<String> command = buildCommand(arguments);
        Path commandOutput = null;
        Process process = null;
        try {
            commandOutput = Files.createTempFile("daily-time-openclaw-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(commandOutput.toFile());
            process = processBuilder.start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("OpenClaw 飞书通知发送超时");
            }
            String output = readCommandOutput(commandOutput);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "OpenClaw 飞书通知失败，exit=" + process.exitValue() + "，output=" + output);
            }
        } catch (IOException ioError) {
            throw new IllegalStateException("无法启动 OpenClaw 通知命令", ioError);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenClaw 通知发送被中断", interrupted);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (commandOutput != null) {
                try {
                    Files.deleteIfExists(commandOutput);
                } catch (IOException ignored) {
                    // 临时日志删除失败不改变通知发送结果。
                }
            }
        }
    }

    private List<String> buildCommand(List<String> arguments) {
        String normalizedCommand = openClawCommand.toLowerCase(Locale.ROOT);
        if (isWindows() && (normalizedCommand.endsWith(".cmd") || normalizedCommand.endsWith(".bat"))) {
            StringBuilder commandLine = new StringBuilder("call ");
            commandLine.append(quoteForCmd(openClawCommand));
            for (String argument : arguments) {
                commandLine.append(' ').append(quoteForCmd(argument));
            }
            return List.of("cmd.exe", "/d", "/s", "/c", commandLine.toString());
        }

        List<String> command = new ArrayList<>();
        command.add(openClawCommand);
        command.addAll(arguments);
        return command;
    }

    private String quoteForCmd(String value) {
        // cmd.exe 没有可靠的参数数组接口；替换可打断引号或触发展开的字符。
        String safeValue =
                value.replace('"', '\'')
                        .replace('%', '％')
                        .replace('!', '！')
                        .replace('^', '＾')
                        .replace("\r", "")
                        .replace("\n", "\\n");
        return "\"" + safeValue + "\"";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String readCommandOutput(Path commandOutput) throws IOException {
        String output = new String(Files.readAllBytes(commandOutput), StandardCharsets.UTF_8).trim();
        if (output.length() <= MAXIMUM_COMMAND_OUTPUT_LENGTH) {
            return output;
        }
        return output.substring(0, MAXIMUM_COMMAND_OUTPUT_LENGTH);
    }
}
