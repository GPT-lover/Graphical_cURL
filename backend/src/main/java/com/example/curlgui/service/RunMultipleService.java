package com.example.curlgui.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

import com.example.curlgui.dto.RunMode;
import com.example.curlgui.dto.RunMultipleRequestDto;
import com.example.curlgui.dto.RunResultDto;
import com.example.curlgui.dto.RunStartedDto;
import com.example.curlgui.dto.RunStatusDto;
import com.example.curlgui.dto.RunSummaryDto;
import com.example.curlgui.dto.SendRequestDto;
import com.example.curlgui.dto.SendResponseDto;

import jakarta.annotation.PreDestroy;

/**
 * Runs the current request N times.
 *
 * <p>Flow: validate -&gt; resolve {{variables}} <b>once</b> (fail fast, no run
 * started on an unknown variable) -&gt; snapshot -&gt; kick the loop onto a
 * background thread and return a {@code runId}. The loop delegates the actual
 * HTTP work to {@link RequestService#executeResolved} - the same code the normal
 * Send uses - so environment resolution, cookies, headers, timeouts and response
 * handling all behave identically.
 *
 * <p>History: <b>one</b> sanitised record for the whole operation, never one per
 * iteration. Loop results hold only {run, status, duration, error} - no bodies,
 * headers or cookies.
 */
@Service
public class RunMultipleService {

    private static final Logger log = LoggerFactory.getLogger(RunMultipleService.class);

    /** Backend safety limit - enforced regardless of the frontend. */
    static final int MAX_RUNS = 5000;
    static final long MAX_DELAY_MS = 60_000;
    private static final long RETENTION_MS = 10 * 60 * 1000L;

    private final RequestService requestService;
    private final EnvironmentVariableService environmentVariableService;
    private final EnvironmentVariableResolver variableResolver;
    private final RequestLoopRunner loopRunner;
    private final RequestHistoryService historyService;

