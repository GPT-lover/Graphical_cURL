import { useEffect, useRef, useState } from 'react'
import { importCurl } from '../api/client.js'

const PLACEHOLDER = `curl 'https://example.com/api/user' \\
  -H 'accept: application/json' \\
  -H 'authorization: Bearer abc123' \\
  -b 'session=xyz789; theme=dark' \\
  --data-raw '{"name":"William"}'`

/**
 * Modal dialog for pasting a Chrome "Copy as cURL" command.
 *
 * Props:
 *   open       - whether the dialog is shown
 *   onClose    - () => void
 *   onImported - (parsedRequest) => void   called with the backend's
 *                ParsedRequestDto; the parent loads it into the editor + closes
 *
 * The request is only ever parsed on the backend - it is never executed and it
 * is never sent as an HTTP request from here.
 */
export default function ImportCurlModal({ open, onClose, onImported }) {
  const [text, setText] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const textareaRef = useRef(null)

  // Reset + focus each time the dialog opens.
  useEffect(() => {
    if (open) {
      setText('')
      setError(null)
      setBusy(false)
      const id = setTimeout(() => textareaRef.current?.focus(), 0)
      return () => clearTimeout(id)
    }
  }, [open])

  // Close on Escape.
  useEffect(() => {
    if (!open) return
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  async function handleImport() {
    const value = text.trim()
    if (!value) {
      setError('Paste a cURL command first.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const parsed = await importCurl(value)
      onImported(parsed) // parent loads it into the editor and closes this dialog
    } catch (err) {
      // err is an ApiError from client.js. Show a useful, stack-trace-free message.
      setError(
        err.detail ? `${err.message}\n${err.detail}` : err.message,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    // mousedown (not click) so a text drag that ends on the overlay doesn't close it
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-curl-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="import-curl-title">Import cURL</h2>
          <button
            type="button"
            className="btn btn--icon"
            onClick={onClose}
            aria-label="Close"
          >
            &times;
          </button>
        </div>

        <p className="modal__hint">
          In Chrome DevTools open <strong>Network</strong>, right-click a request,
          choose <strong>Copy → Copy as cURL</strong>, then paste it here. Nothing
          is sent until you review it and press Send.
        </p>

        <textarea
          ref={textareaRef}
          className="modal__textarea"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={PLACEHOLDER}
          spellCheck={false}
          rows={10}
          aria-label="cURL command"
        />

        {error && (
          <div className="modal__error" role="alert">
            <strong>Unable to import cURL</strong>
            {'\n'}
            {error}
          </div>
        )}

        <div className="modal__actions">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={handleImport}
            disabled={busy}
          >
            {busy ? 'Importing…' : 'Import'}
          </button>
        </div>
      </div>
    </div>
  )
}
