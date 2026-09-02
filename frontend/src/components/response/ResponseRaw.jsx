import { useMemo } from 'react'
import { useCopyButton } from '../../hooks/useCopyButton.js'
import { rawText } from '../../lib/response.js'

const COPY_LABEL = { idle: 'Copy', copied: 'Copied', failed: 'Copy failed' }

/**
 * The Raw tab - a readable reconstruction of the response from the status,
 * headers and body. It is NOT the exact bytes that came off the socket (the
 * backend hands us a parsed representation), so it's labelled as an
 * approximation.
 */
export default function ResponseRaw({ statusCode, headers, body }) {
  const { state: copyState, copy } = useCopyButton()
  const text = useMemo(
    () => rawText(statusCode, headers, body),
    [statusCode, headers, body],
  )

  return (
    <div className="response-raw-view">
      <div className="response-body-toolbar">
        <span className="response-body-toolbar__left response-body-notice">
          Approximation — rebuilt from status, headers and body (not the wire bytes).
        </span>
        <button
          type="button"
          className="btn btn--tiny"
          onClick={() => copy(text)}
          title="Copy the raw view"
        >
          {COPY_LABEL[copyState]}
        </button>
      </div>
      <pre className="response-body">{text}</pre>
    </div>
  )
}
