package com.yyyplot.dailytime.notification;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.entity.NotificationOutboxEntity;
import com.yyyplot.dailytime.enums.NotificationType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

class OpenClawFeishuNotificationSenderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void invokesWindowsCmdWrapperWithoutShellInjectionFromMessage() throws Exception {
        Path commandDirectory = temporaryDirectory.resolve("open claw test");
        Files.createDirectories(commandDirectory);
        Path command = commandDirectory.resolve("fake-openclaw.cmd");
        Files.writeString(command, "@echo off\r\nexit /b 0\r\n", StandardCharsets.UTF_8);
        OpenClawFeishuNotificationSender sender =
                new OpenClawFeishuNotificationSender(
                        new ObjectMapper(), command.toString(), Duration.ofSeconds(10));
        NotificationOutboxEntity notification = new NotificationOutboxEntity();
        notification.setRecipientUserId("ou_User123");
        notification.setNotificationType(NotificationType.FAILED);
        notification.setPayloadJson(
                "{\"message\":\"失败：\\\"quoted\\\" & %PATH% ! ^\\n请稍后\"}");

        assertThatCode(() -> sender.send(notification)).doesNotThrowAnyException();
    }
}
