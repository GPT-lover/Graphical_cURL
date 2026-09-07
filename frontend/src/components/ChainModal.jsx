import { useEffect, useState } from 'react'
import { HTTP_METHODS } from '../constants/httpMethods.js'

const MAX_LOOPS = 5000
const MAX_CHAIN_LENGTH = 20
const MAX_COOLDOWN_MS = 60000

function phaseClass(result) {
  if (result.classification == null) return 'pending'
  if (result.classification === 'SUCCESS') return 'ok'
  if (result.classification === 'REDIRECT') return 'redirect'
  return 'error'
}

function phaseLabel(result) {
  if (result.classification == null) return 'Dispatched'
  if (result.error) return result.error
  return String(result.status)
}

/**
 * "Request Chain" dialog: reorder/remove the requests that were added via the
 * editor's "Add to Chain" button, set a loop count, and execute the chain.
 *
 * Requests are DISPATCHED strictly in the order shown, but the chain never
 * waits for one's HTTP response before dispatching the next - responses are
 * collected as they arrive and matched back to the (iteration, request) that
 * produced them. The results table distinguishes "Dispatched" (sent, no
 * response yet) from a completed status code or a failure.
 *
 * Props:
 *   open, onClose
 *   chain - the object from useChainBuilder() (steps, loops, add/remove/move/update)
 *   run   - the object from useChain()
 *   onRun({ requests, loops }) - App adds the active environment and starts
 */
