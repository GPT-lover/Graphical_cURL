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
}
