import { useState } from 'react'
import KeyValueEditor from './KeyValueEditor.jsx'

/**
 * The request Cookies section. Same component as Headers, different labels, plus
 * two cookie-specific controls in the section header:
 *
 *   - Show / Hide : cookie values are treated as sensitive and masked
 *     (type="password") by default; this toggle reveals them for editing.
 *   - Clear Cookies : removes every cookie row from *this request only*. It does
 *     not touch headers, body, URL, the response, or any browser cookies.
 *
 * On send, the non-empty rows are combined into a single "Cookie: a=1; b=2"
 * header by the backend.
 *
 * Props: cookies, onChange(id, field, value), onAdd(), onRemove(id), onClear()
 */
export default function CookiesEditor({ cookies, onChange, onAdd, onRemove, onClear }) {
  const [revealed, setRevealed] = useState(false)

  const actions = (
    <>
      <button
        type="button"
        className="btn btn--tiny"
        onClick={() => setRevealed((v) => !v)}
        aria-pressed={revealed}
        title={revealed ? 'Hide cookie values' : 'Show cookie values'}
      >
        {revealed ? 'Hide' : 'Show'}
      </button>
      <button
        type="button"
        className="btn btn--tiny"
        onClick={onClear}
        title="Remove all cookies from this request"
      >
        Clear Cookies
      </button>
    </>
  )

  return (
    <KeyValueEditor
      title="Cookies"
      rows={cookies}
      onChange={onChange}
      onAdd={onAdd}
      onRemove={onRemove}
      keyPlaceholder="Name"
      valuePlaceholder="Value"
      addLabel="+ Add Cookie"
      keyAriaLabel="Cookie name"
      valueAriaLabel="Cookie value"
      valueInputType={revealed ? 'text' : 'password'}
      headerActions={actions}
    />
  )
}
