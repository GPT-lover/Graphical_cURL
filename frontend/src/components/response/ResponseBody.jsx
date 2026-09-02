import { useMemo } from 'react'
import { useCopyButton } from '../../hooks/useCopyButton.js'
import { classifyBody, looksLikeJson, mimeType, tryFormatJson } from '../../lib/response.js'

const COPY_LABEL = { idle: 'Copy', copied: 'Copied', failed: 'Copy failed' }

/**
 * The Body tab.
 *
 * - JSON (by Content-Type, or a text/unknown body that starts with { or [ and
 *   parses) is pretty-printed for display only; `body` in state is untouched.
 * - "JSON" Content-Type with invalid JSON -> a notice + the raw body, no crash.
 * - HTML / XML / plain text -> shown verbatim as text (never rendered).
 * - Obvious binary types -> a short notice instead of garbled text.
 * - Empty body -> "No response body".
 *
 * Copy copies the RAW response body, not the prettified version.
 */
export default function ResponseBody({ body, headers }) {
  const { state: copyState, copy } = useCopyButton()

  const view = useMemo(() => {
    const hasBody = typeof body === 'string' && body.length > 0
    if (!hasBody) {
      return { mode: 'empty' }
    }

    const kind = classifyBody(headers)

    if (kind === 'binary') {
      return { mode: 'binary', mime: mimeType(headers) }
    }

    if (kind === 'json') {
      const { ok, text } = tryFormatJson(body)
      return ok
        ? { mode: 'json', text, tag: 'JSON' }
        : { mode: 'invalid-json', text: body }
    }

    // text / html / xml / unknown: show as text, but sniff for JSON when the
    // Content-Type wasn't helpful.
    if ((kind === 'text' || kind === 'unknown') && looksLikeJson(body)) {
      const { ok, text } = tryFormatJson(body)
      if (ok) return { mode: 'json', text, tag: 'JSON' }
    }

    const tag = kind === 'html' ? 'HTML' : kind === 'xml' ? 'XML' : null
    return { mode: 'text', text: body, tag }
  }, [body, headers])

  return (
    <div className="response-body-view">
      <div className="response-body-toolbar">
        <span className="response-body-toolbar__left">
          {view.tag && <span className="tag">{view.tag}</span>}
          {view.mode === 'invalid-json' && (
            <span className="response-body-notice response-body-notice--warn">
              Invalid JSON — showing raw response
            </span>
          )}
        </span>
        {view.mode !== 'empty' && view.mode !== 'binary' && (
          <button
            type="button"
            className="btn btn--tiny"
            onClick={() => copy(body)}
            title="Copy the raw response body to the clipboard"
          >
            {COPY_LABEL[copyState]}
          </button>
        )}
      </div>

      {view.mode === 'empty' && (
        <p className="response-body-notice">No response body</p>
      )}

      {view.mode === 'binary' && (
        <p className="response-body-notice">
          Binary response{view.mime ? ` (${view.mime})` : ''} — not shown as text.
        </p>
      )}

      {(view.mode === 'json' || view.mode === 'text' || view.mode === 'invalid-json') && (
        <pre className="response-body">{view.text || '(empty)'}</pre>
      )}
    </div>
  )
}
