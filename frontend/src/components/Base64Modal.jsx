import { useEffect, useMemo, useRef, useState } from 'react'

/** UTF-8 safe text -> Base64 (handles multi-byte characters, unlike plain btoa). */
function encodeBase64(text) {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

/** UTF-8 safe Base64 -> text; throws if the input isn't valid Base64 (or isn't valid UTF-8 once decoded). */
function decodeBase64(text) {
  const binary = atob(text.trim())
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
}

/**
 * Standalone Base64 encode/decode scratchpad. Opened from the "Base64" button
 * on the Body panel's header - entirely separate from the request body: text
 * is typed/pasted here, never read from or written back to the body editor.
 *
 * Props:
 *   open    - whether the dialog is shown
 *   onClose - () => void
 */
export default function Base64Modal({ open, onClose }) {
  const [mode, setMode] = useState('encode')
  const [input, setInput] = useState('')
  const [copied, setCopied] = useState(false)
  const textareaRef = useRef(null)

  // Reset + focus each time the dialog opens.
  useEffect(() => {
    if (!open) return undefined
    setMode('encode')
    setInput('')
    setCopied(false)
    const id = setTimeout(() => textareaRef.current?.focus(), 0)
    return () => clearTimeout(id)
  }, [open])

  // Close on Escape.
  useEffect(() => {
    if (!open) return undefined
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Auto-hide the "copied" confirmation.
  useEffect(() => {
    if (!copied) return undefined
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const { output, error } = useMemo(() => {
    if (!input) return { output: '', error: null }
    try {
      const result = mode === 'encode' ? encodeBase64(input) : decodeBase64(input)
      return { output: result, error: null }
    } catch {
      return {
        output: '',
        error: mode === 'encode' ? 'Could not encode this text.' : 'That text is not valid Base64.',
      }
    }
  }, [input, mode])

  if (!open) return null

  async function handleCopy() {
    if (!output) return
    try {
      await navigator.clipboard.writeText(output)
      setCopied(true)
    } catch {
      // Clipboard blocked (permissions, insecure context, old browser) - the
      // result is still visible to select and copy by hand.
      setCopied(false)
    }
  }

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="base64-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="base64-title">Base64 Encode / Decode</h2>
          <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <p className="modal__hint">
          A scratchpad, separate from the request body - nothing here is read from or
          written back to Body.
        </p>

        <fieldset className="field base64-modal__mode">
          <span className="field__label">Mode</span>
          <div className="base64-modal__mode-options">
            <label>
              <input
                type="radio"
                name="base64-mode"
                checked={mode === 'encode'}
                onChange={() => setMode('encode')}
              />{' '}
              Encode (text → Base64)
            </label>
            <label>
              <input
                type="radio"
                name="base64-mode"
                checked={mode === 'decode'}
                onChange={() => setMode('decode')}
              />{' '}
              Decode (Base64 → text)
            </label>
          </div>
        </fieldset>

        <label className="field">
          <span className="field__label">{mode === 'encode' ? 'Text' : 'Base64'}</span>
          <textarea
            ref={textareaRef}
            className="modal__textarea base64-modal__textarea"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={mode === 'encode' ? 'Paste or type text to encode…' : 'Paste Base64 to decode…'}
            spellCheck={false}
            rows={6}
            aria-label={mode === 'encode' ? 'Text to encode' : 'Base64 to decode'}
          />
        </label>

        <label className="field">
          <span className="field__label">{mode === 'encode' ? 'Base64' : 'Text'}</span>
          <textarea
            className="modal__textarea base64-modal__textarea"
            value={output}
            readOnly
            placeholder="Result appears here"
            spellCheck={false}
            rows={6}
            aria-label={mode === 'encode' ? 'Base64 result' : 'Decoded text result'}
          />
        </label>

        {error && (
          <div className="modal__error" role="alert">
            {error}
          </div>
        )}

        <div className="modal__actions">
          <span className="export-curl__status">{copied ? '✓ Copied to clipboard' : ''}</span>
          <button type="button" className="btn" onClick={handleCopy} disabled={!output}>
            Copy Result
          </button>
          <button type="button" className="btn btn--primary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
