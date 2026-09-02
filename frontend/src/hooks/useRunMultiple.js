import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getRunMultipleStatus,
  startRunMultiple,
  stopRunMultiple,
} from '../api/client.js'

const POLL_MS = 400
const EMPTY_PROGRESS = { total: 0, completed: 0, successful: 0, redirects: 0, failed: 0 }

/**
 * Drives a "run multiple" loop: POST to start, then poll status until the
 * backend reports DONE or STOPPED. The UI is updated on every poll, so it never
 * freezes. Results arrive incrementally (only ones the client doesn't have yet).
 *
 *   phase     - 'idle' | 'running' | 'done' | 'stopped' | 'error'
 *   progress  - { total, completed, successful, redirects, failed }
 *   results   - [{ run, status, durationMs, error, classification }]
 *   summary   - RunSummaryDto once finished, else null
 *   mode      - 'SEQUENTIAL' | 'PARALLEL'
 *   error     - message when phase === 'error'
 *   start(payload) / stop() / reset()
 */
export function useRunMultiple() {
  const [phase, setPhase] = useState('idle')
  const [progress, setProgress] = useState(EMPTY_PROGRESS)
  const [results, setResults] = useState([])
  const [summary, setSummary] = useState(null)
  const [mode, setMode] = useState('SEQUENTIAL')
  const [error, setError] = useState(null)

  const runIdRef = useRef(null)
  const timerRef = useRef(null)
  const resultsRef = useRef([])

  const stopPolling = useCallback(() => {
    clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  useEffect(() => () => stopPolling(), [stopPolling])

  const poll = useCallback(async () => {
    const runId = runIdRef.current
    if (!runId) return
    try {
      const s = await getRunMultipleStatus(runId, resultsRef.current.length)
      if (Array.isArray(s.results) && s.results.length > 0) {
        resultsRef.current = resultsRef.current.concat(s.results)
        setResults(resultsRef.current)
      }
      setProgress({
        total: s.total,
        completed: s.completed,
        successful: s.successful,
        redirects: s.redirects,
        failed: s.failed,
      })
      setMode(s.mode)

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
  }, [stopPolling])

  const start = useCallback(
    async (payload) => {
      stopPolling()
      runIdRef.current = null
      resultsRef.current = []
      setResults([])
      setSummary(null)
      setError(null)
      setMode(payload.mode)
      setProgress({ ...EMPTY_PROGRESS, total: payload.runs })
      setPhase('running')
      try {
        const { runId } = await startRunMultiple(payload)
        runIdRef.current = runId
        timerRef.current = setTimeout(poll, 0)
      } catch (err) {
        setError(err.detail ? `${err.message}\n${err.detail}` : err.message)
        setPhase('error')
      }
    },
    [poll, stopPolling],
  )

  const stop = useCallback(async () => {
    const runId = runIdRef.current
    if (!runId) return
    try {
      await stopRunMultiple(runId)
    } catch {
      // keep polling anyway; the loop may still be finishing in-flight requests
    }
  }, [])

  const reset = useCallback(() => {
    stopPolling()
    runIdRef.current = null
    resultsRef.current = []
    setPhase('idle')
    setResults([])
    setSummary(null)
    setError(null)
    setProgress(EMPTY_PROGRESS)
  }, [stopPolling])

  return { phase, progress, results, summary, mode, error, start, stop, reset }
}
