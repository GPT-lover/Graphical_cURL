package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable, thread-safe state for one running (or finished) Hydra attack, held
 * in memory by {@link HydraAttackService}. Output is capped so a very long or
 * runaway attack cannot grow memory without bound.
 */
class HydraAttackState {

    enum Status { RUNNING, DONE, STOPPED, ERROR }

    private static final int MAX_LINES = 20_000;

    final String id;
    volatile Process process;
    volatile Status status = Status.RUNNING;
    volatile Integer exitCode;
    volatile String errorMessage;
    volatile long lastTouchedAtMillis = System.currentTimeMillis();

    /**
     * Set only for a WSL-mode attack: the distro/path used to invoke Hydra, so
     * {@code stop()} can also reach in and kill the process *inside* WSL - just
     * destroying the {@code wsl.exe} client process on the Windows side does
     * not reliably terminate it. Null for a local-executable attack.
     */
    volatile String wslCleanupDistro;
    volatile String wslCleanupHydraPath;

    final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final List<String> output = Collections.synchronizedList(new ArrayList<>());

    HydraAttackState(String id) {
        this.id = id;
    }

    /** Called from the reader thread as Hydra prints each line. */
    void appendLine(String line) {
        synchronized (output) {
            if (output.size() >= MAX_LINES) {
                return;
            }
            output.add(line);
            if (output.size() == MAX_LINES) {
                output.add("[output truncated at " + MAX_LINES + " lines]");
            }
        }
    }

    /** A copy of the output lines from {@code offset} onward. */
    List<String> outputFrom(int offset) {
        synchronized (output) {
            int size = output.size();
            int from = Math.max(0, Math.min(offset, size));
            return new ArrayList<>(output.subList(from, size));
        }
    }
}
