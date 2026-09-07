package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.ChainResultDto;
import com.example.curlgui.dto.SendRequestDto;

/**
 * Unit tests for chain dispatch mechanics. The "send one request" step is a
 * plain function, so there's no Spring, no HttpClient, no network - exactly the
 * style {@code RequestLoopRunnerTest} uses for the existing single-request loop.
 *
 * <p>These tests exist to prove the central requirement of request chaining:
 * requests are DISPATCHED strictly in order, but the chain never waits for a
 * request's response before dispatching the next one - dispatch order and
 * response-completion order are independent.
 */
class ChainRunnerTest {

    private final ChainRunner runner = new ChainRunner();

    @AfterEach
    void tearDown() {
        runner.shutdown();
    }

    private static SendRequestDto req(String url) {
        return new SendRequestDto("GET", url, List.of(), List.of(), "", null);
    }

    private static ChainState state(int chainLength, int loops) {
        return new ChainState("test", chainLength, loops, 0L);
    }

    private static ChainState state(int chainLength, int loops, long cooldownMs) {
        return new ChainState("test", chainLength, loops, cooldownMs);
    }

    private static RunOutcome ok(long ms) {
        return new RunOutcome(200, ms, null);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- 1. a single request still works ---------------------------

    @Test
    void singleRequestStillWorks() {
        ChainState s = state(1, 1);
        List<SendRequestDto> chain = List.of(req("https://example.com/only"));

        runner.execute(s, chain, 0L, r -> ok(5));

        assertEquals(1, s.dispatched.get());
        assertEquals(1, s.completed.get());
        assertEquals(1, s.successful.get());
        ChainResultDto result = s.resultsFrom(0).get(s.resultsFrom(0).size() - 1);
        assertEquals(1, result.iteration());
        assertEquals(0, result.requestIndex());
        assertEquals("SUCCESS", result.classification());
    }

    // ---- 2 & 4. dispatch order for N requests -----------------------
    //
    // Dispatch order is decided entirely by ChainRunner's single dispatching
    // thread, synchronously, before any request is handed to a worker - so it
    // can never race. But *executing* oneRun happens on worker threads, so
    // recording order via a side effect inside oneRun (as an earlier version of
    // this test did) is not a reliable way to observe it: once more than one
    // worker is involved, which worker's body happens to run first is an OS
    // scheduling detail, not something the dispatch loop controls.
    //
    // Instead, these tests block every oneRun call before it does anything, wait
    // until every request in the run has been marked dispatched, and then read
    // back state.resultsFrom(0): since nothing has been allowed to complete yet,
    // every entry in it is still a pending "dispatched" slot, appended in the
    // exact order markDispatched was called - a direct, race-free read of
    // dispatch order.

    @Test
    void twoRequestsAreDispatchedInOrder() throws InterruptedException {
        ChainState s = state(2, 1);
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));
        assertDispatchOrder(s, chain, List.of(0, 1));
    }

    @Test
    void threeRequestsAreDispatchedInOrder() throws InterruptedException {
        ChainState s = state(3, 1);
        List<SendRequestDto> chain = List.of(
                req("https://example.com/r1"), req("https://example.com/r2"), req("https://example.com/r3"));
        assertDispatchOrder(s, chain, List.of(0, 1, 2));
    }

    /**
     * Runs the given chain with every {@code oneRun} call blocked, waits for
     * every slot to become dispatched, and asserts the pending slots appear (in
     * {@code state.resultsFrom(0)}, which is append-order) with the expected
     * {@code requestIndex} sequence. Then releases everything so the run can
     * finish.
     */
    private void assertDispatchOrder(ChainState s, List<SendRequestDto> chain, List<Integer> expectedRequestIndexOrder)
            throws InterruptedException {
        int total = s.totalDispatches;
        CountDownLatch release = new CountDownLatch(1);
        Thread loop = new Thread(() -> runner.execute(s, chain, 0L, r -> {
            awaitUninterruptibly(release, 5000);
            return ok(1);
        }));
        loop.start();

        assertTrue(waitUntil(() -> s.dispatched.get() == total, 2000),
                "not every request was dispatched (dispatched=" + s.dispatched.get() + "/" + total + ")");

        List<ChainResultDto> pending = s.resultsFrom(0);
        assertEquals(total, pending.size());
        List<Integer> actualOrder = pending.stream().map(ChainResultDto::requestIndex).toList();
        assertEquals(expectedRequestIndexOrder, actualOrder);
        for (ChainResultDto r : pending) {
            assertNull(r.classification(), "nothing should have completed yet");
        }

        release.countDown();
        loop.join(2000);
        assertFalse(loop.isAlive());
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(2);
        }
        return condition.getAsBoolean();
    }

    // ---- 3. request 2 dispatched before request 1's slow response ---

    @Test
    void request2IsDispatchedBeforeRequest1sSlowResponseArrives() throws InterruptedException {
        ChainState s = state(2, 1);
        List<SendRequestDto> chain = List.of(req("https://example.com/slow"), req("https://example.com/fast"));

        CountDownLatch request1Responding = new CountDownLatch(1);
        CountDownLatch request2DispatchedWhileRequest1StillInFlight = new CountDownLatch(1);

        Function<SendRequestDto, RunOutcome> oneRun = r -> {
            if (r.url().endsWith("/slow")) {
                // Block "request 1" until we've observed request 2 dispatched -
                // proves dispatch of #2 did not wait on #1's response.
                boolean sawRequest2Dispatched =
                        awaitUninterruptibly(request2DispatchedWhileRequest1StillInFlight, 2000);
                assertTrue(sawRequest2Dispatched, "request 2 was never dispatched while request 1 was in flight");
                return ok(1);
            }
            request2DispatchedWhileRequest1StillInFlight.countDown();
            return ok(1);
        };

        runner.execute(s, chain, 0L, oneRun);

        assertEquals(2, s.completed.get());
    }

    private static boolean awaitUninterruptibly(CountDownLatch latch, long timeoutMs) {
        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- 5 & 6. loop count x chain length dispatch order, no waiting between iterations

    @Test
    void loopCount3WithTwoRequestsDispatchesInR1R2R1R2R1R2Order() throws InterruptedException {
        ChainState s = state(2, 3);
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        // Same technique as assertDispatchOrder, but also checks iteration numbers
        // (1,0) (1,1) (2,0) (2,1) (3,0) (3,1) - i.e. R1 R2 R1 R2 R1 R2.
        int total = s.totalDispatches;
        CountDownLatch release = new CountDownLatch(1);
        Thread loop = new Thread(() -> runner.execute(s, chain, 0L, r -> {
            awaitUninterruptibly(release, 5000);
            return ok(1);
        }));
        loop.start();

        assertTrue(waitUntil(() -> s.dispatched.get() == total, 2000),
                "not every request was dispatched (dispatched=" + s.dispatched.get() + "/" + total + ")");

        List<ChainResultDto> pending = s.resultsFrom(0);
        assertEquals(total, pending.size());
        List<String> actualOrder = pending.stream()
                .map(r -> r.iteration() + ":" + (r.requestIndex() == 0 ? "R1" : "R2"))
                .toList();
        assertEquals(List.of("1:R1", "1:R2", "2:R1", "2:R2", "3:R1", "3:R2"), actualOrder);

        release.countDown();
        loop.join(2000);
        assertFalse(loop.isAlive());
    }

    @Test
    void loopDoesNotWaitForResponsesBetweenIterations() {
        // R1 is slow (50ms); if the loop waited for each response before moving
        // on, dispatching R1 three times (iterations 1..3) would take >= 150ms
        // measured strictly by dispatch timestamps. Since dispatch never waits,
        // all 6 dispatches should happen almost immediately.
        ChainState s = state(2, 3);
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        long t0 = System.nanoTime();
        runner.execute(s, chain, 0L, r -> {
            sleep(40);
            return ok(40);
        });
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(6, s.completed.get());
        // 6 dispatches at ~40ms each would take >= 240ms if serialized on
        // responses; bounded concurrency (30) easily finishes well under that.
        assertTrue(totalMs < 200, "loop appears to have waited for responses between iterations: " + totalMs + "ms");
    }

    // ---- 7 & 8. out-of-order responses stay associated with the right slot

    @Test
    void responsesCanArriveOutOfOrderAndAreAssociatedWithTheCorrectRequestAndIteration() {
        ChainState s = state(2, 2); // 4 dispatches: (1,0) (1,1) (2,0) (2,1)
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        // Make request-index 0 slower than request-index 1, and later iterations
        // faster than earlier ones, so completion order is scrambled relative to
        // dispatch order.
        runner.execute(s, chain, 0L, r -> {
            if (r.url().endsWith("/r1")) {
                sleep(30);
            }
            return ok(1);
        });

        List<ChainResultDto> all = s.resultsFrom(0);
        // Keep only the completion events (classification != null), one per slot.
        Map<String, ChainResultDto> bySlot = new ConcurrentHashMap<>();
        for (ChainResultDto r : all) {
            if (r.classification() != null) {
                bySlot.put(r.iteration() + ":" + r.requestIndex(), r);
            }
        }
        assertEquals(4, bySlot.size());
        for (int iteration = 1; iteration <= 2; iteration++) {
            for (int idx = 0; idx < 2; idx++) {
                ChainResultDto r = bySlot.get(iteration + ":" + idx);
                assertEquals(iteration, r.iteration(), "iteration mismatch for slot " + iteration + ":" + idx);
                assertEquals(idx, r.requestIndex(), "requestIndex mismatch for slot " + iteration + ":" + idx);
                assertEquals("SUCCESS", r.classification());
            }
        }
    }

    // ---- 9. a failed request does not block subsequent dispatches ---

    @Test
    void aFailedRequestDoesNotBlockSubsequentDispatches() {
        ChainState s = state(3, 1);
        List<SendRequestDto> chain = List.of(
                req("https://example.com/r1"), req("https://example.com/r2"), req("https://example.com/r3"));
        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();

        runner.execute(s, chain, 0L, r -> {
            order.add(r.url());
            if (r.url().endsWith("/r1")) {
                return new RunOutcome(500, 1L, null); // R1 "fails" (server error)
            }
            return ok(1);
        });

        // All three were still dispatched (and sent) despite R1's failure - which
        // one's oneRun call happens to run first is a worker-thread scheduling
        // detail, not something dispatch order controls (see assertDispatchOrder
        // above), so this only checks that none of them were skipped.
        assertEquals(
                java.util.Set.of("https://example.com/r1", "https://example.com/r2", "https://example.com/r3"),
                java.util.Set.copyOf(order));
        assertEquals(3, s.completed.get());
        assertEquals(1, s.failed.get());
        assertEquals(2, s.successful.get());
    }

    @Test
    void networkErrorOnOneRequestDoesNotBlockSubsequentDispatches() {
        ChainState s = state(2, 1);
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        runner.execute(s, chain, 0L, r -> r.url().endsWith("/r1")
                ? new RunOutcome(null, null, "Network Error")
                : ok(1));

        assertEquals(2, s.completed.get());
        assertEquals(1, s.failed.get());
        assertEquals(1, s.successful.get());
        ChainResultDto failedResult = s.resultsFrom(0).stream()
                .filter(r -> r.requestIndex() == 0 && r.classification() != null)
                .findFirst().orElseThrow();
        assertEquals("Network Error", failedResult.error());
        assertNull(failedResult.status());
        assertEquals("FAILED", failedResult.classification());
    }

    // ---- dispatched-but-pending state is visible before completion ---

    @Test
    void aDispatchedSlotIsVisibleAsPendingBeforeItsResultArrives() throws InterruptedException {
        ChainState s = state(1, 1);
        List<SendRequestDto> chain = List.of(req("https://example.com/slow"));
        CountDownLatch dispatched = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);

        Thread loop = new Thread(() -> runner.execute(s, chain, 0L, r -> {
            dispatched.countDown();
            awaitUninterruptibly(releaseResponse, 2000);
            return ok(1);
        }));
        loop.start();

        assertTrue(dispatched.await(1, TimeUnit.SECONDS));
        // Give the dispatching thread a moment to have called markDispatched
        // (it does so before submitting, so this is already true, but a tiny
        // sleep guards against any scheduling flakiness).
        sleep(20);

        List<ChainResultDto> snapshot = s.resultsFrom(0);
        assertEquals(1, snapshot.size());
        assertNull(snapshot.get(0).classification(), "slot should be pending (dispatched, no result yet)");
        assertFalse(s.completed.get() > 0, "should not be completed yet");

        releaseResponse.countDown();
        loop.join(2000);
        assertFalse(loop.isAlive());
        assertEquals(1, s.completed.get());
    }

    // ---- stop --------------------------------------------------------

    /**
     * Like {@code RequestLoopRunner}'s PARALLEL mode, {@code workers.submit()}
     * never blocks (unbounded queue), so - by design, matching the "never wait
     * before dispatching the next one" requirement - EVERY slot in the run gets
     * marked dispatched almost immediately, regardless of {@code Stop}. What
     * {@code Stop} actually controls is which of those dispatched requests are
     * still allowed to be sent for real: a worker that picks up a slot after
     * cancellation has been observed resolves it as "Cancelled" instead of
     * calling {@code oneRun}.
     */
    @Test
    void stoppingStopsSendingNotYetPickedUpRequestsButKeepsCompletedResults() throws InterruptedException {
        ChainState s = state(2, 50); // 100 dispatches
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));
        AtomicInteger actuallySent = new AtomicInteger();
        Function<SendRequestDto, RunOutcome> oneRun = r -> {
            actuallySent.incrementAndGet();
            sleep(10);
            return ok(10);
        };

        Thread loop = new Thread(() -> runner.execute(s, chain, 0L, oneRun));
        loop.start();
        Thread.sleep(15); // let the first small batch actually start sending
        s.cancelled.set(true);
        loop.join(3000);

        assertFalse(loop.isAlive(), "loop did not return after stop");
        assertEquals(100, s.dispatched.get(), "dispatch order is fixed immediately, unaffected by Stop");
        assertTrue(actuallySent.get() > 0, "some requests should have actually been sent");
        assertTrue(actuallySent.get() < 100, "should not have sent all 100 after stop");
        assertEquals(100, s.completed.get(), "every slot still resolves - sent ones normally, the rest as cancelled");
    }

    // ---- cooldown between loop iterations ---------------------------

    /**
     * cooldownMs = 0 must behave exactly as before: no pause anywhere, and the
     * chain never reports itself as "cooling down".
     */
    @Test
    void zeroCooldownAddsNoDelayAndNeverReportsCoolingDown() {
        ChainState s = state(2, 4, 0L); // 8 dispatches over 4 iterations
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        long t0 = System.nanoTime();
        runner.execute(s, chain, 0L, r -> ok(1));
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(8, s.completed.get());
        assertFalse(s.coolingDown, "must not be left in the cooling-down state");
        assertTrue(totalMs < 150, "zero cooldown should add no measurable delay: " + totalMs + "ms");
    }

    /**
     * With a positive cooldown and N iterations there must be exactly N-1
     * pauses: one between each pair of consecutive iterations, none before the
     * first and none after the last.
     */
    @Test
    void positiveCooldownPausesBetweenIterationsButNotAfterTheLast() {
        long cooldownMs = 100L;
        ChainState s = state(2, 3, cooldownMs); // 3 iterations => expect 2 cooldowns
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        long t0 = System.nanoTime();
        runner.execute(s, chain, cooldownMs, r -> ok(1));
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(6, s.completed.get());
        assertFalse(s.coolingDown);
        assertTrue(totalMs >= 2 * cooldownMs - 30,
                "expected at least two ~" + cooldownMs + "ms cooldowns, took only " + totalMs + "ms");
        assertTrue(totalMs < 3 * cooldownMs,
                "took " + totalMs + "ms - looks like a cooldown also ran after the final iteration");
    }

    /**
     * More direct check that nothing waits after the final iteration: the gap
     * between the last request being handed to {@code oneRun} and {@code
     * execute} returning must be far smaller than one cooldown.
     */
    @Test
    void noCooldownRunsAfterTheFinalIteration() {
        long cooldownMs = 200L;
        ChainState s = state(1, 3, cooldownMs);
        List<SendRequestDto> chain = List.of(req("https://example.com/only"));
        java.util.concurrent.atomic.AtomicLong lastDispatchNanos = new java.util.concurrent.atomic.AtomicLong();

        runner.execute(s, chain, cooldownMs, r -> {
            lastDispatchNanos.set(System.nanoTime());
            return ok(1);
        });
        long tailMs = (System.nanoTime() - lastDispatchNanos.get()) / 1_000_000;

        assertEquals(3, s.completed.get());
        assertTrue(tailMs < cooldownMs / 2,
                "execute() returned " + tailMs + "ms after the last dispatch - a trailing cooldown ran");
    }

    /**
     * A Stop pressed while the runner is waiting out a cooldown must break the
     * wait promptly (not block for the whole interval) and must not dispatch any
     * further iterations.
     */
    @Test
    void stopDuringCooldownEndsPromptlyAndDispatchesNoFurtherIterations() throws InterruptedException {
        long cooldownMs = 5000L; // deliberately long; the test must not wait this out
        ChainState s = state(2, 5, cooldownMs); // would be 10 dispatches without the stop
        List<SendRequestDto> chain = List.of(req("https://example.com/r1"), req("https://example.com/r2"));

        Thread loop = new Thread(() -> runner.execute(s, chain, cooldownMs, r -> ok(1)));
        long t0 = System.nanoTime();
        loop.start();

        assertTrue(waitUntil(() -> s.coolingDown, 2000), "runner never entered the cooldown");
        s.cancelled.set(true);
        loop.join(1000);
        long totalMs = (System.nanoTime() - t0) / 1_000_000;

        assertFalse(loop.isAlive(), "cooldown was not cancellable - loop still running after Stop");
        assertTrue(totalMs < cooldownMs, "Stop did not cut the cooldown short: " + totalMs + "ms");
        assertFalse(s.coolingDown, "must not be left in the cooling-down state after Stop");
        assertEquals(2, s.dispatched.get(),
                "only the first iteration's requests should have been dispatched before the stop");
    }
}
