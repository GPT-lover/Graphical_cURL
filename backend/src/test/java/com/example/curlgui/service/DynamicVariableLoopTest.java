package com.example.curlgui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.curlgui.dto.RunMode;
import com.example.curlgui.dto.SendRequestDto;

/**
 * End-to-end-ish tests for dynamic {@code {{random(N)}}} / {@code
 * {{increment(N)}}} variables under the existing loop and chain runners.
 *
 * <p>The "send one request" step stands in for
 * {@code RequestService#executeResolved}: like the real thing it reads {@link
 * IterationContext#current()} and calls {@link
 * DynamicVariableResolver#resolveRequest(SendRequestDto, int)} on the request
 * it was handed, at the moment it is about to send, and then records what
 * would have gone out. That is exactly the point being tested - resolution
 * happens per dispatch, not once before the loop, and each dispatch sees the
 * right iteration number.
 */
class DynamicVariableLoopTest {

    private final RequestLoopRunner loopRunner = new RequestLoopRunner();
    private final ChainRunner chainRunner = new ChainRunner();
    private final DynamicVariableResolver resolver = new DynamicVariableResolver();

    private final ConcurrentLinkedQueue<String> sentBodies = new ConcurrentLinkedQueue<>();

    @AfterEach
    void tearDown() {
        loopRunner.shutdown();
        chainRunner.shutdown();
    }

    /** Mirrors RequestService: resolve on a copy immediately before sending. */
    private final Function<SendRequestDto, RunOutcome> send = req -> {
        sentBodies.add(resolver.resolveRequest(req, IterationContext.current()).body());
        return new RunOutcome(200, 1L, null);
    };

    private static SendRequestDto request(String body) {
        return new SendRequestDto("POST", "https://example.com", List.of(), List.of(), body, null);
    }

    // ---- run-multiple loop -----------------------------------------

    @Test
    void sequentialLoopResolvesTheTemplateSeparatelyForEveryIteration() {
        SendRequestDto original = request("[\"{{random(50)}}\"]");
        RunState state = new RunState("test", RunMode.SEQUENTIAL, 100);

        loopRunner.execute(state, original, 0, send);

        assertEquals(100, sentBodies.size());
        assertEquals(100, Set.copyOf(sentBodies).size(), "an iteration reused another's value");
        for (String body : sentBodies) {
            assertEquals("[\"".length() + 50 + "\"]".length(), body.length(), body);
        }
        // The stored request is untouched and can be looped again.
        assertEquals("[\"{{random(50)}}\"]", original.body());
    }

    @Test
    void parallelLoopNeverReusesOneGeneratedValue() {
        SendRequestDto original = request("{{random(40)}}");
        RunState state = new RunState("test", RunMode.PARALLEL, 200);

        loopRunner.execute(state, original, 0, send);

        assertEquals(200, sentBodies.size());
        assertEquals(200, Set.copyOf(sentBodies).size(), "two parallel tasks sent the same value");
        assertEquals("{{random(40)}}", original.body());
    }

    @Test
    void sequentialLoopIncrementsOnceForEveryIteration() {
        SendRequestDto original = request("{{increment(1)}}");
        RunState state = new RunState("test", RunMode.SEQUENTIAL, 5);

        loopRunner.execute(state, original, 0, send);

        assertEquals(List.of("1", "2", "3", "4", "5"), List.copyOf(sentBodies));
        assertEquals("{{increment(1)}}", original.body());
    }

    @Test
    void parallelLoopStillProducesOneValuePerIterationWithNoDuplicates() {
        SendRequestDto original = request("{{increment(1)}}");
        RunState state = new RunState("test", RunMode.PARALLEL, 200);

        loopRunner.execute(state, original, 0, send);

        assertEquals(200, sentBodies.size());
        assertEquals(200, Set.copyOf(sentBodies).size(), "two parallel iterations reused the same increment value");
        Set<String> expected = new java.util.HashSet<>();
        for (int i = 1; i <= 200; i++) {
            expected.add(Integer.toString(i));
        }
        assertEquals(expected, Set.copyOf(sentBodies));
    }

    @Test
    void aLoopWithoutTemplatesBehavesExactlyAsBefore() {
        SendRequestDto original = request("{\"name\":\"Alex\"}");
        RunState state = new RunState("test", RunMode.SEQUENTIAL, 5);

        loopRunner.execute(state, original, 0, send);

        assertEquals(5, sentBodies.size());
        assertEquals(1, Set.copyOf(sentBodies).size());
        assertTrue(sentBodies.stream().allMatch(b -> b.equals("{\"name\":\"Alex\"}")));
        assertEquals(5, state.completed.get());
        assertEquals(5, state.successful.get());
    }

    // ---- chains ----------------------------------------------------

    @Test
    void everyRequestOfEveryChainIterationGetsItsOwnValue() {
        SendRequestDto first = request("one-{{random(30)}}");
        SendRequestDto second = request("two-{{random(30)}}");
        ChainState state = new ChainState("test", 2, 50, 0);

        chainRunner.execute(state, List.of(first, second), 0, send);

        assertEquals(100, sentBodies.size());
        assertEquals(100, Set.copyOf(sentBodies).size(), "a chain dispatch reused another's value");
        assertEquals(50, sentBodies.stream().filter(b -> b.startsWith("one-")).count());
        assertEquals(50, sentBodies.stream().filter(b -> b.startsWith("two-")).count());
        assertEquals("one-{{random(30)}}", first.body());
        assertEquals("two-{{random(30)}}", second.body());
    }

    @Test
    void everyRequestOfAChainIterationSeesThatIterationsIncrementValue() {
        SendRequestDto first = request("one-{{increment(1)}}");
        SendRequestDto second = request("two-{{increment(1)}}");
        ChainState state = new ChainState("test", 2, 4, 0);

        chainRunner.execute(state, List.of(first, second), 0, send);

        assertEquals(8, sentBodies.size());
        for (int iteration = 1; iteration <= 4; iteration++) {
            assertTrue(sentBodies.contains("one-" + iteration), "missing iteration " + iteration + " for request 1");
            assertTrue(sentBodies.contains("two-" + iteration), "missing iteration " + iteration + " for request 2");
        }
    }
}
