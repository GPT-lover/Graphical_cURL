import { HTTP_METHODS } from '../constants/httpMethods.js'

/**
 * The [GET v] dropdown. Controlled component: it shows `value` and reports every
 * change through `onChange(newMethod)`. It holds no state of its own.
 */
export default function MethodSelector({ value, onChange }) {
  return (
    <select
      className="method-selector"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label="HTTP method"
    >
      {HTTP_METHODS.map((method) => (
        <option key={method} value={method}>
          {method}
        </option>
      ))}
    </select>
  )
}
