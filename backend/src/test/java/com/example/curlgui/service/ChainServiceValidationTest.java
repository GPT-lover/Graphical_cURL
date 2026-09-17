package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pure unit tests for the chain-length / loop-count validation. No Spring. */
class ChainServiceValidationTest {

    @Test
    void chainMustHaveAtLeastOneRequest() {
        assertThrows(InvalidRequestException.class, () -> ChainService.requireChainLength(0));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireChainLength(null));
    }

    @Test
    void chainLengthUpToTwentyIsAllowed() {
        assertEquals(1, ChainService.requireChainLength(1));
        assertEquals(20, ChainService.requireChainLength(20));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireChainLength(21));
    }

    @Test
    void loopsMustBeAtLeastOne() {
        assertThrows(InvalidRequestException.class, () -> ChainService.requireLoops(0));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireLoops(-5));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireLoops(null));
    }

    @Test
    void fiveThousandLoopsIsTheHardMaximum() {
        assertEquals(5000, ChainService.requireLoops(5000));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireLoops(5001));
    }

    @Test
    void missingOrNullCooldownMeansZero() {
        assertEquals(0L, ChainService.requireCooldown(null));
        assertEquals(0L, ChainService.requireCooldown(0L));
    }

    @Test
    void positiveCooldownIsAcceptedUpToTheMaximum() {
        assertEquals(1000L, ChainService.requireCooldown(1000L));
        assertEquals(ChainService.MAX_COOLDOWN_MS, ChainService.requireCooldown(ChainService.MAX_COOLDOWN_MS));
    }

    @Test
    void negativeOrOverMaxCooldownIsRejected() {
        assertThrows(InvalidRequestException.class, () -> ChainService.requireCooldown(-1L));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireCooldown(-1000L));
        assertThrows(InvalidRequestException.class,
                () -> ChainService.requireCooldown(ChainService.MAX_COOLDOWN_MS + 1));
    }

    @Test
    void delayModeParsing() {
        assertEquals(DelayPlan.Mode.FIXED, ChainService.requireDelayMode(null)); // default
        assertEquals(DelayPlan.Mode.FIXED, ChainService.requireDelayMode("  "));
        assertEquals(DelayPlan.Mode.FIXED, ChainService.requireDelayMode("fixed"));
        assertEquals(DelayPlan.Mode.JITTER, ChainService.requireDelayMode("JITTER"));
        assertEquals(DelayPlan.Mode.WINDOW, ChainService.requireDelayMode("window"));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireDelayMode("RANDOM"));
    }

    @Test
    void jitterRange() {
        assertEquals(0, ChainService.requireJitter(null)); // absent -> no jitter
        assertEquals(0, ChainService.requireJitter(0L));
        assertEquals(50, ChainService.requireJitter(50L));
        assertEquals(ChainService.MAX_JITTER_MS, ChainService.requireJitter(ChainService.MAX_JITTER_MS));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireJitter(-1L));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireJitter(ChainService.MAX_JITTER_MS + 1));
    }

    @Test
    void windowRange() {
        assertEquals(100_000, ChainService.requireWindow(100_000L));
        assertEquals(ChainService.MAX_WINDOW_MS, ChainService.requireWindow(ChainService.MAX_WINDOW_MS));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireWindow(null));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireWindow(0L));
        assertThrows(InvalidRequestException.class, () -> ChainService.requireWindow(-1L));
        assertThrows(InvalidRequestException.class,
                () -> ChainService.requireWindow(ChainService.MAX_WINDOW_MS + 1));
    }
}
