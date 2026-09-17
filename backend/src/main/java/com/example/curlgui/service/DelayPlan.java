package com.example.curlgui.service;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Computes how long to wait, in ms, before dispatching iteration {@code n}
 * (1-based) of a loop with a known total iteration count. Shared by {@link
 * RequestLoopRunner} (run-multiple) and {@link ChainRunner} (the pause
 * between a chain's loop iterations) so both features offer identical pacing
 * semantics:
 *
 * <ul>
 *   <li>{@code FIXED} - the same delay between every iteration, never before
 *       the first. Exactly the original behaviour.</li>
 *   <li>{@code JITTER} - a fresh random delay drawn independently for every
 *       iteration, uniform within {@code [base - jitter, base + jitter]}
 *       (clamped to 0 or above), between iterations, never before the
 *       first.</li>
 *   <li>{@code WINDOW} - {@code total} dispatch times are drawn uniformly at
 *       random across {@code [0, windowMs]} and sorted once, up front; every
 *       iteration (including the first) waits until its scheduled time
 *       arrives. This spreads a fixed request count irregularly across a
 *       fixed wall-clock window - e.g. 50 requests somewhere within the next
 *       100 seconds - rather than delaying by a fixed or jittered amount
 *       between each one.</li>
 * </ul>
 *
 * <p>{@link #waitMs} takes {@code elapsedMs} (time since the loop started) so
 * {@code WINDOW} can self-correct: if earlier iterations (their sends, in
 * SEQUENTIAL run-multiple mode) took longer than expected, the remaining
 * waits shrink accordingly instead of pushing the whole run past the window.
 */
final class DelayPlan {

    enum Mode { FIXED, JITTER, WINDOW }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Mode mode;
    private final long baseMs;
    private final long jitterMs;
    private final long[] windowOffsetsMs; // WINDOW only: sorted ascending, one per iteration

    private DelayPlan(Mode mode, long baseMs, long jitterMs, long[] windowOffsetsMs) {
        this.mode = mode;
        this.baseMs = baseMs;
        this.jitterMs = jitterMs;
        this.windowOffsetsMs = windowOffsetsMs;
    }

    static DelayPlan fixed(long delayMs) {
        return new DelayPlan(Mode.FIXED, delayMs, 0, null);
    }

    static DelayPlan jitter(long delayMs, long jitterMs) {
        return new DelayPlan(Mode.JITTER, delayMs, jitterMs, null);
    }

    /** {@code totalIterations} random dispatch offsets drawn uniformly across {@code [0, windowMs]}, sorted. */
    static DelayPlan window(long windowMs, int totalIterations) {
        long[] offsets = new long[Math.max(totalIterations, 0)];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = Math.round(RANDOM.nextDouble() * windowMs);
        }
        Arrays.sort(offsets);
        return new DelayPlan(Mode.WINDOW, 0, 0, offsets);
    }

    static DelayPlan of(Mode mode, long baseMs, long jitterMs, long windowMs, int totalIterations) {
        return switch (mode) {
            case FIXED -> fixed(baseMs);
            case JITTER -> jitter(baseMs, jitterMs);
            case WINDOW -> window(windowMs, totalIterations);
        };
    }

    /**
     * ms to wait before dispatching 1-based iteration {@code n}, given that
     * {@code elapsedMs} have passed since the loop started.
     */
    long waitMs(int n, long elapsedMs) {
        return switch (mode) {
            case FIXED -> n > 1 ? Math.max(0, baseMs) : 0;
            case JITTER -> n > 1 ? jitterValue() : 0;
            case WINDOW -> Math.max(0, windowOffsetsMs[n - 1] - elapsedMs);
        };
    }

    private long jitterValue() {
        if (jitterMs <= 0) {
            return Math.max(0, baseMs);
        }
        long span = jitterMs * 2 + 1; // draws uniformly from the inclusive range [-jitterMs, +jitterMs]
        long offset = (long) (RANDOM.nextDouble() * span) - jitterMs;
        return Math.max(0, baseMs + offset);
    }
}
