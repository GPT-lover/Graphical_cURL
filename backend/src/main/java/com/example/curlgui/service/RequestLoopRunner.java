package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.example.curlgui.dto.RunMode;
import com.example.curlgui.dto.RunResultDto;
import com.example.curlgui.dto.SendRequestDto;

import jakarta.annotation.PreDestroy;

/**
 * Executes the actual loop for {@code RunMultipleService}, in SEQUENTIAL or
 * PARALLEL mode, recording each iteration's metadata into a {@link RunState}.
 *
 * <p>The "send one request" work is injected as a {@code Function} so this class
 * (and its tests) stay independent of {@code HttpClient}. It never creates a
 * thread per run - PARALLEL mode submits to a <b>bounded</b> pool of
 * {@link #PARALLEL_CONCURRENCY} workers, so 5000 runs still means at most 20
 * in flight.
 *
 * <p>Pacing between iterations is delegated to a {@link DelayPlan}: FIXED (the
 * original behaviour - the same delay between every iteration, never before
 * the first), JITTER (a fresh random delay per iteration) or WINDOW (a
 * pre-computed schedule spreading every iteration irregularly across a fixed
 * wall-clock window). The {@code long delayMs} overload is kept for existing
 * callers/tests and is exactly {@code DelayPlan.fixed(delayMs)}.
 */
@Component
class RequestLoopRunner {

    /** Max requests executing at once in PARALLEL mode. Not user-configurable. */
    static final int PARALLEL_CONCURRENCY = 20;

    private final ExecutorService workers = new ThreadPoolExecutor(
            PARALLEL_CONCURRENCY, PARALLEL_CONCURRENCY,
            30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            daemon("loop-worker"));

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    /**
     * Run the loop. Blocks until every iteration has finished (or, after a stop,
     * until the requests that had already started have finished). Results and
     * counts are written into {@code state} as they arrive.
     */
    void execute(RunState state, SendRequestDto resolved, long delayMs,
                 Function<SendRequestDto, RunOutcome> oneRun) {
        execute(state, resolved, DelayPlan.fixed(delayMs), oneRun);
    }

    /** Same as above, but with full control over pacing via {@link DelayPlan}. */
    void execute(RunState state, SendRequestDto resolved, DelayPlan delayPlan,
                 Function<SendRequestDto, RunOutcome> oneRun) {
        if (state.mode == RunMode.SEQUENTIAL) {
            runSequential(state, resolved, delayPlan, oneRun);
        } else {
            runParallel(state, resolved, delayPlan, oneRun);
        }
    }

    private void runSequential(RunState state, SendRequestDto resolved, DelayPlan delayPlan,
                               Function<SendRequestDto, RunOutcome> oneRun) {
        long startNanos = System.nanoTime();
        for (int run = 1; run <= state.total; run++) {
            if (state.cancelled.get()) {
                return;
            }
            waitFor(state, delayPlan, run, startNanos); // delay BETWEEN requests - not before the first (FIXED/JITTER)
            if (state.cancelled.get()) {
                return;
            }
            state.record(toResult(run, IterationContext.runWith(run, () -> oneRun.apply(resolved))));
        }
    }

    private void runParallel(RunState state, SendRequestDto resolved, DelayPlan delayPlan,
                             Function<SendRequestDto, RunOutcome> oneRun) {
        List<Future<?>> futures = new ArrayList<>();
        long startNanos = System.nanoTime();
        for (int run = 1; run <= state.total; run++) {
            if (state.cancelled.get()) {
                break; // stop starting new requests
            }
            waitFor(state, delayPlan, run, startNanos); // delay between STARTS; already-started ones keep running
            if (state.cancelled.get()) {
                break;
            }
            final int runNumber = run;
            futures.add(workers.submit(() -> {
                if (state.cancelled.get()) {
                    return; // a queued task that only got scheduled after Stop
                }
                state.record(toResult(runNumber,
                        IterationContext.runWith(runNumber, () -> oneRun.apply(resolved))));
            }));
        }
        // Let the in-flight requests finish; their results are kept.
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // an individual task failing is already reflected in its RunResult
            }
        }
    }

    /** Computes and, if positive, performs the wait before iteration {@code run}, tracking it on {@code state}. */
    private void waitFor(RunState state, DelayPlan delayPlan, int run, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        long waitMs = delayPlan.waitMs(run, elapsedMs);
        if (waitMs <= 0) {
            return;
        }
        state.currentWaitMs = waitMs;
        state.waiting = true;
        try {
            InterruptibleSleep.sleep(waitMs, state.cancelled);
        } finally {
            state.waiting = false;
        }
    }

    private RunResultDto toResult(int run, RunOutcome outcome) {
        return new RunResultDto(run, outcome.status(), outcome.durationMs(), outcome.error(),
                RunClassification.classify(outcome));
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }
}
