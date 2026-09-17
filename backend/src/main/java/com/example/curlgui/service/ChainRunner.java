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
 * <h3>Pacing between iterations</h3>
 * Before dispatching each iteration's requests, the runner asks a {@link
 * DelayPlan} how long to wait, given the iteration number and elapsed time.
 * Under FIXED/JITTER pacing this is 0 for iteration 1 and a (fixed or
 * per-iteration random) delay for every iteration after that - i.e. a pause
 * <b>between</b> complete loop iterations, never before the first, never after
 * the last, and never between the individual requests inside one iteration
 * (dispatch order there is unchanged: still immediate, still never waiting on
 * a response). Under WINDOW pacing every iteration, including the first, waits
 * until its pre-computed random slot in the window arrives. The wait runs on
 * the caller's (per-chain orchestrator) thread and is polled in short slices
 * (see {@link InterruptibleSleep}) so a {@code Stop} during the wait takes
 * effect promptly instead of blocking for the whole interval.
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
     * Run the chain with fixed-delay pacing (the original behaviour). Kept for
     * existing callers/tests; equivalent to {@code execute(state, resolvedChain,
     * DelayPlan.fixed(cooldownMs), oneRun)}.
     *
     * @param cooldownMs pause between complete loop iterations, in ms; {@code 0}
     *                   disables it
     */
    void execute(ChainState state, List<SendRequestDto> resolvedChain, long cooldownMs,
                Function<SendRequestDto, RunOutcome> oneRun) {
        execute(state, resolvedChain, DelayPlan.fixed(cooldownMs), oneRun);
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
     * @param delayPlan     how long to wait before each iteration - see
     *                      {@link DelayPlan}
     * @param oneRun        performs one request and reports its outcome; never
     *                      throws (a failure is reported as a {@code RunOutcome}
     *                      with a non-null {@code error})
     */
    void execute(ChainState state, List<SendRequestDto> resolvedChain, DelayPlan delayPlan,
                Function<SendRequestDto, RunOutcome> oneRun) {
        int chainLength = resolvedChain.size();
        List<Future<?>> futures = new ArrayList<>();
        long startNanos = System.nanoTime();

        iterations:
        for (int iteration = 1; iteration <= state.totalIterations; iteration++) {
            if (state.cancelled.get()) {
                break;
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            long waitMs = delayPlan.waitMs(iteration, elapsedMs);
            if (waitMs > 0 && !state.cancelled.get()) {
                cooldown(state, waitMs);
            }

            for (int requestIndex = 0; requestIndex < chainLength; requestIndex++) {
                if (state.cancelled.get()) {
                    break iterations; // stop dispatching new requests
                }
                int slot = state.markDispatched(iteration, requestIndex);
                SendRequestDto request = resolvedChain.get(requestIndex);
                int currentIteration = iteration;
                futures.add(workers.submit(() -> {
                    if (state.cancelled.get()) {
                        // Already dispatched (its slot exists and was counted),
                        // but Stop was pressed before a worker thread picked it
                        // up - do not actually send it.
                        state.markCompleted(slot, new RunOutcome(null, null, "Cancelled"));
                        return;
                    }
                    state.markCompleted(slot,
                            IterationContext.runWith(currentIteration, () -> oneRun.apply(request)));
                }));
                // Deliberately NOT awaiting `futures`' last element here - dispatching
                // the next request must not wait for this one's response.
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
     * Wait out {@code waitMs}, but in short slices so a {@code Stop} pressed
     * mid-wait is noticed within ~50ms rather than after the whole interval.
     * Runs on the per-chain orchestrator thread (never an app-wide one).
     */
    private static void cooldown(ChainState state, long waitMs) {
        state.currentWaitMs = waitMs;
        state.coolingDown = true;
        try {
            InterruptibleSleep.sleep(waitMs, state.cancelled);
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
