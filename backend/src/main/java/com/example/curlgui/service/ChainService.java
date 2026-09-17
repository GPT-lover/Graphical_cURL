package com.example.curlgui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.curlgui.dto.ChainResultDto;
import com.example.curlgui.dto.ChainStartedDto;
import com.example.curlgui.dto.ChainStatusDto;
import com.example.curlgui.dto.RunChainRequestDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;

import jakarta.annotation.PreDestroy;

/**
 * Runs a "request chain": an ordered list of (possibly different) requests,
 * repeated for a number of loops, where each request is <b>dispatched</b> in
 * order but the chain never waits for a request's HTTP response before
 * dispatching the next one - see {@link ChainRunner} for how that's done.
 *
 * <p>Flow mirrors {@code RunMultipleService}: validate -&gt; resolve each
 * request's {@code {{variables}}} <b>once</b> (fail fast, no chain started on an
 * unknown variable or bad URL) -&gt; snapshot -&gt; kick the chain onto a
 * background thread and return a {@code chainId}. The runner delegates the
 * actual HTTP work to {@link RequestService#executeResolved} - the same code the
 * normal Send and run-multiple loop use.
 *
 * <p>History is intentionally not recorded for chain runs: unlike run-multiple
 * (which repeats one request, and records a single summarising row for the
 * whole loop), a chain is a heterogeneous list of requests, so there is no
 * single "the request" a chain's row could represent without either recording
 * one row per dispatch (defeating the "never one per iteration" rule
 * run-multiple already follows) or misrepresenting the run. This can be revisited
 * if per-chain history turns out to be wanted.
 */
@Service
public class ChainService {

    private static final Logger log = LoggerFactory.getLogger(ChainService.class);

    /** Backend safety limits - enforced regardless of the frontend. */
    static final int MAX_CHAIN_LENGTH = 20;
    static final int MAX_LOOPS = 5000;
    /** Extra guard on top of the two limits above: chainLength * loops. */
    static final int MAX_TOTAL_DISPATCHES = 20_000;
    /** Upper bound on the between-iterations cooldown; matches run-multiple's delay cap. */
    static final long MAX_COOLDOWN_MS = 60_000;
    /** JITTER's +/- range is capped the same as a FIXED cooldown. */
    static final long MAX_JITTER_MS = MAX_COOLDOWN_MS;
    /** WINDOW spreads the whole chain run across up to this many ms (1 hour). */
    static final long MAX_WINDOW_MS = 3_600_000L;
    private static final long RETENTION_MS = 10 * 60 * 1000L;

    private final RequestService requestService;
    private final EnvironmentVariableService environmentVariableService;
    private final EnvironmentVariableResolver variableResolver;
    private final DynamicVariableResolver dynamicResolver;
    private final ChainRunner chainRunner;

