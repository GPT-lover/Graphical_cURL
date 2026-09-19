import { useEffect, useState } from 'react'

function canPickFile() {
  return typeof window !== 'undefined' && window.curlGui && typeof window.curlGui.pickFile === 'function'
}

function canCheckPath() {
  return typeof window !== 'undefined' && window.curlGui && typeof window.curlGui.pathExists === 'function'
}

/**
 * A single local-file path field: Browse / typed path / missing-file warning /
 * clear. Shared by multipart "File" rows and the "Binary File" body editor so
 * both get the same picker + missing-file behaviour for free.
 *
 * A file's contents are never read here - only its path (chosen via the
 * native picker, or typed/pasted) is kept in the request. `window.curlGui` is
 * only present in the Electron shell; in a plain browser the path can still be
 * typed by hand, just without Browse or the missing-file check.
 *
 * Props: value, onChange(newPath), pickerTitle, placeholder, ariaLabel.
 */
export function FilePathPicker({ value, onChange, pickerTitle, placeholder, ariaLabel = 'File path' }) {
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    let cancelled = false
    if (!value || !canCheckPath()) {
      setMissing(false)
      return undefined
    }
    window.curlGui.pathExists(value).then((exists) => {
      if (!cancelled) setMissing(!exists)
    })
    return () => {
      cancelled = true
    }
  }, [value])

  async function handleBrowse() {
    const picked = await window.curlGui.pickFile({ title: pickerTitle || 'Select a file' })
    if (picked) onChange(picked)
  }

  return (
    <div className="file-picker">
      {canPickFile() && (
        <button type="button" className="btn btn--tiny" onClick={handleBrowse}>
          Browse
        </button>
      )}
      <input
        className="input file-picker__path"
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder ?? (canPickFile() ? 'No file selected' : 'C:\\path\\to\\file.jpg')}
        title={value || undefined}
        spellCheck={false}
        autoComplete="off"
        aria-label={ariaLabel}
      />
      {value && missing && (
        <span className="file-picker__missing" title={`Not found: ${value}`}>
          ⚠ Not found
        </span>
      )}
      {value && (
        <button
          type="button"
          className="btn btn--icon"
          onClick={() => onChange('')}
          aria-label="Clear selected file"
          title="Clear"
        >
          &times;
        </button>
      )}
    </div>
  )
}

/**
 * "Multipart Form" body editor: a table of name/type/value fields, each either
 * a plain text value or a local file (browsed or typed as a path). Mirrors the
 * shape a real `curl -F` command sends - curl builds the actual multipart
 * body, boundary and Content-Type itself at send time; nothing here encodes
 * bytes or touches a boundary string.
 *
 * Props:
 *   fields   - [{ id, type: 'text'|'file', name, value, contentType }]
 *   onAdd    - (type) => void   append a blank row of that type
 *   onRemove - (id) => void
 *   onUpdate - (id, partialChanges) => void
 */
export default function MultipartFormEditor({ fields, onAdd, onRemove, onUpdate }) {
  return (
    <div className="multipart-editor">
      <div className="multipart-rows">
        {fields.map((field) => (
          <div className="multipart-row" key={field.id}>
            <select
              className="input multipart-row__type"
              value={field.type}
              onChange={(event) => onUpdate(field.id, { type: event.target.value })}
              aria-label="Field type"
            >
              <option value="text">Text</option>
              <option value="file">File</option>
            </select>
            <input
              className="input"
              placeholder="Name"
              value={field.name}
              onChange={(event) => onUpdate(field.id, { name: event.target.value })}
              spellCheck={false}
              autoComplete="off"
              aria-label="Field name"
            />
            {field.type === 'file' ? (
              <FilePathPicker
                value={field.value}
                onChange={(value) => onUpdate(field.id, { value })}
                pickerTitle="Select a file to upload"
              />
            ) : (
              <input
                className="input"
                placeholder="Value"
                value={field.value}
                onChange={(event) => onUpdate(field.id, { value: event.target.value })}
                spellCheck={false}
                autoComplete="off"
                aria-label="Field value"
              />
            )}
            {field.type === 'file' && (
              <input
                className="input multipart-row__mime"
                placeholder="MIME type (optional)"
                value={field.contentType}
                onChange={(event) => onUpdate(field.id, { contentType: event.target.value })}
                spellCheck={false}
                autoComplete="off"
                aria-label="Content type"
              />
            )}
            <button
              type="button"
              className="btn btn--icon"
              onClick={() => onRemove(field.id)}
              aria-label="Remove field"
              title="Remove"
            >
              &times;
            </button>
          </div>
        ))}
      </div>

      <div className="multipart-editor__actions">
        <button type="button" className="btn btn--ghost" onClick={() => onAdd('text')}>
          + Add Text Field
        </button>
        <button type="button" className="btn btn--ghost" onClick={() => onAdd('file')}>
          + Add File Field
        </button>
      </div>
    </div>
  )
}
