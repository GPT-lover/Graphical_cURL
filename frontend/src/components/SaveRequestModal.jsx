import { useEffect, useRef, useState } from 'react'

/**
 * "Save Request" dialog: pick a name and a collection, then create a new saved
 * request. Used for both the first Save and "Save As".
 *
 * Props:
 *   open                 - whether it's shown
 *   title                - "Save Request" / "Save as new request"
 *   collections          - [{ id, name }] for the picker
 *   initialName          - prefill for the name field
 *   initialCollectionId  - prefill for the collection picker
 *   onSubmit({name, collectionId}) - called on Save (name guaranteed non-blank)
 *   onClose()
 */
export default function SaveRequestModal({
  open,
  title = 'Save Request',
  collections,
  initialName = '',
  initialCollectionId = null,
  onSubmit,
  onClose,
}) {
  const [name, setName] = useState('')
  const [collectionId, setCollectionId] = useState('')
  const [error, setError] = useState(null)
  const nameRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    setName(initialName)
    setError(null)
    const first = collections[0]?.id
    setCollectionId(String(initialCollectionId ?? first ?? ''))
    const t = setTimeout(() => nameRef.current?.select(), 0)
    return () => clearTimeout(t)
  }, [open, initialName, initialCollectionId, collections])

  useEffect(() => {
    if (!open) return undefined
    function onKey(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null

  function handleSave() {
    const trimmed = name.trim()
    if (!trimmed) {
      setError('Give the request a name.')
      return
    }
    if (!collectionId) {
      setError('Choose a collection.')
      return
    }
    onSubmit({ name: trimmed, collectionId: Number(collectionId) })
  }

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        className="modal modal--narrow"
        role="dialog"
        aria-modal="true"
        aria-labelledby="save-request-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="save-request-title">{title}</h2>
          <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <label className="field">
          <span className="field__label">Name</span>
          <input
            ref={nameRef}
            className="input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSave()}
            placeholder="Rate Release"
            spellCheck={false}
          />
        </label>

        <label className="field">
          <span className="field__label">Collection</span>
          <select
            className="input"
            value={collectionId}
            onChange={(e) => setCollectionId(e.target.value)}
          >
            {collections.length === 0 && <option value="">No collections</option>}
            {collections.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        {error && <div className="modal__error" role="alert">{error}</div>}

        <div className="modal__actions">
          <button type="button" className="btn" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn btn--primary" onClick={handleSave}>
            Save
          </button>
        </div>
      </div>
    </div>
  )
}
