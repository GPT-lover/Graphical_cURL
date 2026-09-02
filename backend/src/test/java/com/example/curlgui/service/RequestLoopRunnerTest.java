package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.RunMode;
import com.example.curlgui.dto.RunResultDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Unit tests for the loop mechanics. The "send one request" step is a plain
 * function, so there's no Spring, no HttpClient, no network.
 */
class RequestLoopRunnerTest {

    private final RequestLoopRunner runner = new RequestLoopRunner();

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    private static final SendRequestDto REQ =
            new SendRequestDto("GET", "https://example.com", List.of(), List.of(), "", null);

    private static RunState state(RunMode mode, int total) {
        return new RunState("test", mode, total);
    }

    private static RunOutcome ok(long ms) {
        return new RunOutcome(200, ms, null);
    }

    // ---- sequential ------------------------------------------------

    @Test
    void sequentialRunsExactlyNTimesOneAfterAnother() {
        RunState s = state(RunMode.SEQUENTIAL, 3);
        long[] entry = new long[3];
        long[] exit = new long[3];
        AtomicInteger i = new AtomicInteger();

        runner.execute(s, REQ, 0, req -> {
            int idx = i.getAndIncrement();
            entry[idx] = System.nanoTime();
            sleep(15);
            exit[idx] = System.nanoTime();
            return ok(15);
        });

        assertEquals(3, s.completed.get());
        assertEquals(3, s.successful.get());
        assertTrue(entry[1] >= exit[0], "run 2 started before run 1 finished");
        assertTrue(entry[2] >= exit[1], "run 3 started before run 2 finished");
    }

    @Test
    void sequentialAppliesTheDelayBetweenRequestsButNotBeforeTheFirst() {
        RunState s = state(RunMode.SEQUENTIAL, 3);
        long delay = 40;
        long[] entry = new long[3];
        long[] exit = new long[3];
        AtomicInteger i = new AtomicInteger();

        long t0 = System.nanoTime();
        runner.execute(s, REQ, delay, req -> {
            int idx = i.getAndIncrement();
            entry[idx] = System.nanoTime();
            sleep(5);
            exit[idx] = System.nanoTime();
            return ok(5);
        });

        long beforeFirstMs = (entry[0] - t0) / 1_000_000;
        long gap1Ms = (entry[1] - exit[0]) / 1_000_000;
        long gap2Ms = (entry[2] - exit[1]) / 1_000_000;
        assertTrue(beforeFirstMs < delay, "there should be no delay before the first request");
        assertTrue(gap1Ms >= delay / 2, "expected a delay between requests, gap was " + gap1Ms + "ms");
        assertTrue(gap2Ms >= delay / 2, "expected a delay between requests, gap was " + gap2Ms + "ms");
    }

    // ---- parallel -----------------------------------------------

    @Test
    void parallelActuallyOverlapsRequestsButStaysBounded() {
        RunState s = state(RunMode.PARALLEL, 12);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        runner.execute(s, REQ, 0, req -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            sleep(60);
            active.decrementAndGet();
            return ok(60);
        });