export default function ChainModal({ open, onClose, chain, run, onRun }) {
  const [formError, setFormError] = useState(null)

  useEffect(() => {
    if (open && run.phase === 'idle') {
      setFormError(null)
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

  if (!open) return null

  function handleRunClick() {
    if (chain.steps.length < 1) {
      setFormError('Add at least one request to the chain first.')
      return
    }
    if (chain.steps.length > MAX_CHAIN_LENGTH) {
      setFormError(`A chain can have at most ${MAX_CHAIN_LENGTH} requests.`)
      return
    }
    if (chain.steps.some((step) => !step.url || !step.url.trim())) {
      setFormError('Every request in the chain needs a URL.')
      return
    }
    const n = Number(chain.loops)
    if (!Number.isInteger(n) || n < 1) {
      setFormError(`Enter a whole number of loops between 1 and ${MAX_LOOPS}.`)
      return
    }
    if (n > MAX_LOOPS) {
      setFormError(`The maximum is ${MAX_LOOPS} loops.`)
      return
    }
    const cooldownMs = Number(chain.cooldown)
    if (!Number.isInteger(cooldownMs) || cooldownMs < 0) {
      setFormError('Cooldown must be a whole number of milliseconds, 0 or more.')
      return
    }
    if (cooldownMs > MAX_COOLDOWN_MS) {
      setFormError(`Cooldown must be ${MAX_COOLDOWN_MS} ms or less.`)
      return
    }
    setFormError(null)
    onRun({ requests: chain.steps, loops: n, cooldownMs })
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
        aria-labelledby="chain-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="chain-title">
            {run.phase === 'error'
              ? 'Could not start the chain'
              : running
                ? 'Running chain…'
                : run.phase === 'stopped'
                  ? 'Stopped'
                  : finished
                    ? 'Completed'
                    : 'Request Chain'}
          </h2>
          {dismissable && (
            <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
              &times;
            </button>
          )}
        </div>

        {/* ---- builder ---- */}
        {run.phase === 'idle' && (
          <>
            <p className="modal__hint">
              Requests are dispatched in this order without waiting for the previous
              request&rsquo;s response. The whole chain repeats for the given number of loops,
              pausing for the cooldown between one iteration and the next.
            </p>

            <div className="chain-steps">
              {chain.steps.length === 0 && (
                <p className="chain-steps__empty">
                  No requests yet. Build a request above, then click &ldquo;Add to Chain&rdquo;.
                </p>
              )}
              {chain.steps.map((step, index) => {
                const headerCount = (step.headers ?? []).filter((h) => h.key.trim() !== '').length
                return (
                  <div className="chain-step" key={step.id}>
                    <span className="chain-step__index">{index + 1}</span>
                    <select
                      className="method-selector chain-step__method"
                      value={step.method}
                      onChange={(e) => chain.updateStep(step.id, 'method', e.target.value)}
                      aria-label={`Request ${index + 1} method`}
                    >
                      {HTTP_METHODS.map((m) => (
                        <option key={m} value={m}>
                          {m}
                        </option>
                      ))}
                    </select>
                    <input
                      className="input chain-step__url"
                      value={step.url}
                      onChange={(e) => chain.updateStep(step.id, 'url', e.target.value)}
                      placeholder="https://api.example.com/…"
                      spellCheck={false}
                      autoComplete="off"
                      aria-label={`Request ${index + 1} URL`}
                    />
                    <span className="chain-step__meta">
                      {headerCount > 0 && `${headerCount}h`}
                      {step.body ? ' body' : ''}
                    </span>
                    <button
                      type="button"
                      className="btn btn--icon"
                      disabled={index === 0}
                      onClick={() => chain.moveStep(step.id, -1)}
                      aria-label={`Move request ${index + 1} up`}
                      title="Move up"
                    >
                      &uarr;
                    </button>
                    <button
                      type="button"
                      className="btn btn--icon"
                      disabled={index === chain.steps.length - 1}
                      onClick={() => chain.moveStep(step.id, 1)}
                      aria-label={`Move request ${index + 1} down`}
                      title="Move down"
                    >
                      &darr;
                    </button>
                    <button
                      type="button"
                      className="btn btn--icon"
                      onClick={() => chain.removeStep(step.id)}
                      aria-label={`Remove request ${index + 1}`}
                      title="Remove"
                    >
                      &times;
                    </button>
                  </div>
                )
              })}
            </div>

            <label className="field">
              <span className="field__label">Loop count (repeats the whole chain)</span>
              <input
                className="input"
                type="number"
                min="1"
                max={MAX_LOOPS}
                value={chain.loops}
                onChange={(e) => chain.setLoops(e.target.value)}
              />
            </label>

            <label className="field">
              <span className="field__label">Cooldown between loops (ms)</span>
              <input
                className="input"
                type="number"
                min="0"
                max={MAX_COOLDOWN_MS}
                value={chain.cooldown}
                onChange={(e) => chain.setCooldown(e.target.value)}
              />
            </label>

            {formError && (
              <div className="modal__error" role="alert">
                {formError}
              </div>
            )}

            <div className="modal__actions">
              <button type="button" className="btn" onClick={onClose}>
                Cancel
              </button>
              <button type="button" className="btn btn--primary" onClick={handleRunClick}>
                Run Chain
              </button>
            </div>
          </>
        )}

        {/* ---- error ---- */}
        {run.phase === 'error' && (
          <>
            <div className="modal__error" role="alert">
              {run.error}
            </div>
            <div className="modal__actions">
              <button type="button" className="btn btn--primary" onClick={onClose}>
                Close
              </button>
            </div>
          </>
        )}

        {/* ---- running / finished: progress + table ---- */}
        {(running || finished) && (
          <>
            <div className="run-progress">
              <span className="run-progress__count">
                {run.progress.completed} / {run.progress.totalDispatches}
              </span>
              <span className="run-progress__stat">Dispatched: {run.progress.dispatched}</span>
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
              <span className="run-progress__mode">
                {run.progress.chainLength} request(s) × {run.progress.totalIterations} loop(s)
                {run.progress.cooldownMs > 0 && `, ${run.progress.cooldownMs} ms cooldown`}
              </span>
              {running && run.progress.coolingDown && (
                <span className="run-progress__stat">
                  Waiting {run.progress.cooldownMs} ms before the next loop…
                </span>
              )}
            </div>

            {finished && run.summary && (
              <div className="run-summary">
                <div>Total dispatches: {run.summary.totalDispatches}</div>
                <div>
                  Completed: {run.summary.completed} / {run.summary.totalDispatches}
                </div>
                <div>Successful: {run.summary.successful}</div>
                {run.summary.redirects > 0 && <div>3xx responses: {run.summary.redirects}</div>}
                <div>Failed: {run.summary.failed}</div>
                <div>Average response time: {run.summary.averageDurationMs} ms</div>
                <div>Total elapsed time: {(run.summary.elapsedMs / 1000).toFixed(1)} s</div>
                {run.summary.stopped && <p className="run-summary__note">The chain was stopped early.</p>}
              </div>
            )}

            <div className="run-table__wrap">
              <table className="run-table">
                <thead>
                  <tr>
                    <th>Iter</th>
                    <th>Request</th>
                    <th>Status</th>
                    <th>Duration</th>
                  </tr>
                </thead>
                <tbody>
                  {run.results.map((r) => {
                    const step = chain.steps[r.requestIndex]
                    return (
                      <tr key={`${r.iteration}:${r.requestIndex}`}>
                        <td>{r.iteration}</td>
                        <td>
                          R{r.requestIndex + 1}
                          {step ? ` ${step.method}` : ''}
                        </td>
                        <td>
                          <span className={`status-code status-code--${phaseClass(r)}`}>
                            {phaseLabel(r)}
                          </span>
                        </td>
                        <td>{r.durationMs != null ? `${r.durationMs} ms` : '—'}</td>
                      </tr>
                    )
                  })}
                  {run.results.length === 0 && (
                    <tr>
                      <td colSpan="4" className="run-table__empty">
                        Waiting for the first dispatch…
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="modal__actions">
              {running ? (
                <button type="button" className="btn" onClick={run.stop}>
                  Stop
                </button>
              ) : (
                <button type="button" className="btn btn--primary" onClick={onClose}>
                  Close
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
