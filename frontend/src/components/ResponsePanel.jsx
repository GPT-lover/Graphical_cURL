import { useMemo } from 'react'
import { statusClass } from '../lib/http.js'

// Reason phrases for the common status codes. Anything not listed just shows the
// number (that's fine - the requirement is only that a 404 shows "404").
const REASON_PHRASE = {
  200: 'OK',
  201: 'Created',
  202: 'Accepted',
  204: 'No Content',
  301: 'Moved Permanently',
  302: 'Found',
  304: 'Not Modified',
  307: 'Temporary Redirect',
  308: 'Permanent Redirect',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  405: 'Method Not Allowed',
  409: 'Conflict',
  410: 'Gone',
  422: 'Unprocessable Entity',
  429: 'Too Many Requests',
  500: 'Internal Server Error',
  502: 'Bad Gateway',
  503: 'Service Unavailable',
  504: 'Gateway Timeout',
}

/**
 * Pretty-print the body if it parses as JSON, otherwise show it verbatim.
 * The original string in `result.body` is never modified - this only affects
 * what we render.
 */
function formatBody(body) {
  if (typeof body !== 'string' || body.trim() === '') {
    return { text: body ?? '', isJson: false }
  }
  try {
    return { text: JSON.stringify(JSON.parse(body), null, 2), isJson: true }
  } catch {
    return { text: body, isJson: false }
  }
}

/**
 * The response area. Shows one of four things depending on props:
 *   isSending      -> "Sending request…"
 *   error          -> a red error box (never crashes the app)
 *   result         -> status + timing + headers + body
 *   none of those  -> the idle placeholder
 *
 * Props:
 *   result    - SendResponseDto | null
 *   error     - { message, detail? } | null
 *   isSending - boolean
 */
export default function ResponsePanel({ result, error, isSending }) {
  const formatted = useMemo(
    () => (result ? formatBody(result.body) : null),
    [result],
  )

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
              {result.statusCode} {REASON_PHRASE[result.statusCode] ?? ''}
            </span>
            <span className="response-time">{result.durationMs} ms</span>
          </div>

          {Array.isArray(result.warnings) && result.warnings.length > 0 && (
            <ul className="response-warnings">
              {result.warnings.map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          )}

          <div className="response-section">
            <h3 className="response-section__title">Response Headers</h3>
            <div className="kv-list">
              {Object.entries(result.headers ?? {}).map(([name, value]) => (
                <div className="kv-list__row" key={name}>
                  <span className="kv-list__key">{name}</span>
                  <span className="kv-list__value">{value}</span>
                </div>
              ))}
              {Object.keys(result.headers ?? {}).length === 0 && (
                <p className="kv-list__empty">No response headers.</p>
              )}
            </div>
          </div>

          <div className="response-section">
            <h3 className="response-section__title">
              Response Body
              {formatted.isJson && <span className="tag">JSON</span>}
            </h3>
            <pre className="response-body">{formatted.text || '(empty body)'}</pre>
          </div>
        </>
      )}

      {!isSending && !error && !result && (
        <div className="response-panel__placeholder">
          Send a request and its status, timing, headers and body will appear here.
        </div>
      )}
    </section>
  )
}