    private final ExecutorService orchestrators = new ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), daemon("chain-orchestrator"));
    private final Map<String, ChainState> chains = new ConcurrentHashMap<>();

    public ChainService(RequestService requestService,
                        EnvironmentVariableService environmentVariableService,
                        EnvironmentVariableResolver variableResolver,
                        DynamicVariableResolver dynamicResolver,
                        ChainRunner chainRunner) {
        this.requestService = requestService;
        this.environmentVariableService = environmentVariableService;
        this.variableResolver = variableResolver;
        this.dynamicResolver = dynamicResolver;
        this.chainRunner = chainRunner;
    }

    @PreDestroy
    void shutdown() {
        orchestrators.shutdownNow();
    }

    // ------------------------------------------------------------------

    public ChainStartedDto start(RunChainRequestDto dto) {
        sweepOldChains();

        if (dto == null || dto.requests() == null || dto.requests().isEmpty()) {
            throw new InvalidRequestException("At least one request is required in the chain.");
        }
        int chainLength = requireChainLength(dto.requests().size());
        int loops = requireLoops(dto.loops());
        long cooldownMs = requireCooldown(dto.cooldownMs());
        DelayPlan.Mode delayMode = requireDelayMode(dto.delayMode());
        long jitterMs = requireJitter(dto.jitterMs());
        long windowMs = delayMode == DelayPlan.Mode.WINDOW ? requireWindow(dto.windowMs()) : 0;
        DelayPlan delayPlan = DelayPlan.of(delayMode, cooldownMs, jitterMs, windowMs, loops);
        if (chainLength * loops > MAX_TOTAL_DISPATCHES) {
            throw new InvalidRequestException(
                    "This chain would dispatch " + (chainLength * loops) + " requests; the maximum is "
                            + MAX_TOTAL_DISPATCHES + ". Reduce the chain length or the loop count.");
        }

        // Resolve every step's environment variables ONCE, up front. If a
        // placeholder is unknown or a URL is invalid this throws (HTTP 400) and
        // no chain is created / no request is sent.
        List<SendRequestDto> resolved = new ArrayList<>(chainLength);
        for (SendRequestDto step : dto.requests()) {
            if (step == null) {
                throw new InvalidRequestException("Every request in the chain must be present.");
            }
            Map<String, String> variables = environmentVariableService.variablesFor(step.environmentId());
            SendRequestDto resolvedStep = variableResolver.resolveRequest(step, variables);
            // Probe this step's dynamic templates once, up front (see
            // RunMultipleService#start); the probe's values are discarded, every
            // dispatch generates its own.
            SendRequestDto probe = dynamicResolver.resolveRequest(resolvedStep);
            requestService.parseAndValidateUrl(probe.url()); // fail fast on a bad URL
            resolved.add(resolvedStep);
        }

        String id = UUID.randomUUID().toString();
        ChainState state = new ChainState(id, chainLength, loops, cooldownMs);
        chains.put(id, state);

        orchestrators.submit(() -> runChain(state, resolved, delayPlan));
        log.info("Chain started: {} request(s) x {} loop(s), {} ms cooldown", chainLength, loops, cooldownMs);
        return new ChainStartedDto(id);
    }

    public ChainStatusDto status(String chainId, int offset) {
        ChainState state = require(chainId);
        state.lastTouchedAtMillis = System.currentTimeMillis();
        return new ChainStatusDto(
                state.status.name(),
                state.totalIterations,
                state.chainLength,
                state.totalDispatches,
                state.dispatched.get(),
                state.completed.get(),
                state.successful.get(),
                state.redirects.get(),
                state.failed.get(),
                state.cooldownMs,
                state.status == ChainState.Status.RUNNING && state.coolingDown,
                state.currentWaitMs,
                state.resultsFrom(offset),
                state.status == ChainState.Status.RUNNING ? null : state.summary());
    }

    public void stop(String chainId) {
        require(chainId).cancelled.set(true);
    }

    // ------------------------------------------------------------------

    private void runChain(ChainState state, List<SendRequestDto> resolved, DelayPlan delayPlan) {
        Function<SendRequestDto, RunOutcome> oneRun = req -> {
            try {
                SendResponseDto response = requestService.executeResolved(req);
                return new RunOutcome(response.statusCode(), response.durationMs(), null);
            } catch (RequestExecutionException ex) {
                return new RunOutcome(null, null, "Network Error");
            } catch (RuntimeException ex) {
                return new RunOutcome(null, null, "Error");
            }
        };
        try {
            chainRunner.execute(state, resolved, delayPlan, oneRun);
        } catch (RuntimeException ex) {
            log.warn("Chain run ended abnormally: {}", ex.getClass().getSimpleName());
        } finally {
            state.finishedAtNanos = System.nanoTime();
            state.status = state.cancelled.get() ? ChainState.Status.STOPPED : ChainState.Status.DONE;
            state.lastTouchedAtMillis = System.currentTimeMillis();
        }
    }

    private ChainState require(String chainId) {
        ChainState state = chains.get(chainId);
        if (state == null) {
            throw new NotFoundException("Chain \"" + chainId + "\" not found.");
        }
        return state;
    }

    private void sweepOldChains() {
        long now = System.currentTimeMillis();
        chains.values().removeIf(s ->
                s.status != ChainState.Status.RUNNING && now - s.lastTouchedAtMillis > RETENTION_MS);
    }

    // ---- validation (unit-tested directly) ------------------------

    static int requireChainLength(Integer length) {
        if (length == null || length < 1) {
            throw new InvalidRequestException("A chain must have at least one request.");
        }
        if (length > MAX_CHAIN_LENGTH) {
            throw new InvalidRequestException("A chain must not exceed " + MAX_CHAIN_LENGTH + " requests.");
        }
        return length;
    }

    static int requireLoops(Integer loops) {
        if (loops == null) {
            throw new InvalidRequestException("Loop count is required.");
        }
        if (loops < 1) {
            throw new InvalidRequestException("Loop count must be at least 1.");
        }
        if (loops > MAX_LOOPS) {
            throw new InvalidRequestException("Loop count must not exceed " + MAX_LOOPS + ".");
        }
        return loops;
    }

    /**
     * Cooldown between loop iterations. A missing/null value means 0 (no
     * cooldown - the original behaviour), so old clients and saved chains keep
     * working unchanged.
     */
    static long requireCooldown(Long cooldownMs) {
        long c = cooldownMs == null ? 0L : cooldownMs;
        if (c < 0) {
            throw new InvalidRequestException("Cooldown must not be negative.");
        }
        if (c > MAX_COOLDOWN_MS) {
            throw new InvalidRequestException("Cooldown must not exceed " + MAX_COOLDOWN_MS + " ms.");
        }
        return c;
    }

    /** Missing/blank -&gt; FIXED, so existing clients and saved chains keep behaving exactly as before. */
    static DelayPlan.Mode requireDelayMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return DelayPlan.Mode.FIXED;
        }
        try {
            return DelayPlan.Mode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Delay mode must be FIXED, JITTER or WINDOW.");
        }
    }

    /** Only meaningful under JITTER; null/missing -&gt; 0 (no jitter). */
    static long requireJitter(Long jitterMs) {
        long j = jitterMs == null ? 0L : jitterMs;
        if (j < 0) {
            throw new InvalidRequestException("Jitter must not be negative.");
        }
        if (j > MAX_JITTER_MS) {
            throw new InvalidRequestException("Jitter must not exceed " + MAX_JITTER_MS + " ms.");
        }
        return j;
    }

    /** Only called (and required) under WINDOW pacing. */
    static long requireWindow(Long windowMs) {
        if (windowMs == null) {
            throw new InvalidRequestException("A window duration is required for WINDOW pacing.");
        }
        if (windowMs <= 0) {
            throw new InvalidRequestException("Window duration must be greater than 0 ms.");
        }
        if (windowMs > MAX_WINDOW_MS) {
            throw new InvalidRequestException("Window duration must not exceed " + MAX_WINDOW_MS + " ms.");
        }
        return windowMs;
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }
}