        assertEquals(12, s.completed.get());
        assertTrue(maxActive.get() >= 2, "requests did not run concurrently (max active = " + maxActive.get() + ")");
        assertTrue(maxActive.get() <= RequestLoopRunner.PARALLEL_CONCURRENCY,
                "concurrency exceeded the bound (" + maxActive.get() + ")");
    }

    // ---- failure continuation + classification ------------------

    @Test
    void oneFailingRequestDoesNotStopTheLoop() {
        RunState s = state(RunMode.SEQUENTIAL, 5);
        AtomicInteger n = new AtomicInteger();

        runner.execute(s, REQ, 0, req -> {
            int run = n.incrementAndGet();
            return run == 3 ? new RunOutcome(500, 8L, null) : ok(8);
        });

        assertEquals(5, s.completed.get());
        assertEquals(4, s.successful.get());
        assertEquals(1, s.failed.get());
        List<RunResultDto> all = s.resultsFrom(0);
        all.sort((a, b) -> Integer.compare(a.run(), b.run()));
        assertEquals(500, all.get(2).status());
        assertEquals("FAILED", all.get(2).classification());
        assertEquals("SUCCESS", all.get(3).classification()); // run 4 still executed
    }

    @Test
    void networkErrorsAreRecordedAsFailuresAndTheLoopContinues() {
        RunState s = state(RunMode.SEQUENTIAL, 4);
        AtomicInteger n = new AtomicInteger();

        runner.execute(s, REQ, 0, req -> {
            int run = n.incrementAndGet();
            return run == 2 ? new RunOutcome(null, null, "Network Error") : ok(10);
        });

        assertEquals(4, s.completed.get());
        assertEquals(1, s.failed.get());
        RunResultDto errored = s.resultsFrom(0).stream().filter(r -> r.run() == 2).findFirst().orElseThrow();
        assertEquals("Network Error", errored.error());
        assertNull(errored.status());
        assertNull(errored.durationMs());
        assertEquals("FAILED", errored.classification());
    }

    @Test
    void mixedStatusCodesAreClassifiedConsistently() {
        RunState s = state(RunMode.SEQUENTIAL, 5);
        int[] codes = {200, 201, 301, 404, 500};
        AtomicInteger n = new AtomicInteger();

        runner.execute(s, REQ, 0, req -> new RunOutcome(codes[n.getAndIncrement()], 5L, null));

        List<RunResultDto> all = s.resultsFrom(0);
        all.sort((a, b) -> Integer.compare(a.run(), b.run()));
        assertEquals("SUCCESS", all.get(0).classification()); // 200
        assertEquals("SUCCESS", all.get(1).classification()); // 201
        assertEquals("REDIRECT", all.get(2).classification()); // 301
        assertEquals("FAILED", all.get(3).classification()); // 404
        assertEquals("FAILED", all.get(4).classification()); // 500
        assertEquals(2, s.successful.get());
        assertEquals(1, s.redirects.get());
        assertEquals(2, s.failed.get());
    }

    // ---- stop -------------------------------------------------

    @Test
    void stoppingSequentialStopsStartingNewRequestsButKeepsCompletedResults() throws Exception {
        RunState s = state(RunMode.SEQUENTIAL, 50);
        Function<SendRequestDto, RunOutcome> oneRun = req -> {
            sleep(20);
            return ok(20);
        };

        Thread loop = new Thread(() -> runner.execute(s, REQ, 0, oneRun));
        loop.start();
        Thread.sleep(120);
        s.cancelled.set(true);
        loop.join(2000);

        assertTrue(!loop.isAlive(), "loop did not return after stop");
        assertTrue(s.completed.get() > 0, "some results should have been kept");
        assertTrue(s.completed.get() < 50, "the loop should not have run all 50 after stop");
    }

    @Test
    void stoppingParallelLetsInFlightFinishAndDoesNotStartMore() throws Exception {
        RunState s = state(RunMode.PARALLEL, 200);
        Function<SendRequestDto, RunOutcome> oneRun = req -> {
            sleep(40);
            return ok(40);
        };

        Thread loop = new Thread(() -> runner.execute(s, REQ, 0, oneRun));
        loop.start();
        Thread.sleep(80);
        s.cancelled.set(true);
        loop.join(3000);

        assertTrue(!loop.isAlive(), "loop did not return after stop");
        assertTrue(s.completed.get() > 0);
        assertTrue(s.completed.get() < 200, "should not have run all 200 after stop");
    }

    // ---- results carry only metadata -------------------------

    @Test
    void resultsCarryOnlyRunStatusDurationErrorAndClassification() {
        RunState s = state(RunMode.SEQUENTIAL, 1);
        runner.execute(s, REQ, 0, req -> ok(42));
        RunResultDto r = s.resultsFrom(0).get(0);
        // A RunResultDto has exactly these 5 record components - no body/headers/cookies.
        assertEquals(5, RunResultDto.class.getRecordComponents().length);
        assertEquals(1, r.run());
        assertEquals(200, r.status());
        assertEquals(42L, r.durationMs());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
