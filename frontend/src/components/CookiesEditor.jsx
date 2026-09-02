import KeyValueEditor from './KeyValueEditor.jsx'

/**
 * The request Cookies section. Same component as Headers, different labels.
 * On send, these rows are combined into a single "Cookie: a=1; b=2" header by
 * the backend.
 *
 * Props: cookies, onChange(id, field, value), onAdd(), onRemove(id)
 */
export default function CookiesEditor({ cookies, onChange, onAdd, onRemove }) {
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
    />
  )
}
