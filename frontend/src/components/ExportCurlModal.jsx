import { useEffect } from 'react'

/**
 * Preview of the generated cURL command. Opens after "Copy as cURL". The command
 * has already been copied to the clipboard (best effort); this lets the user
 * read it and copy it again by hand.
 *
 * Props:
 *   status  - from useCurlExport ('ready' shows the command, 'error' shows why)
 *   curl    - the generated command
 *   error   - error message (when status === 'error')
 *   copied  - whether the last clipboard write succeeded
 *   onCopy  - (text) => void   copy again
 *   onClose - () => void
 */
export default function ExportCurlModal({ status, curl, error, copied, onCopy, onClose }) {
  const open = status === 'ready' || status === 'error'

  useEffect(() => {
    if (!open) return undefined
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="export-curl-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="export-curl-title">
            {status === 'error' ? 'Could not generate cURL' : 'Generated cURL'}
          </h2>
          <button
            type="button"
            className="btn btn--icon"
            onClick={onClose}
            aria-label="Close"
          >
            &times;
          </button>
        </div>

        {status === 'error' ? (
          <div className="modal__error" role="alert">
            {error}
          </div>
        ) : (
          <>
            <p className="modal__hint">
              POSIX shell syntax — paste into Git Bash, WSL, macOS or Linux.
            </p>
            <pre className="export-curl__text">{curl}</pre>
            <div className="modal__actions">
              <span className="export-curl__status">
                {copied ? '✓ Copied to clipboard' : ''}
              </span>
              <button type="button" className="btn" onClick={() => onCopy(curl)}>
                Copy
              </button>
              <button type="button" className="btn btn--primary" onClick={onClose}>
                Close
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
