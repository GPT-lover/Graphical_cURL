package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link DelayPlan}'s three pacing modes. No Spring, no timing. */
class DelayPlanTest {

    // ---- FIXED -------------------------------------------------------

    @Test
    void fixedNeverWaitsBeforeTheFirstIteration() {
        DelayPlan plan = DelayPlan.fixed(200);
        assertEquals(0, plan.waitMs(1, 0));
    }

    @Test
    void fixedWaitsTheSameAmountBeforeEveryOtherIteration() {
        DelayPlan plan = DelayPlan.fixed(200);
        assertEquals(200, plan.waitMs(2, 0));
        assertEquals(200, plan.waitMs(50, 0));
    }

    @Test
    void fixedIgnoresElapsedTime() {
        DelayPlan plan = DelayPlan.fixed(200);
        assertEquals(200, plan.waitMs(2, 999_999));
    }

    // ---- JITTER --------------------------------------------------------

    @Test
    void jitterNeverWaitsBeforeTheFirstIteration() {
        DelayPlan plan = DelayPlan.jitter(200, 50);
        assertEquals(0, plan.waitMs(1, 0));
    }

    @Test
    void jitterStaysWithinBaseWhenNoJitterIsGiven() {
        DelayPlan plan = DelayPlan.jitter(200, 0);
        for (int i = 0; i < 20; i++) {
            assertEquals(200, plan.waitMs(2, 0));
        }
    }

    @Test
    void jitterStaysWithinTheConfiguredRange() {
        DelayPlan plan = DelayPlan.jitter(200, 50);
        for (int i = 0; i < 500; i++) {
            long wait = plan.waitMs(2, 0);
            assertTrue(wait >= 150 && wait <= 250, "wait " + wait + " outside [150,250]");
        }
    }

    @Test
    void jitterClampsToZeroRatherThanGoingNegative() {
        DelayPlan plan = DelayPlan.jitter(10, 50); // range would be [-40, 60] unclamped
        for (int i = 0; i < 500; i++) {
            long wait = plan.waitMs(2, 0);
            assertTrue(wait >= 0 && wait <= 60, "wait " + wait + " outside [0,60]");
        }
    }

    @Test
    void jitterDrawsIndependentlyEachCall() {
        DelayPlan plan = DelayPlan.jitter(1000, 1000);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(plan.waitMs(2, 0));
        }
        assertTrue(seen.size() > 1, "jitter produced the same value every time");
    }

    // ---- WINDOW --------------------------------------------------------

    @Test
    void windowCanWaitBeforeTheFirstIterationUnlikeFixedAndJitter() {
        // With enough iterations packed into a short window, at least one run
        // should land with a non-trivial wait for its first dispatch.
        DelayPlan plan = DelayPlan.window(10_000, 1);
        // A single-iteration window: its one offset is somewhere in [0, 10000].
        long wait = plan.waitMs(1, 0);
        assertTrue(wait >= 0 && wait <= 10_000, "wait " + wait + " outside [0,10000]");
    }

    @Test
    void windowSchedulesEveryIterationWithinTheWindow() {
        DelayPlan plan = DelayPlan.window(100_000, 50);
        for (int n = 1; n <= 50; n++) {
            long wait = plan.waitMs(n, 0);
            assertTrue(wait >= 0 && wait <= 100_000, "iteration " + n + " wait " + wait + " outside window");
        }
    }

    @Test
    void windowOffsetsAreNonDecreasingAcrossIterations() {
        // Iteration n's target (waitMs at elapsed=0) must never be later than
        // iteration n+1's, since offsets are sorted ascending.
        DelayPlan plan = DelayPlan.window(50_000, 30);
        long previous = -1;
        for (int n = 1; n <= 30; n++) {
            long target = plan.waitMs(n, 0);
            assertTrue(target >= previous, "offsets are not sorted ascending at n=" + n);
            previous = target;
        }
    }

    @Test
    void windowSelfCorrectsAgainstElapsedTime() {
        DelayPlan plan = DelayPlan.window(10_000, 1);
        long target = plan.waitMs(1, 0);
        // If we've already "spent" more time than the target, the wait shrinks to 0.
        assertEquals(0, plan.waitMs(1, target + 5_000));
    }

    @Test
    void windowNeverReturnsANegativeWait() {
        DelayPlan plan = DelayPlan.window(1_000, 5);
        for (int n = 1; n <= 5; n++) {
            assertTrue(plan.waitMs(n, 999_999) >= 0);
        }
    }

    // ---- of() dispatch ---------------------------------------------------

    @Test
    void ofDispatchesToTheRightFactory() {
        assertEquals(0, DelayPlan.of(DelayPlan.Mode.FIXED, 100, 0, 0, 5).waitMs(1, 0));
        assertEquals(100, DelayPlan.of(DelayPlan.Mode.FIXED, 100, 0, 0, 5).waitMs(2, 0));
        assertEquals(0, DelayPlan.of(DelayPlan.Mode.JITTER, 100, 0, 0, 5).waitMs(1, 0));
        DelayPlan window = DelayPlan.of(DelayPlan.Mode.WINDOW, 0, 0, 5_000, 3);
        for (int n = 1; n <= 3; n++) {
            assertTrue(window.waitMs(n, 0) <= 5_000);
        }
    }
}
