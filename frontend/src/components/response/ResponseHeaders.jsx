import { useCopyButton } from '../../hooks/useCopyButton.js'

const COPY_LABEL = { idle: 'Copy', copied: 'Copied', failed: 'Copy failed' }

/**
 * The Headers tab - every response header the backend reported.
 *
 * The backend returns headers as a flat { name: value } object; a header that
 * appeared more than once on the wire arrives here with its values comma-joined
 * into that one value string (standard HTTP folding). It is displayed as-is, so
 * repeated headers never crash the list.
 */
export default function ResponseHeaders({ headers }) {
  const { state: copyState, copy } = useCopyButton()
  const entries = headers ? Object.entries(headers) : []

  function copyAll() {
    copy(entries.map(([name, value]) => `${name}: ${value}`).join('\n'))
  }

  return (
    <div className="response-headers-view">
      <div className="response-body-toolbar">
        <span className="response-body-toolbar__left">
          {entries.length} header{entries.length === 1 ? '' : 's'}
        </span>
        {entries.length > 0 && (
          <button
            type="button"
            className="btn btn--tiny"
            onClick={copyAll}
            title="Copy all response headers"
          >
            {COPY_LABEL[copyState]}
          </button>
        )}
      </div>

      {entries.length === 0 ? (
        <p className="response-body-notice">No response headers.</p>
      ) : (
        <div className="kv-list">
          {entries.map(([name, value]) => (
            <div className="kv-list__row" key={name}>
              <span className="kv-list__key">{name}</span>
              <span className="kv-list__value">{value}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
