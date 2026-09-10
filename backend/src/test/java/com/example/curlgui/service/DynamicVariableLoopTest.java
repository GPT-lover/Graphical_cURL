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
 * End-to-end-ish tests for dynamic {@code {{random(N)}}} variables under the
 * existing loop and chain runners.
 *
 * <p>The "send one request" step stands in for
 * {@code RequestService#executeResolved}: like the real thing it calls
 * {@link DynamicVariableResolver#resolveRequest} on the request it was handed,
 * at the moment it is about to send, and then records what would have gone out.
 * That is exactly the point being tested - resolution happens per dispatch, not
 * once before the loop.
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
        sentBodies.add(resolver.resolveRequest(req).body());
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
    void aLoopWithoutTemplatesBehavesExactlyAsBefore() {
        SendRequestDto original = request("{\"name\":\"William\"}");
        RunState state = new RunState("test", RunMode.SEQUENTIAL, 5);

        loopRunner.execute(state, original, 0, send);

        assertEquals(5, sentBodies.size());
        assertEquals(1, Set.copyOf(sentBodies).size());
        assertTrue(sentBodies.stream().allMatch(b -> b.equals("{\"name\":\"William\"}")));
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
}
