package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.curlgui.dto.ChainResultDto;
import com.example.curlgui.dto.ChainSummaryDto;

/**
 * Mutable, thread-safe state for one running chain, held in memory by
 * {@code ChainService} and updated by {@code ChainRunner} as requests are
 * dispatched and their results arrive. Holds only metadata per slot - never a
 * response body, header or cookie.
 *
 * <p>Unlike {@link RunState} (whose results list is append-only, one entry per
 * finished run), a chain slot is created the moment its request is
 * <b>dispatched</b> - before any response exists - and is updated again in place
 * once a result arrives. This lets the frontend show "dispatched, awaiting
 * response" distinctly from "completed" or "failed".
 *
 * <p>A slot is addressed by {@code (iteration, requestIndex)}, flattened into a
 * single {@code slotIndex = (iteration - 1) * chainLength + requestIndex}. Each
 * slot's own fields are only ever written by one thread at a time (the
 * dispatching thread creates it; exactly one worker thread later completes it),
 * so the fields need only be {@code volatile} for visibility, not further
 * locking. The shared {@code changeLog} records, in order, every slot index that
 * changed (once on dispatch, once again on completion) so polling clients can
 * fetch only what changed since their last poll - mirroring {@link
 * RunState#resultsFrom}, but as a log of events rather than an append-only list,
 * since the same slot can legitimately appear twice.
 */
class ChainState {

    enum Status { RUNNING, DONE, STOPPED }

    final String id;
    final int chainLength;
    final int totalIterations;
    final int totalDispatches;
    final long startedAtNanos = System.nanoTime();

    volatile long finishedAtNanos;
    volatile Status status = Status.RUNNING;
    volatile long lastTouchedAtMillis = System.currentTimeMillis();

    final AtomicBoolean cancelled = new AtomicBoolean(false);
    final AtomicInteger dispatched = new AtomicInteger();
    final AtomicInteger completed = new AtomicInteger();
    final AtomicInteger successful = new AtomicInteger();
    final AtomicInteger redirects = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();

    private final List<Slot> slots;
    private final List<Integer> changeLog = Collections.synchronizedList(new ArrayList<>());

    ChainState(String id, int chainLength, int totalIterations) {
        this.id = id;
        this.chainLength = chainLength;
        this.totalIterations = totalIterations;
        this.totalDispatches = chainLength * totalIterations;
        this.slots = new ArrayList<>(Collections.nCopies(totalDispatches, null));
    }

    /**
     * Called once per slot, in dispatch order, from the single dispatching
     * thread, the moment a request is handed off for execution (not when its
     * response arrives). Returns the flattened slot index to pass to {@link
     * #markCompleted}.
     */
    int markDispatched(int iteration, int requestIndex) {
        int idx = (iteration - 1) * chainLength + requestIndex;
        slots.set(idx, new Slot(iteration, requestIndex));
        dispatched.incrementAndGet();
        changeLog.add(idx);
        return idx;
    }

    /** Called once per slot (from that slot's own worker thread) when its result is known. */
    void markCompleted(int slotIndex, RunOutcome outcome) {
        Slot s = slots.get(slotIndex);
        s.status = outcome.status();
        s.durationMs = outcome.durationMs();
        s.error = outcome.error();
        s.classification = RunClassification.classify(outcome);
        completed.incrementAndGet();
        switch (s.classification) {
            case RunClassification.SUCCESS -> successful.incrementAndGet();
            case RunClassification.REDIRECT -> redirects.incrementAndGet();
            default -> failed.incrementAndGet();
        }
        changeLog.add(slotIndex);
    }

    /**
     * The change events from {@code offset} onward, resolved to each slot's
     * <em>current</em> snapshot (order = the order slots changed; the same slot
     * may appear twice - once dispatched, once completed).
     */
    List<ChainResultDto> resultsFrom(int offset) {
        List<Integer> changedIndexes;
        synchronized (changeLog) {
            int size = changeLog.size();
            int from = Math.max(0, Math.min(offset, size));
            changedIndexes = new ArrayList<>(changeLog.subList(from, size));
        }
        List<ChainResultDto> out = new ArrayList<>(changedIndexes.size());
        for (int idx : changedIndexes) {
            Slot s = slots.get(idx);
            out.add(new ChainResultDto(
                    s.iteration, s.requestIndex, s.status, s.durationMs, s.error, s.classification));
        }
        return out;
    }

    /**
     * Only called once the chain has finished (after every dispatched future has
     * been joined), so {@code slots} needs no extra synchronization here.
     */
    ChainSummaryDto summary() {
        long elapsedMs = Math.round((finishedAtNanos - startedAtNanos) / 1_000_000.0);
        long sum = 0;
        int n = 0;
        for (Slot s : slots) {
            if (s != null && s.durationMs != null) {
                sum += s.durationMs;
                n++;
            }
        }
        long avg = n == 0 ? 0 : Math.round((double) sum / n);
        return new ChainSummaryDto(
                totalIterations, chainLength, totalDispatches,
                completed.get(), successful.get(), redirects.get(), failed.get(),
                avg, elapsedMs, status == Status.STOPPED);
    }

    private static final class Slot {
        final int iteration;
        final int requestIndex;
        volatile Integer status;
        volatile Long durationMs;
        volatile String error;
        volatile String classification;

        Slot(int iteration, int requestIndex) {
            this.iteration = iteration;
            this.requestIndex = requestIndex;
        }
    }
}
