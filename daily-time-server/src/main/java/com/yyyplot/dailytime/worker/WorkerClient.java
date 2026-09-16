package com.yyyplot.dailytime.worker;

import com.yyyplot.dailytime.worker.protocol.WorkerProgressEvent;
import com.yyyplot.dailytime.worker.protocol.WorkerResult;

import java.util.function.Consumer;

public interface WorkerClient<T> {
    WorkerResult execute(T request, Consumer<WorkerProgressEvent> progress);
}
