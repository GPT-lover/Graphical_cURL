import { useCallback, useEffect, useRef, useState } from 'react'
import {
  detectHydra,
  fetchHydraSettings,
  getHydraAttackStatus,
  startHydraAttack,
  stopHydraAttack,
  updateHydraSettings,
} from '../api/client.js'

const POLL_MS = 500

const DEFAULT_SETTINGS = {
  executionMode: 'LOCAL',
  executablePath: '',
  wslDistro: 'kali-linux',
  wslHydraPath: '/usr/bin/hydra',
}

/**
 * Drives the Hydra tool: loads/saves the execution settings (a local
 * executable, or Hydra installed inside a WSL distribution), runs the "-h"
 * detect probe, and drives one attack at a time with the same
 * start -> poll -> stop shape as useRunMultiple/useChain.
 *
 *   settings       - { executionMode, executablePath, wslDistro, wslHydraPath }
 *   settingsLoaded - true once the initial GET has completed (success or not)
 *   saveSettings(settings) - PUT the settings; updates `settings` on success
 *   detect(settings) - probe Hydra with the given (possibly unsaved) settings,
 *                      or the saved ones if omitted; result -> detectResult
 *   detecting      - true while a detect probe is in flight
 *   phase          - 'idle' | 'running' | 'done' | 'stopped' | 'error'
 *   output         - accumulated Hydra stdout/stderr lines
 *   exitCode       - Hydra's process exit code once finished, else null
 *   error          - message when phase === 'error'
 *   start(config) / stop() / reset()
 */
export function useHydra() {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS)
  const [settingsLoaded, setSettingsLoaded] = useState(false)
  const [detectResult, setDetectResult] = useState(null)
  const [detecting, setDetecting] = useState(false)

  const [phase, setPhase] = useState('idle')
  const [output, setOutput] = useState([])
  const [exitCode, setExitCode] = useState(null)
  const [error, setError] = useState(null)

  const attackIdRef = useRef(null)
  const timerRef = useRef(null)
  const outputRef = useRef([])

  useEffect(() => {
    let cancelled = false
    fetchHydraSettings()
      .then((s) => {
        if (!cancelled) setSettings(s)
      })
      .catch(() => {
        // leave the default { executablePath: '' } - the modal shows a blank field
      })
      .finally(() => {
        if (!cancelled) setSettingsLoaded(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const saveSettings = useCallback(async (newSettings) => {
    const s = await updateHydraSettings(newSettings)
    setSettings(s)
    setDetectResult(null)
    return s
  }, [])

  const detect = useCallback(async (settingsToTest) => {
    setDetecting(true)
    setDetectResult(null)
    try {
      const result = await detectHydra(settingsToTest)
      setDetectResult(result)
      return result
    } catch (err) {
      const result = { available: false, message: err.message }
      setDetectResult(result)
      return result
    } finally {
      setDetecting(false)
    }
  }, [])

  const stopPolling = useCallback(() => {
    clearTimeout(timerRef.current)
    timerRef.current = null
  }, [])

  useEffect(() => () => stopPolling(), [stopPolling])

  const poll = useCallback(async () => {
    const attackId = attackIdRef.current
    if (!attackId) return
    try {
      const s = await getHydraAttackStatus(attackId, outputRef.current.length)
      if (Array.isArray(s.output) && s.output.length > 0) {
        outputRef.current = outputRef.current.concat(s.output)
        setOutput(outputRef.current)
      }
      if (s.status === 'RUNNING') {
        timerRef.current = setTimeout(poll, POLL_MS)
      } else {
        setExitCode(s.exitCode ?? null)
        if (s.status === 'ERROR') {
          setError(s.errorMessage || 'Hydra exited with an error.')
          setPhase('error')
        } else {
          setPhase(s.status === 'STOPPED' ? 'stopped' : 'done')
        }
        stopPolling()
      }
    } catch (err) {
      setError(err.message)
      setPhase('error')
      stopPolling()
    }
  }, [stopPolling])

  const start = useCallback(
    async (config) => {
      stopPolling()
      attackIdRef.current = null
      outputRef.current = []
      setOutput([])
      setExitCode(null)
      setError(null)
      setPhase('running')
      try {
        const { attackId } = await startHydraAttack(config)
        attackIdRef.current = attackId
        timerRef.current = setTimeout(poll, 0)
      } catch (err) {
        setError(err.detail ? `${err.message}\n${err.detail}` : err.message)
        setPhase('error')
      }
    },
    [poll, stopPolling],
  )

  const stop = useCallback(async () => {
    const attackId = attackIdRef.current
    if (!attackId) return
    try {
      await stopHydraAttack(attackId)
    } catch {
      // keep polling; the process may still be finishing shutdown
    }
  }, [])

  const reset = useCallback(() => {
    stopPolling()
    attackIdRef.current = null
    outputRef.current = []
    setPhase('idle')
    setOutput([])
    setExitCode(null)
    setError(null)
  }, [stopPolling])

  return {
    settings,
    settingsLoaded,
    saveSettings,
    detectResult,
    detecting,
    detect,
    phase,
    output,
    exitCode,
    error,
    start,
    stop,
    reset,
  }
}
