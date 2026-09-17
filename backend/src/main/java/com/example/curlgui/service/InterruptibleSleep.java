package com.example.curlgui.service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@code Thread.sleep} that polls {@code cancelled} every ~50ms instead of
 * blocking for the whole interval in one call, so a Stop pressed mid-wait
 * takes effect within about 50ms rather than after the full delay. Shared by
 * {@link RequestLoopRunner} and {@link ChainRunner} for every wait they
 * perform, however it was computed (fixed, jittered, or a window-schedule
 * gap).
 */
final class InterruptibleSleep {

    private static final long SLICE_MS = 50L;

    private InterruptibleSleep() {
    }

    static void sleep(long ms, AtomicBoolean cancelled) {
        long deadlineNanos = System.nanoTime() + ms * 1_000_000L;
        long remainingMs;
        while (!cancelled.get() && (remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L) > 0) {
            try {
                Thread.sleep(Math.min(SLICE_MS, remainingMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
