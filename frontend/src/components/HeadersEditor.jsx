/**
 * The dynamic list of header rows.
 *
 * Props (all from the useRequest hook, passed down by RequestEditor):
 *   headers   - array of { id, key, value }
 *   onChange  - (id, "key" | "value", newText) => void
 *   onAdd     - () => void   append a blank row
 *   onRemove  - (id) => void  delete that row
 *
 * Each row is keyed by its stable `id`, not the array index, so removing a
 * middle row doesn't make React reassign the remaining <input> elements.
 */
export default function HeadersEditor({ headers, onChange, onAdd, onRemove }) {
  const filledCount = headers.filter((header) => header.key.trim() !== '').length

  return (
    <section className="panel">
      <div className="panel__header">
        <h2 className="panel__title">Headers</h2>
        {filledCount > 0 && <span className="badge">{filledCount}</span>}
      </div>

      <div className="headers">
        {headers.map((header) => (
          <div className="header-row" key={header.id}>
            <input
              className="input"
              placeholder="Key"
              value={header.key}
              onChange={(event) => onChange(header.id, 'key', event.target.value)}
              spellCheck={false}
              autoComplete="off"
              aria-label="Header name"
            />
            <input
              className="input"
              placeholder="Value"
              value={header.value}
              onChange={(event) => onChange(header.id, 'value', event.target.value)}
              spellCheck={false}
              autoComplete="off"
              aria-label="Header value"
            />
            <button
              type="button"
              className="btn btn--icon"
              onClick={() => onRemove(header.id)}
              aria-label="Remove header"
              title="Remove header"
            >
              &times;
            </button>
          </div>
        ))}
      </div>

      <button type="button" className="btn btn--ghost" onClick={onAdd}>
        + Add Header
      </button>
    </section>
  )
}
