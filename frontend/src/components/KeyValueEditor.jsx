/**
 * A reusable "list of key/value rows with add & remove" section. Used for both
 * the Headers editor and the Cookies editor so they stay visually and
 * behaviourally identical.
 *
 * Props:
 *   title            - section heading ("Headers" / "Cookies")
 *   rows             - array of { id, key, value }
 *   onChange         - (id, "key" | "value", newText) => void
 *   onAdd            - () => void   append a blank row
 *   onRemove         - (id) => void  delete that row
 *   keyPlaceholder / valuePlaceholder - input placeholders
 *   addLabel         - text for the add button
 *   keyAriaLabel / valueAriaLabel - accessible names for the inputs
 *   valueInputType   - 'text' (default) or 'password' to mask sensitive values
 *   headerActions    - optional React node shown at the right of the section
 *                      header (e.g. Cookies' Show/Hide + Clear buttons)
 *
 * Rows are keyed by their stable `id`, not the array index, so removing a middle
 * row doesn't make React reassign the remaining <input> elements.
 */
export default function KeyValueEditor({
  title,
  rows,
  onChange,
  onAdd,
  onRemove,
  keyPlaceholder = 'Key',
  valuePlaceholder = 'Value',
  addLabel = '+ Add',
  keyAriaLabel = 'Key',
  valueAriaLabel = 'Value',
  valueInputType = 'text',
  headerActions = null,
}) {
  const filledCount = rows.filter((row) => row.key.trim() !== '').length

  return (
    <section className="panel">
      <div className="panel__header">
        <h2 className="panel__title">{title}</h2>
        {filledCount > 0 && <span className="badge">{filledCount}</span>}
        {headerActions && <div className="panel__actions">{headerActions}</div>}
      </div>

      <div className="pairs">
        {rows.map((row) => (
          <div className="pair-row" key={row.id}>
            <input
              className="input"
              placeholder={keyPlaceholder}
              value={row.key}
              onChange={(event) => onChange(row.id, 'key', event.target.value)}
              spellCheck={false}
              autoComplete="off"
              aria-label={keyAriaLabel}
            />
            <input
              className="input"
              type={valueInputType}
              placeholder={valuePlaceholder}
              value={row.value}
              onChange={(event) => onChange(row.id, 'value', event.target.value)}
              spellCheck={false}
              autoComplete="off"
              aria-label={valueAriaLabel}
            />
            <button
              type="button"
              className="btn btn--icon"
              onClick={() => onRemove(row.id)}
              aria-label={`Remove ${keyAriaLabel}`}
              title="Remove"
            >
              &times;
            </button>
          </div>
        ))}
      </div>

      <button type="button" className="btn btn--ghost" onClick={onAdd}>
        {addLabel}
      </button>
    </section>
  )
}
