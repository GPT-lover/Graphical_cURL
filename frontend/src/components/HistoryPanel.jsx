import { formatDuration, statusClass } from '../lib/http.js'
import { shortUrl, timeAgo } from '../lib/history.js'

/**
 * The History section of the sidebar.
 *
 * Props:
 *   entries   - history DTOs, newest first
 *   loadError - true if history couldn't be loaded (shown, but non-blocking)
 *   onRestore - (entry) => void   load method/url/headers/body into the editor
 *   onDelete  - (id) => void      remove one entry
 *   onClear   - () => void        remove all entries (asks for confirmation)
 *
 * Compact by design: method, shortened URL, status + duration, relative time.
 * The full URL lives in the entry and is restored in full on click.
 */
export default function HistoryPanel({ entries, loadError, onRestore, onDelete, onClear }) {
  function handleClear() {
    if (window.confirm('Clear all request history? This cannot be undone.')) {
      onClear()
    }
  }

  return (
    <div className="sidebar__section">
      <div className="history__header">
        <h2 className="sidebar__title">History</h2>
        {entries.length > 0 && (
          <button type="button" className="btn btn--tiny" onClick={handleClear}>
            Clear History
          </button>
        )}
      </div>

      {loadError && <p className="sidebar__empty">History could not be loaded.</p>}

      {!loadError && entries.length === 0 && (
        <p className="sidebar__empty">Sent requests appear here.</p>
      )}

      <ul className="history">
        {entries.map((entry) => (
          <li className="history-item" key={entry.id}>
            <button
              type="button"
              className="history-item__open"
              onClick={() => onRestore(entry)}
              title={entry.url}
            >
              <span className="history-item__top">
                <span className={`method method--${entry.method.toLowerCase()}`}>
                  {entry.method}
                </span>
                <span className={`status-code status-code--${statusClass(entry.statusCode)}`}>
                  {entry.statusCode}
                </span>
              </span>
              <span className="history-item__url">{shortUrl(entry.url)}</span>
              <span className="history-item__meta">
                {formatDuration(entry.durationMs)} · {timeAgo(entry.createdAt)}
              </span>
            </button>
            <button
              type="button"
              className="btn btn--icon history-item__delete"
              onClick={() => onDelete(entry.id)}
              aria-label="Delete history entry"
              title="Delete"
            >
              &times;
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
