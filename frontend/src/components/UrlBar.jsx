/**
 * The URL text field. Controlled by the parent (value + onChange).
 * Pressing Enter triggers onSend so you can fire a request without reaching for
 * the mouse - a small nicety common to API tools.
 */
export default function UrlBar({ value, onChange, onSend }) {
  return (
    <input
      type="text"
      className="url-bar"
      placeholder="https://api.example.com/users"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          onSend()
        }
      }}
      aria-label="Request URL"
      spellCheck={false}
      autoComplete="off"
      autoCapitalize="off"
      autoCorrect="off"
    />
  )
}
