package com.yyyplot.dailytime.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class AbstractProcessWorkerClientTest {
    @TempDir
    Path root;

    @Test
    void consumesNdjsonFromBothProtocolEvents() {
        Path task = root.resolve("task");
        List<WorkerProgressEvent> progress = new ArrayList<>();
        TestClient client = new TestClient(root);
        String workerScript =
                "import json;"
                        + "print(json.dumps({'type':'progress','percent':25,"
                        + "'stage':'DOWNLOAD_MEDIA','message':'ok'}));"
                        + "print(json.dumps({'type':'result','status':'SUCCESS',"
                        + "'data':{'files':[]}}))";
        WorkerProcessResult result =
                client.call(List.of("python", "-c", workerScript), task, progress);
        assertThat(result.exitCode()).isZero();

        JsonNode finalEvent = result.finalEvent();
        JsonNode eventTypeNode = finalEvent.path("type");
        String eventType = eventTypeNode.asText();
        assertThat(eventType).isEqualTo("result");

        assertThat(progress).hasSize(1);
        int progressPercent = progress.get(0).percent();
        assertThat(progressPercent).isEqualTo(25);
    }

    @Test
    void rejectsDirectoryEscape() {
        TestClient client = new TestClient(root);
        assertThatThrownBy(() -> client.check(root.getParent()))
                .isInstanceOf(RuntimeException.class);
    }

    private static final class TestClient extends AbstractProcessWorkerClient {
        TestClient(Path root) {
            super(new ObjectMapper(), root);
        }

        WorkerProcessResult call(
                List<String> command,
                Path taskDirectory,
                List<WorkerProgressEvent> progressEvents) {
            return run(command, taskDirectory, Duration.ofSeconds(5), progressEvents::add);
        }

        void check(Path childPath) {
            requireInside(projectRoot(), childPath);
        }
    }
}
