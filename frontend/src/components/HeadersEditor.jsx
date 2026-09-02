import KeyValueEditor from './KeyValueEditor.jsx'

/**
 * The request Headers section. A thin wrapper around the shared
 * {@link KeyValueEditor}; the props it accepts are unchanged from before.
 *
 * Props: headers, onChange(id, field, value), onAdd(), onRemove(id)
 */
export default function HeadersEditor({ headers, onChange, onAdd, onRemove }) {
  return (
    <KeyValueEditor
      title="Headers"
      rows={headers}
      onChange={onChange}
      onAdd={onAdd}
      onRemove={onRemove}
      keyPlaceholder="Key"
      valuePlaceholder="Value"
      addLabel="+ Add Header"
      keyAriaLabel="Header name"
      valueAriaLabel="Header value"
    />
  )
}
