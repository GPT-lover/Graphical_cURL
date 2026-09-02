import { useEffect, useMemo, useState } from 'react'
import { statusClass } from '../lib/http.js'
import { byteLength, formatBytes, statusText } from '../lib/response.js'
import ResponseBody from './response/ResponseBody.jsx'
import ResponseHeaders from './response/ResponseHeaders.jsx'
import ResponseRaw from './response/ResponseRaw.jsx'

const TABS = [
  { id: 'body', label: 'Body' },
  { id: 'headers', label: 'Headers' },
  { id: 'raw', label: 'Raw' },
]

/**
 * The response viewer. Four states:
 *   isSending      -> loading indicator
 *   error          -> a red box (network / app error - NOT an HTTP status)
 *   result         -> summary (status · time · size) + Body / Headers / Raw tabs
 *   none           -> the idle placeholder
 *
 * A 4xx/5xx from the target server is a normal `result` (our backend returns it
 * with HTTP 200), so it renders here like any response, just with a red status
 * pill - it is never shown as a network error.
 *
 * Props: result (SendResponseDto | null), error ({message, detail?} | null),
 *        isSending (boolean).
 */
export default function ResponsePanel({ result, error, isSending }) {
  const [tab, setTab] = useState('body')

  // Every new response starts on the Body tab.
  useEffect(() => {
    if (result) setTab('body')
  }, [result])

  const size = useMemo(
    () => (result ? formatBytes(byteLength(result.body)) : ''),
    [result],
  )

  function onTabKeyDown(event) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const i = TABS.findIndex((t) => t.id === tab)
    const next = event.key === 'ArrowRight' ? (i + 1) % TABS.length : (i - 1 + TABS.length) % TABS.length
    setTab(TABS[next].id)
  }

  return (
    <section className="panel response-panel">
      <div className="panel__header">
        <h2 className="panel__title">Response</h2>
      </div>

      {isSending && (
        <div className="response-loading">
          <span className="spinner" aria-hidden="true" />
          Sending request…
        </div>
      )}

      {!isSending && error && (
        <div className="response-error" role="alert">
          <strong>{error.message}</strong>
          {error.detail && <p className="response-error__detail">{error.detail}</p>}
        </div>
      )}

      {!isSending && !error && result && (
        <>
          <div className="response-summary">
            <span className={`status-code status-code--${statusClass(result.statusCode)}`}>
              {result.statusCode} {statusText(result.statusCode)}
            </span>
            <span className="response-time">{result.durationMs} ms</span>
            {size && <span className="response-size">{size}</span>}
          </div>

          {Array.isArray(result.warnings) && result.warnings.length > 0 && (
            <ul className="response-warnings">
              {result.warnings.map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          )}

          <div className="response-tabs" role="tablist" aria-label="Response" onKeyDown={onTabKeyDown}>
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                id={`response-tab-${t.id}`}
                aria-selected={tab === t.id}
                aria-controls="response-tabpanel"
                tabIndex={tab === t.id ? 0 : -1}
                className={'response-tab' + (tab === t.id ? ' response-tab--active' : '')}
                onClick={() => setTab(t.id)}
              >
                {t.label}
              </button>
            ))}
          </div>

          <div
            id="response-tabpanel"
            role="tabpanel"
            aria-labelledby={`response-tab-${tab}`}
            className="response-tabpanel"
          >
            {tab === 'body' && <ResponseBody body={result.body} headers={result.headers} />}
            {tab === 'headers' && <ResponseHeaders headers={result.headers} />}
            {tab === 'raw' && (
              <ResponseRaw
                statusCode={result.statusCode}
                headers={result.headers}
                body={result.body}
              />
            )}
          </div>
        </>
      )}

      {!isSending && !error && !result && (
        <div className="response-panel__placeholder">
          Send a request and its status, timing, size, headers and body will appear here.
        </div>
      )}
    </section>
  )
}
