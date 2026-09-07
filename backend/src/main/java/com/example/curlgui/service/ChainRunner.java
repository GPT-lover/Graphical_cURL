package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.example.curlgui.dto.SendRequestDto;

import jakarta.annotation.PreDestroy;

/**
 * Executes a chain of (possibly different) requests for {@code ChainService},
 * repeated for a number of loops, recording each dispatch and its eventual
 * result into a {@link ChainState}.
 *
 * <h3>Dispatch order vs. response completion</h3>
 * The whole point of a chain is that requests are <b>dispatched</b> - handed off
 * for execution - strictly in order (request 1, then request 2, ... then loop
 * back to request 1 for the next iteration), but the chain never waits for a
 * request's HTTP response before dispatching the next one. Concretely:
 * {@link #execute} calls {@code workers.submit(...)} for one request and moves
 * straight on to the next iteration of its loop - it never calls {@code
 * Future.get()} until every request in the whole run has been submitted. The
 * "send one request" work (injected as {@code oneRun}, the same blocking curl
 * call {@code RequestService#executeResolved} performs) therefore runs
 * concurrently with the dispatch loop, on a worker thread, while the dispatch
 * loop is already moving on to submit the next request.
 *
 * <p>Like {@code RequestLoopRunner}'s PARALLEL mode, actual execution is bounded
 * to a fixed-size worker pool with an unbounded queue: {@code submit()} itself
 * never blocks (so dispatch order and timing are never affected by the pool
 * being busy), but only {@link #CHAIN_CONCURRENCY} curl processes run at once -
 * a chain of 20 requests looped 5000 times must not try to spawn 100000 OS
 * processes simultaneously.
 *
 * <h3>Cooldown between iterations</h3>
 * If {@code cooldownMs > 0}, the runner pauses for that long <b>between complete
 * loop iterations</b> - after every request of iteration N has been dispatched,
 * before the first request of iteration N+1 is dispatched. There is no pause
 * before the first iteration or after the last one, and never a pause between
 * the individual requests inside one iteration (dispatch order there is
 * unchanged: still immediate, still never waiting on a response). The wait runs
 * on the caller's (per-chain orchestrator) thread and is polled in short slices
 * so a {@code Stop} during the cooldown takes effect promptly instead of
 * blocking for the whole interval.
 *
 * <h3>Error handling</h3>
 * A failed request (network error, non-2xx, whatever {@code oneRun} reports) is
 * recorded on its own slot and does not stop or skip any other dispatch - every
 * request in the chain, in every loop, is still dispatched regardless of any
 * other request's outcome.
 */
@Component
class ChainRunner {

    /** Max requests executing at once. Not user-configurable. */
    static final int CHAIN_CONCURRENCY = 30;

    private final ExecutorService workers = new ThreadPoolExecutor(
            CHAIN_CONCURRENCY, CHAIN_CONCURRENCY,
            30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            daemon("chain-worker"));

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    /**
     * Run the chain. Dispatches every request of every iteration, in order,
     * without ever waiting on a request's result before dispatching the next
     * one. Blocks only at the very end, until every dispatched request has
     * finished (or, after a stop, until the ones already dispatched have
     * finished) - so the caller can rely on {@code state} being fully settled
     * once this method returns.
     *
     * @param resolvedChain the chain's requests, in dispatch order, already
     *                      variable-resolved
     * @param cooldownMs    pause between complete loop iterations, in ms; {@code
     *                      0} disables it (behaviour identical to before this
     *                      parameter existed)
     * @param oneRun        performs one request and reports its outcome; never
     *                      throws (a failure is reported as a {@code RunOutcome}
     *                      with a non-null {@code error})
     */
    void execute(ChainState state, List<SendRequestDto> resolvedChain, long cooldownMs,
                Function<SendRequestDto, RunOutcome> oneRun) {
        int chainLength = resolvedChain.size();
        List<Future<?>> futures = new ArrayList<>();

        iterations:
        for (int iteration = 1; iteration <= state.totalIterations; iteration++) {
            for (int requestIndex = 0; requestIndex < chainLength; requestIndex++) {
                if (state.cancelled.get()) {
                    break iterations; // stop dispatching new requests
                }
                int slot = state.markDispatched(iteration, requestIndex);
                SendRequestDto request = resolvedChain.get(requestIndex);
                futures.add(workers.submit(() -> {
                    if (state.cancelled.get()) {
                        // Already dispatched (its slot exists and was counted),
                        // but Stop was pressed before a worker thread picked it
                        // up - do not actually send it.
                        state.markCompleted(slot, new RunOutcome(null, null, "Cancelled"));
                        return;
                    }
                    state.markCompleted(slot, oneRun.apply(request));
                }));
                // Deliberately NOT awaiting `futures`' last element here - dispatching
                // the next request must not wait for this one's response.
            }

            // Cooldown BETWEEN iterations only: not after the final one, and not
            // if a Stop has already been requested.
            if (cooldownMs > 0 && iteration < state.totalIterations && !state.cancelled.get()) {
                cooldown(state, cooldownMs);
            }
        }

        // Every request has been dispatched (or the chain was stopped); now let
        // the in-flight ones finish so their results are collected before the
        // run is reported as DONE/STOPPED.
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
                // an individual task failing is already reflected in its slot
            }
        }
    }

    /**
     * Wait out the cooldown, but in short slices so a {@code Stop} pressed
     * mid-cooldown is noticed within ~50ms rather than after the whole interval.
     * Runs on the per-chain orchestrator thread (never an app-wide one).
     */
    private static void cooldown(ChainState state, long cooldownMs) {
        state.coolingDown = true;
        try {
            long deadlineNanos = System.nanoTime() + cooldownMs * 1_000_000L;
            long remainingMs;
            while (!state.cancelled.get()
                    && (remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L) > 0) {
                try {
                    Thread.sleep(Math.min(50L, remainingMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            state.coolingDown = false;
        }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }
}
