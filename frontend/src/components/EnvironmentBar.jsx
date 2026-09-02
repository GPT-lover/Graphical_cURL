/**
 * Compact environment selector at the top of the request editor.
 *
 *   Environment: [ Development ▼ ]  [ Manage ]
 *
 * Changing the selection only affects the NEXT request sent - it does not touch
 * the request currently in the editor.
 *
 * Props:
 *   environments - [{ id, name }]
 *   activeId     - currently selected environment id (or null)
 *   loadError    - true if environments couldn't be loaded
 *   onChange(id) - select a different environment
 *   onManage()   - open the manage dialog
 */
export default function EnvironmentBar({ environments, activeId, loadError, onChange, onManage }) {
  return (
    <div className="env-bar">
      <label className="env-bar__label" htmlFor="env-select">
        Environment
      </label>
      <select
        id="env-select"
        className="input env-bar__select"
        value={activeId ?? ''}
        onChange={(event) => onChange(event.target.value ? Number(event.target.value) : null)}
        disabled={loadError && environments.length === 0}
      >
        {environments.length === 0 && (
          <option value="">{loadError ? 'unavailable' : 'no environments'}</option>
        )}
        {environments.map((env) => (
          <option key={env.id} value={env.id}>
            {env.name}
          </option>
        ))}
      </select>
      <button type="button" className="btn btn--tiny" onClick={onManage}>
        Manage
      </button>
    </div>
  )
}
