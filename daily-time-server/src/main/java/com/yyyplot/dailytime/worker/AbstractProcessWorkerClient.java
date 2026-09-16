package com.yyyplot.dailytime.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.common.exception.BusinessException;
import com.yyyplot.dailytime.common.exception.ErrorCode;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public abstract class AbstractProcessWorkerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractProcessWorkerClient.class);
    private static final Duration PROCESS_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration STREAM_READ_TIMEOUT = Duration.ofSeconds(10);

    protected final ObjectMapper objectMapper;
    private final Path projectRoot;

    protected AbstractProcessWorkerClient(ObjectMapper objectMapper, Path projectRoot) {
        this.objectMapper = objectMapper;
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    protected WorkerProcessResult run(
            List<String> command,
            Path taskDir,
            Duration timeout,
            Consumer<WorkerProgressEvent> progress) {
        try {
            Path safeTaskDirectory = prepareTaskDirectory(taskDir);
            Process process = startProcess(command);
            WorkerOutput workerOutput = readProcessOutput(process, timeout, progress);
            writeProcessArtifacts(safeTaskDirectory, process, workerOutput);
            JsonNode finalEvent = workerOutput.finalEvent();
            return new WorkerProcessResult(process.exitValue(), finalEvent);
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.WORKER_INTERRUPTED, "Worker 进程执行被中断");
        } catch (IOException | ExecutionException | TimeoutException processException) {
            throw new BusinessException(
                    ErrorCode.WORKER_PROCESS_FAILED, "Worker 进程启动或读取失败", processException);
        }
    }

    private Path prepareTaskDirectory(Path taskDirectory) throws IOException {
        Path safeTaskDirectory = taskDirectory.toAbsolutePath().normalize();
        requireInside(projectRoot, safeTaskDirectory);
        Files.createDirectories(safeTaskDirectory);
        return safeTaskDirectory;
    }

    private Process startProcess(List<String> command) throws IOException {
        // 只执行固定可执行文件和参数数组；绝不经过 cmd /c 或拼接 Shell 字符串。
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectRoot.toFile());
        processBuilder.redirectErrorStream(false);
        return processBuilder.start();
    }

    private WorkerOutput readProcessOutput(
            Process process,
            Duration timeout,
            Consumer<WorkerProgressEvent> progress)
            throws InterruptedException, ExecutionException, TimeoutException {
        List<JsonNode> events = Collections.synchronizedList(new ArrayList<>());
        StringBuilder standardOutput = new StringBuilder();
        StringBuilder standardError = new StringBuilder();
        try (ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> outputTask =
                    streamExecutor.submit(
                            () ->
                                    consumeStdout(
                                            process.getInputStream(),
                                            events,
                                            progress,
                                            standardOutput));
            Future<?> errorTask =
                    streamExecutor.submit(
                            () -> consumeStderr(process.getErrorStream(), standardError));
            waitForProcess(process, timeout);
            outputTask.get(STREAM_READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            errorTask.get(STREAM_READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        return new WorkerOutput(events, standardOutput.toString(), standardError.toString());
    }

    private void waitForProcess(Process process, Duration timeout) throws InterruptedException {
        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (completed) {
            return;
        }
        process.destroy();
        boolean stopped =
                process.waitFor(PROCESS_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!stopped) {
            process.destroyForcibly();
        }
        throw new BusinessException(ErrorCode.WORKER_TIMEOUT, "Worker 执行超时");
    }

    private void writeProcessArtifacts(
            Path taskDirectory,
            Process process,
            WorkerOutput workerOutput)
            throws IOException {
        appendLog(taskDirectory.resolve("worker-stdout.log"), workerOutput.stdout());
        appendLog(taskDirectory.resolve("worker-stderr.log"), workerOutput.stderr());
        String processMetadata =
                objectMapper.writeValueAsString(
                        Map.of("pid", process.pid(), "exitCode", process.exitValue()));
        Files.writeString(
                taskDirectory.resolve("worker-process.json"),
                processMetadata,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void appendLog(Path logFile, String content) throws IOException {
        Files.writeString(
                logFile,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private void consumeStdout(
            InputStream input,
            List<JsonNode> events,
            Consumer<WorkerProgressEvent> progress,
            StringBuilder rawOutput) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                rawOutput.append(line).append(System.lineSeparator());
                if (line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode event = objectMapper.readTree(line);
                    events.add(event);
                    if ("progress".equals(event.path("type").asText())) {
                        progress.accept(
                                new WorkerProgressEvent(
                                        event.path("percent").asInt(),
                                        event.path("message").asText()));
                    }
                } catch (JsonProcessingException parsingException) {
                    LOGGER.debug("忽略无法解析的 Worker 标准输出行", parsingException);
                }
            }
        } catch (IOException streamException) {
            LOGGER.debug("读取 Worker 标准输出失败", streamException);
        }
    }

    private void consumeStderr(InputStream input, StringBuilder target) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                target.append(line).append(System.lineSeparator());
            }
        } catch (IOException streamException) {
            LOGGER.debug("读取 Worker 标准错误输出失败", streamException);
        }
    }

    protected void requireInside(Path parent, Path child) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedParent)) {
            throw new BusinessException(ErrorCode.INVALID_WORKER_RESPONSE, "Worker 路径越过允许目录");
        }
    }

    protected Path projectRoot() {
        return projectRoot;
    }

    private record WorkerOutput(
            List<JsonNode> events,
            String stdout,
            String stderr) {

        private JsonNode finalEvent() {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }
}
