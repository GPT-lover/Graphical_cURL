package com.example.curlgui.service;

import java.util.function.Supplier;

/**
 * Carries the current loop/chain iteration number (1-based) across the "one
 * send" boundary between {@link RequestLoopRunner} / {@link ChainRunner} and
 * {@link RequestService#executeResolved}, so {@link DynamicVariableResolver}
 * can resolve {@code {{increment(N)}}} to the right value without every
 * method in between having to take and pass an extra parameter.
 *
 * <p>Thread-local by design: PARALLEL loops and chains run each dispatch on a
 * pool worker thread, and {@link #runWith} sets the value immediately before,
 * and clears it immediately after, the single synchronous {@code oneRun}
 * call that happens on that thread - so no task ever reads a value left by an
 * earlier task on a reused pool thread. A thread where it was never set (a
 * plain Send, outside any loop) reads back {@link #DEFAULT}.
 */
final class IterationContext {

    static final int DEFAULT = 1;

    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();

    private IterationContext() {
    }

    static int current() {
        Integer value = CURRENT.get();
        return value == null ? DEFAULT : value;
    }

    /** Run {@code action} with {@code iteration} visible to {@link #current()}, then clear it. */
    static <T> T runWith(int iteration, Supplier<T> action) {
        CURRENT.set(iteration);
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }
}
