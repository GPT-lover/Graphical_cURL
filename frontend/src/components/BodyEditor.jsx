const BODY_PLACEHOLDER = `{
  "name": "William",
  "age": 19
}`

/**
 * Plain multiline text area for the request body. The value is stored as a
 * string exactly as typed - no JSON parsing or validation in this phase.
 */
export default function BodyEditor({ value, onChange }) {
  return (
    <section className="panel">
      <div className="panel__header">
        <h2 className="panel__title">Body</h2>
      </div>

      <textarea
        className="body-editor"
        placeholder={BODY_PLACEHOLDER}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
        rows={12}
        aria-label="Request body"
      />

      <p className="panel__hint">
        Sent as raw text. Not parsed or validated yet.
      </p>
    </section>
  )
}
