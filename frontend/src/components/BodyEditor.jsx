import MultipartFormEditor, { FilePathPicker } from './MultipartFormEditor.jsx'

const BODY_PLACEHOLDER = `{
  "name": "Alex",
  "age": 27
}`

const BODY_TYPES = [
  { value: 'raw', label: 'Raw' },
  { value: 'multipart', label: 'Multipart Form' },
  { value: 'binary', label: 'Binary File' },
]

/**
 * The request body editor. Three body types, chosen with the tabs at the top:
 *
 *   Raw            - the existing free-text body (unchanged behaviour). The
 *                     Content-Type is whatever the user set in Headers.
 *   Multipart Form  - a table of text/file fields, sent as a real
 *                     multipart/form-data request that curl itself builds
 *                     (boundary, encoding, file streaming) - see
 *                     MultipartFormEditor.
 *   Binary File     - a single local file, streamed as the raw request body
 *                     (curl's --data-binary @file). Pair it with a
 *                     Content-Type header (e.g. image/jpeg) in Headers.
 *
 * Props:
 *   value        - the raw body string (Raw mode) / file path (Binary mode)
 *   onChange     - (newValue) => void, for Raw/Binary
 *   bodyType     - 'raw' | 'multipart' | 'binary'
 *   onBodyTypeChange - (newType) => void
 *   multipart    - [{ id, type, name, value, contentType }]
 *   onAddMultipartField / onRemoveMultipartField / onUpdateMultipartField
 */
export default function BodyEditor({
  value,
  onChange,
  bodyType,
  onBodyTypeChange,
  multipart,
  onAddMultipartField,
  onRemoveMultipartField,
  onUpdateMultipartField,
}) {
  return (
    <section className="panel">
      <div className="panel__header">
        <h2 className="panel__title">Body</h2>
      </div>

      <div className="body-type-tabs" role="tablist" aria-label="Body type">
        {BODY_TYPES.map((type) => (
          <button
            key={type.value}
            type="button"
            role="tab"
            aria-selected={bodyType === type.value}
            className={`body-type-tab${bodyType === type.value ? ' body-type-tab--active' : ''}`}
            onClick={() => onBodyTypeChange(type.value)}
          >
            {type.label}
          </button>
        ))}
      </div>

      {bodyType === 'multipart' && (
        <>
          <MultipartFormEditor
            fields={multipart}
            onAdd={onAddMultipartField}
            onRemove={onRemoveMultipartField}
            onUpdate={onUpdateMultipartField}
          />
          <p className="panel__hint">
            Sent as a real multipart/form-data request - curl builds the boundary and
            streams file fields directly from disk. Do not add a Content-Type header
            yourself; it would conflict with curl&apos;s own boundary.
          </p>
        </>
      )}

      {bodyType === 'binary' && (
        <>
          <FilePathPicker
            value={value}
            onChange={onChange}
            pickerTitle="Select a file to send as the request body"
            ariaLabel="Request body file"
          />
          <p className="panel__hint">
            Sent as the raw request body (curl&apos;s <code>--data-binary</code>) - not
            wrapped in a form. Set a <code>Content-Type</code> header in Headers above
            (e.g. <code>image/jpeg</code>) to match the file.
          </p>
        </>
      )}

      {bodyType === 'raw' && (
        <>
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
            Sent as raw text. Not parsed or validated yet. Supports dynamic variables
            like <code>{'{{random(N)}}'}</code> and <code>{'{{increment(N)}}'}</code> -
            see <a className="hint-link" href="#/features">Features</a> for details.
          </p>
        </>
      )}
    </section>
  )
}
