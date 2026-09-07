import { useCallback, useEffect, useRef, useState } from 'react'
import { getChainStatus, startChain, stopChain } from '../api/client.js'

const POLL_MS = 400
const EMPTY_PROGRESS = {
  totalIterations: 0,
  chainLength: 0,
  totalDispatches: 0,
  dispatched: 0,
  completed: 0,
  successful: 0,
  redirects: 0,
  failed: 0,
  cooldownMs: 0,
  coolingDown: false,
}

function slotKey(result) {
  return `${result.iteration}:${result.requestIndex}`
}

/**
 * Drives a "request chain" run: POST to start, then poll status until the
 * backend reports DONE or STOPPED.
 *
 * Unlike run-multiple's append-only results, a chain slot changes IN PLACE: it
 * first appears "dispatched" (classification === null, no response yet), then
 * is updated again once its result arrives. The backend's `?offset=` therefore
 * returns a log of change-events rather than only-new results, and the same
 * slot can appear twice. This hook keeps a `(iteration, requestIndex) -> result`
 * map and re-derives a sorted array from it on every update, so the UI always
 * shows each slot's latest known state exactly once.
 *
 *   phase     - 'idle' | 'running' | 'done' | 'stopped' | 'error'
 *   progress  - { totalIterations, chainLength, totalDispatches, dispatched,
 *                 completed, successful, redirects, failed }
 *   results   - [{ iteration, requestIndex, status, durationMs, error, classification }]
 *               classification === null means "dispatched, no result yet"
 *   summary   - ChainSummaryDto once finished, else null
 *   error     - message when phase === 'error'
 *   start(payload) / stop() / reset()
 */
export function useChain() {
  const [phase, setPhase] = useState('idle')
  const [progress, setProgress] = useState(EMPTY_PROGRESS)
  const [results, setResults] = useState([])
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState(null)

  const chainIdRef = useRef(null)
  const timerRef = useRef(null)
  const offsetRef = useRef(0)
  const resultsMapRef = useRef(new Map())

  const stopPolling = useCallback(() => {
    clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  useEffect(() => () => stopPolling(), [stopPolling])

  const applyUpdates = useCallback((updates) => {
    for (const r of updates) {
      resultsMapRef.current.set(slotKey(r), r)
    }
    const merged = Array.from(resultsMapRef.current.values())
    merged.sort((a, b) => a.iteration - b.iteration || a.requestIndex - b.requestIndex)
    setResults(merged)
  }, [])

  const poll = useCallback(async () => {
    const chainId = chainIdRef.current
    if (!chainId) return
    try {
      const s = await getChainStatus(chainId, offsetRef.current)
      if (Array.isArray(s.results) && s.results.length > 0) {
        offsetRef.current += s.results.length
        applyUpdates(s.results)
      }
      setProgress({
        totalIterations: s.totalIterations,
        chainLength: s.chainLength,
        totalDispatches: s.totalDispatches,
        dispatched: s.dispatched,
        completed: s.completed,
        successful: s.successful,
        redirects: s.redirects,
        failed: s.failed,
        cooldownMs: s.cooldownMs ?? 0,
        coolingDown: s.coolingDown ?? false,
      })

      if (s.status === 'RUNNING') {
        timerRef.current = setTimeout(poll, POLL_MS)
      } else {
        setSummary(s.summary ?? null)
        setPhase(s.status === 'STOPPED' ? 'stopped' : 'done')
        stopPolling()
      }
    } catch (err) {
      setError(err.message)
      setPhase('error')
      stopPolling()
    }
  }, [applyUpdates, stopPolling])

  const start = useCallback(
    async (payload) => {
      stopPolling()
      chainIdRef.current = null
      offsetRef.current = 0
      resultsMapRef.current = new Map()
      setResults([])
      setSummary(null)
      setError(null)
      setProgress({
        ...EMPTY_PROGRESS,
        totalIterations: payload.loops,
        chainLength: payload.requests.length,
        totalDispatches: payload.loops * payload.requests.length,
        cooldownMs: payload.cooldownMs ?? 0,
      })
      setPhase('running')
      try {
        const { chainId } = await startChain(payload)
        chainIdRef.current = chainId
        timerRef.current = setTimeout(poll, 0)
      } catch (err) {
        setError(err.detail ? `${err.message}\n${err.detail}` : err.message)
        setPhase('error')
      }
    },
    [poll, stopPolling],
  )

  const stop = useCallback(async () => {
    const chainId = chainIdRef.current
    if (!chainId) return
    try {
      await stopChain(chainId)
    } catch {
      // keep polling anyway; the chain may still be finishing in-flight requests
    }
  }, [])

  const reset = useCallback(() => {
    stopPolling()
    chainIdRef.current = null
    offsetRef.current = 0
    resultsMapRef.current = new Map()
    setPhase('idle')
    setResults([])
    setSummary(null)
    setError(null)
    setProgress(EMPTY_PROGRESS)
  }, [stopPolling])

  return { phase, progress, results, summary, error, start, stop, reset }
}
