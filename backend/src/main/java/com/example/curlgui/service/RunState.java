package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.curlgui.dto.RunMode;
import com.example.curlgui.dto.RunResultDto;

/**
 * Mutable, thread-safe state for one running loop, held in memory by
 * {@code RunMultipleService} and updated by {@code RequestLoopRunner} as results
 * come in. Holds only metadata per run - never a response body, header or cookie.
 */
class RunState {

    enum Status { RUNNING, DONE, STOPPED }

    final String id;
    final RunMode mode;
    final int total;
    final long startedAtNanos = System.nanoTime();

    volatile long finishedAtNanos;
    volatile Status status = Status.RUNNING;
    volatile long lastTouchedAtMillis = System.currentTimeMillis();

    final AtomicBoolean cancelled = new AtomicBoolean(false);
    final AtomicInteger completed = new AtomicInteger();
    final AtomicInteger successful = new AtomicInteger();
    final AtomicInteger redirects = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();

    private final List<RunResultDto> results = Collections.synchronizedList(new ArrayList<>());

    RunState(String id, RunMode mode, int total) {
        this.id = id;
        this.mode = mode;
        this.total = total;
    }

    /** Called once per finished iteration (from any worker thread). */
    void record(RunResultDto result) {
        results.add(result);
        completed.incrementAndGet();
        switch (result.classification()) {
            case RunClassification.SUCCESS -> successful.incrementAndGet();
            case RunClassification.REDIRECT -> redirects.incrementAndGet();
            default -> failed.incrementAndGet();
        }
    }

    /** A copy of results from {@code offset} onward (order = completion order). */
    List<RunResultDto> resultsFrom(int offset) {
        synchronized (results) {
            int size = results.size();
            int from = Math.max(0, Math.min(offset, size));
            return new ArrayList<>(results.subList(from, size));
        }
    }
}
