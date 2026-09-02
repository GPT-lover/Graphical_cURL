import { useEffect, useMemo, useState } from 'react'

const MAX_RUNS = 5000
const WARN_OVER = 1000
const MAX_DELAY = 60000

function statusClassOf(result) {
  const c = result.classification
  return c === 'SUCCESS' ? 'ok' : c === 'REDIRECT' ? 'redirect' : 'error'
}

/**
 * "Run Request Multiple Times" dialog. One component, four views driven by
 * `run.phase`:
 *   idle      -> config form (+ a confirmation view when runs > 1000)
 *   running   -> live progress + Stop + results table
 *   done/stopped -> summary + results table + Close
 *   error     -> message + Close
 *
 * Props:
 *   open, onClose
 *   run  - the object from useRunMultiple()
 *   onRun({ runs, delayMs, mode })  - App adds the request snapshot + env and starts
 */
export default function RunMultipleModal({ open, onClose, run, onRun }) {
  const [runs, setRuns] = useState('10')
  const [delayMs, setDelayMs] = useState('100')
  const [mode, setMode] = useState('SEQUENTIAL')
  const [formError, setFormError] = useState(null)
  const [pendingCount, setPendingCount] = useState(null) // set when the >1000 warning is shown

  useEffect(() => {
    if (open && run.phase === 'idle') {
      setFormError(null)
      setPendingCount(null)
    }
  }, [open, run.phase])

  useEffect(() => {
    if (!open) return undefined
    function onKey(e) {
      if (e.key === 'Escape' && run.phase !== 'running') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, run.phase])

  const sortedResults = useMemo(
    () => [...run.results].sort((a, b) => a.run - b.run),
    [run.results],
  )

  if (!open) return null

  function beginRun(n, d) {
    setPendingCount(null)
    onRun({ runs: n, delayMs: d, mode })
  }

  function handleRunClick() {
    const n = Number(runs)
    const d = Number(delayMs)
    if (!Number.isInteger(n) || n < 1) {
      setFormError('Enter a whole number of runs between 1 and 5000.')
      return
    }
    if (n > MAX_RUNS) {
      setFormError(`The maximum is ${MAX_RUNS} runs.`)
      return
    }
    if (!Number.isInteger(d) || d < 0) {
      setFormError('Delay must be 0 ms or more.')
      return
    }
    if (d > MAX_DELAY) {
      setFormError(`Delay must be ${MAX_DELAY} ms or less.`)
      return
    }
    setFormError(null)
    if (n > WARN_OVER) {
      setPendingCount(n) // show the confirmation, do NOT run yet
      return
    }
    beginRun(n, d)
  }

  const running = run.phase === 'running'
  const finished = run.phase === 'done' || run.phase === 'stopped'
  const dismissable = !running

  return (
    <div className="modal-overlay" onMouseDown={dismissable ? onClose : undefined}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="run-multiple-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="run-multiple-title">
            {run.phase === 'error'
              ? 'Could not start the loop'
              : running
                ? 'Running requests…'
                : run.phase === 'stopped'
                  ? 'Stopped'
                  : finished
                    ? 'Completed'
                    : pendingCount != null
                      ? 'Warning'
                      : 'Run Request Multiple Times'}
          </h2>
          {dismissable && (
            <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
              &times;
            </button>
          )}
        </div>

        {/* ---- config form ---- */}
        {run.phase === 'idle' && pendingCount == null && (
          <>
            <label className="field">
              <span className="field__label">Number of runs</span>
              <input
                className="input"
                type="number"
                min="1"
                max={MAX_RUNS}
                value={runs}
                onChange={(e) => setRuns(e.target.value)}
              />
            </label>
            <label className="field">
              <span className="field__label">Delay between requests (ms)</span>
              <input
                className="input"
                type="number"
                min="0"
                max={MAX_DELAY}
                value={delayMs}
                onChange={(e) => setDelayMs(e.target.value)}
              />
            </label>
            <fieldset className="field run-mode">
              <span className="field__label">Execution mode</span>
              <label>
                <input
                  type="radio"
                  name="run-mode"
                  checked={mode === 'SEQUENTIAL'}
                  onChange={() => setMode('SEQUENTIAL')}
                />{' '}
                Sequential
              </label>
              <label>
                <input
                  type="radio"
                  name="run-mode"
                  checked={mode === 'PARALLEL'}
                  onChange={() => setMode('PARALLEL')}
                />{' '}
                Parallel
              </label>
            </fieldset>

            {formError && <div className="modal__error" role="alert">{formError}</div>}

            <div className="modal__actions">
              <button type="button" className="btn" onClick={onClose}>Cancel</button>
              <button type="button" className="btn btn--primary" onClick={handleRunClick}>Run</button>
            </div>
          </>
        )}

        {/* ---- >1000 confirmation ---- */}
        {run.phase === 'idle' && pendingCount != null && (
          <>
            <p className="modal__hint">
              You are about to run this request <strong>{pendingCount}</strong> times.
              <br />
              This may generate a large number of requests and could put significant load
              on the target server. Are you sure you want to continue?
            </p>
            <div className="modal__actions">
              <button type="button" className="btn" onClick={() => setPendingCount(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => beginRun(pendingCount, Number(delayMs))}
              >
                Continue
              </button>
            </div>
          </>
        )}

        {/* ---- error ---- */}
        {run.phase === 'error' && (
          <>
            <div className="modal__error" role="alert">{run.error}</div>
            <div className="modal__actions">
              <button type="button" className="btn btn--primary" onClick={onClose}>Close</button>
            </div>
          </>
        )}

        {/* ---- running / finished: progress + table ---- */}
        {(running || finished) && (
          <>
            <div className="run-progress">
              <span className="run-progress__count">
                {run.progress.completed} / {run.progress.total}
              </span>
              <span className="run-progress__stat run-progress__stat--ok">
                Successful: {run.progress.successful}
              </span>
              {run.progress.redirects > 0 && (
                <span className="run-progress__stat run-progress__stat--redirect">
                  3xx: {run.progress.redirects}
                </span>
              )}
              <span className="run-progress__stat run-progress__stat--fail">
                Failed: {run.progress.failed}
              </span>
              <span className="run-progress__mode">Mode: {run.mode}</span>
            </div>

            {finished && run.summary && (
              <div className="run-summary">
                <div>Total runs: {run.summary.total}</div>
                <div>Completed: {run.summary.completed} / {run.summary.total}</div>
                <div>Successful: {run.summary.successful}</div>
                {run.summary.redirects > 0 && <div>3xx responses: {run.summary.redirects}</div>}
                <div>Failed: {run.summary.failed}</div>
                <div>Average response time: {run.summary.averageDurationMs} ms</div>
                <div>Total elapsed time: {(run.summary.elapsedMs / 1000).toFixed(1)} s</div>
                <div>Mode: {run.summary.mode}</div>
                {run.summary.mode === 'PARALLEL' && (
                  <p className="run-summary__note">
                    Average is per individual request; total elapsed is the wall-clock time
                    for the whole loop.
                  </p>
                )}
                {run.summary.stopped && (
                  <p className="run-summary__note">The loop was stopped early.</p>
                )}
              </div>
            )}

            <div className="run-table__wrap">
              <table className="run-table">
                <thead>
                  <tr>
                    <th>Run</th>
                    <th>Status</th>
                    <th>Duration</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedResults.map((r) => (
                    <tr key={r.run}>
                      <td>{r.run}</td>
                      <td>
                        <span className={`status-code status-code--${statusClassOf(r)}`}>
                          {r.error ? r.error : r.status}
                        </span>
                      </td>
                      <td>{r.durationMs != null ? `${r.durationMs} ms` : '—'}</td>
                    </tr>
                  ))}
                  {sortedResults.length === 0 && (
                    <tr>
                      <td colSpan="3" className="run-table__empty">Waiting for the first result…</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="modal__actions">
              {running ? (
                <button type="button" className="btn" onClick={run.stop}>Stop</button>
              ) : (
                <button type="button" className="btn btn--primary" onClick={onClose}>Close</button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
