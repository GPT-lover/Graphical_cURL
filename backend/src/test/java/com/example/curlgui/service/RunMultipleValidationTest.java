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
}