    private final ExecutorService orchestrators = new ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), daemon("loop-orchestrator"));
    private final Map<String, RunState> runs = new ConcurrentHashMap<>();

    public RunMultipleService(RequestService requestService,
                              EnvironmentVariableService environmentVariableService,
                              EnvironmentVariableResolver variableResolver,
                              RequestLoopRunner loopRunner,
                              RequestHistoryService historyService) {
        this.requestService = requestService;
        this.environmentVariableService = environmentVariableService;
        this.variableResolver = variableResolver;
        this.loopRunner = loopRunner;
        this.historyService = historyService;
    }

    @PreDestroy
    void shutdown() {
        orchestrators.shutdownNow();
    }

    // ------------------------------------------------------------------

    public RunStartedDto start(RunMultipleRequestDto dto) {
        sweepOldRuns();

        if (dto == null || dto.request() == null) {
            throw new InvalidRequestException("A request to run is required.");
        }
        int runCount = requireRuns(dto.runs());
        long delayMs = requireDelay(dto.delayMs());
        RunMode mode = parseMode(dto.mode());

        // Resolve environment variables ONCE. If a placeholder is unknown this
        // throws (HTTP 400) and no run is created / no request is sent.
        Map<String, String> variables =
                environmentVariableService.variablesFor(dto.request().environmentId());
        SendRequestDto resolved = variableResolver.resolveRequest(dto.request(), variables);
        requestService.parseAndValidateUrl(resolved.url()); // fail fast on a bad URL

        String id = UUID.randomUUID().toString();
        RunState state = new RunState(id, mode, runCount);
        runs.put(id, state);

        SendRequestDto original = dto.request();
        orchestrators.submit(() -> runLoop(state, resolved, original, delayMs));
        log.info("Run-multiple started: {} runs, {} mode", runCount, mode);
        return new RunStartedDto(id);
    }

    public RunStatusDto status(String runId, int offset) {
        RunState state = require(runId);
        state.lastTouchedAtMillis = System.currentTimeMillis();
        RunSummaryDto summary =
                state.status == RunState.Status.RUNNING ? null : buildSummary(state);
        return new RunStatusDto(
                state.status.name(),
                state.mode.name(),
                state.total,
                state.completed.get(),
                state.successful.get(),
                state.redirects.get(),
                state.failed.get(),
                state.resultsFrom(offset),
                summary);
    }

    public void stop(String runId) {
        require(runId).cancelled.set(true);
    }

    // ------------------------------------------------------------------

    private void runLoop(RunState state, SendRequestDto resolved, SendRequestDto original, long delayMs) {
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
            loopRunner.execute(state, resolved, delayMs, oneRun);
        } catch (RuntimeException ex) {
            log.warn("Run-multiple loop ended abnormally: {}", ex.getClass().getSimpleName());
        } finally {
            state.finishedAtNanos = System.nanoTime();
            state.status = state.cancelled.get() ? RunState.Status.STOPPED : RunState.Status.DONE;
            state.lastTouchedAtMillis = System.currentTimeMillis();
            recordHistory(state, original);
        }
    }

    /** One sanitised History row for the whole loop - never one per iteration. */
    private void recordHistory(RunState state, SendRequestDto original) {
        try {
            List<RunResultDto> all = state.resultsFrom(0);
            int lastStatus = all.stream()
                    .map(RunResultDto::status)
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> b)
                    .orElse(0);
            long avg = averageDuration(all);
            historyService.record(original, new SendResponseDto(lastStatus, Map.of(), "", avg, List.of()));
        } catch (Exception ex) {
            log.warn("Could not record run-multiple history: {}", ex.getClass().getSimpleName());
        }
    }

    private RunSummaryDto buildSummary(RunState state) {
        List<RunResultDto> all = state.resultsFrom(0);
        long elapsedMs = Math.round((state.finishedAtNanos - state.startedAtNanos) / 1_000_000.0);
        return new RunSummaryDto(
                state.total,
                state.completed.get(),
                state.successful.get(),
                state.redirects.get(),
                state.failed.get(),
                averageDuration(all),
                elapsedMs,
                state.mode.name(),
                state.status == RunState.Status.STOPPED);
    }

    private static long averageDuration(List<RunResultDto> results) {
        long sum = 0;
        int n = 0;
        for (RunResultDto r : results) {
            if (r.durationMs() != null) {
                sum += r.durationMs();
                n++;
            }
        }
        return n == 0 ? 0 : Math.round((double) sum / n);
    }

    private RunState require(String runId) {
        RunState state = runs.get(runId);
        if (state == null) {
            throw new NotFoundException("Run \"" + runId + "\" not found.");
        }
        return state;
    }

    private void sweepOldRuns() {
        long now = System.currentTimeMillis();
        runs.values().removeIf(s ->
                s.status != RunState.Status.RUNNING && now - s.lastTouchedAtMillis > RETENTION_MS);
    }

    // ---- validation (unit-tested directly) ------------------------

    static int requireRuns(Integer runs) {
        if (runs == null) {
            throw new InvalidRequestException("Number of runs is required.");
        }
        if (runs < 1) {
            throw new InvalidRequestException("Number of runs must be at least 1.");
        }
        if (runs > MAX_RUNS) {
            throw new InvalidRequestException("Number of runs must not exceed " + MAX_RUNS + ".");
        }
        return runs;
    }

    static long requireDelay(Long delayMs) {
        long d = delayMs == null ? 0L : delayMs;
        if (d < 0) {
            throw new InvalidRequestException("Delay must not be negative.");
        }
        if (d > MAX_DELAY_MS) {
            throw new InvalidRequestException("Delay must not exceed " + MAX_DELAY_MS + " ms.");
        }
        return d;
    }

    static RunMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return RunMode.SEQUENTIAL;
        }
        try {
            return RunMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Mode must be SEQUENTIAL or PARALLEL.");
        }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }
}
