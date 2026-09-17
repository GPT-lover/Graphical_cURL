package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.RunMode;

/** Pure unit tests for the run-count / delay / mode validation. No Spring. */
class RunMultipleValidationTest {

    @Test
    void runsMustBeAtLeastOne() {
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireRuns(0));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireRuns(-5));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireRuns(null));
    }

    @Test
    void oneThousandRunsIsAllowed_thereIsNo1000Cap() {
        assertEquals(1, RunMultipleService.requireRuns(1));
        assertEquals(1000, RunMultipleService.requireRuns(1000));
        assertEquals(1001, RunMultipleService.requireRuns(1001)); // warning threshold, not a cap
        assertEquals(2500, RunMultipleService.requireRuns(2500));
    }

    @Test
    void fiveThousandIsTheHardMaximum() {
        assertEquals(5000, RunMultipleService.requireRuns(5000));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireRuns(5001));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireRuns(100000));
    }

    @Test
    void delayRange() {
        assertEquals(0, RunMultipleService.requireDelay(0L));
        assertEquals(0, RunMultipleService.requireDelay(null)); // absent -> no delay
        assertEquals(100, RunMultipleService.requireDelay(100L));
        assertEquals(60_000, RunMultipleService.requireDelay(60_000L));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireDelay(-1L));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireDelay(60_001L));
    }

    @Test
    void modeParsing() {
        assertEquals(RunMode.SEQUENTIAL, RunMultipleService.parseMode("SEQUENTIAL"));
        assertEquals(RunMode.PARALLEL, RunMultipleService.parseMode("parallel"));
        assertEquals(RunMode.SEQUENTIAL, RunMultipleService.parseMode(null)); // default
        assertEquals(RunMode.SEQUENTIAL, RunMultipleService.parseMode("  "));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.parseMode("DIAGONAL"));
    }

    @Test
    void delayModeParsing() {
        assertEquals(DelayPlan.Mode.FIXED, RunMultipleService.requireDelayMode(null)); // default
        assertEquals(DelayPlan.Mode.FIXED, RunMultipleService.requireDelayMode("  "));
        assertEquals(DelayPlan.Mode.FIXED, RunMultipleService.requireDelayMode("fixed"));
        assertEquals(DelayPlan.Mode.JITTER, RunMultipleService.requireDelayMode("JITTER"));
        assertEquals(DelayPlan.Mode.WINDOW, RunMultipleService.requireDelayMode("window"));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireDelayMode("RANDOM"));
    }

    @Test
    void jitterRange() {
        assertEquals(0, RunMultipleService.requireJitter(null)); // absent -> no jitter
        assertEquals(0, RunMultipleService.requireJitter(0L));
        assertEquals(50, RunMultipleService.requireJitter(50L));
        assertEquals(RunMultipleService.MAX_JITTER_MS, RunMultipleService.requireJitter(RunMultipleService.MAX_JITTER_MS));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireJitter(-1L));
        assertThrows(InvalidRequestException.class,
                () -> RunMultipleService.requireJitter(RunMultipleService.MAX_JITTER_MS + 1));
    }

    @Test
    void windowRange() {
        assertEquals(100_000, RunMultipleService.requireWindow(100_000L));
        assertEquals(RunMultipleService.MAX_WINDOW_MS, RunMultipleService.requireWindow(RunMultipleService.MAX_WINDOW_MS));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireWindow(null));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireWindow(0L));
        assertThrows(InvalidRequestException.class, () -> RunMultipleService.requireWindow(-1L));
        assertThrows(InvalidRequestException.class,
                () -> RunMultipleService.requireWindow(RunMultipleService.MAX_WINDOW_MS + 1));
    }
}
