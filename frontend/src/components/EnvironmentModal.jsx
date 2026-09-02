import { useCallback, useEffect, useRef, useState } from 'react'
import {
  createEnvironment,
  createVariable,
  deleteEnvironment,
  deleteVariable,
  fetchEnvironment,
  renameEnvironment,
  updateVariable,
} from '../api/client.js'
import { isSensitiveName } from '../lib/sensitive.js'

let nextLocalId = 1

/**
 * "Manage Environments" dialog: create / rename / delete environments and edit
 * their variables. Variable rows are edited locally and applied on Save (diffed
 * against what was loaded). Sensitive-looking keys mask their value by default.
 *
 * Props:
 *   open, onClose
 *   environments  - [{ id, name }] (from useEnvironments)
 *   activeId      - the app's active environment id (used as the initial selection)
 *   onChanged()   - called after any environment/variable change so the app can
 *                   refresh its environment list
 */
export default function EnvironmentModal({ open, onClose, environments, activeId, onChanged }) {
  const [selectedEnvId, setSelectedEnvId] = useState(null)
  const [rows, setRows] = useState([]) // { localId, id?, key, value, originalKey, originalValue, revealed }
  const [deletedIds, setDeletedIds] = useState(() => new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [savedNote, setSavedNote] = useState(false)
  const savedTimer = useRef(null)

  const loadVariables = useCallback(async (envId) => {
    setError(null)
    setDeletedIds(new Set())
    if (envId == null) {
      setRows([])
      return
    }
    try {
      const dto = await fetchEnvironment(envId)
      setRows(
        (dto.variables ?? []).map((v) => ({
          localId: nextLocalId++,
          id: v.id,
          key: v.key,
          value: v.value,
          originalKey: v.key,
          originalValue: v.value,
          revealed: false,
        })),
      )
    } catch (err) {
      setError(err.message)
      setRows([])
    }
  }, [])

  // On open: pick an environment and load it.
  useEffect(() => {
    if (!open) return
    const initial =
      activeId != null && environments.some((e) => e.id === activeId)
        ? activeId
        : (environments[0]?.id ?? null)
    setSelectedEnvId(initial)
    loadVariables(initial)
  }, [open, activeId, environments, loadVariables])

  useEffect(() => {
    if (!open) return undefined
    function onKey(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  useEffect(() => () => clearTimeout(savedTimer.current), [])

  if (!open) return null

  function selectEnv(id) {
    const envId = id ? Number(id) : null
    setSelectedEnvId(envId)
    loadVariables(envId)
  }

  async function handleNewEnvironment() {
    const name = window.prompt('Environment name:')
    if (!name || !name.trim()) return
    try {
      const created = await createEnvironment(name.trim())
      await onChanged()
      setSelectedEnvId(created.id)
      loadVariables(created.id)
    } catch (err) {
      window.alert(err.message)
    }
  }

  async function handleRenameEnvironment() {
    const current = environments.find((e) => e.id === selectedEnvId)
    if (!current) return
    const name = window.prompt('Environment name:', current.name)
    if (!name || !name.trim() || name.trim() === current.name) return
    try {
      await renameEnvironment(selectedEnvId, name.trim())
      await onChanged()
    } catch (err) {
      window.alert(err.message)
    }
  }

  async function handleDeleteEnvironment() {
    const current = environments.find((e) => e.id === selectedEnvId)
    if (!current) return
    if (!window.confirm(`Delete environment "${current.name}" and all of its variables?`)) return
    try {
      await deleteEnvironment(selectedEnvId)
      await onChanged()
      const remaining = environments.filter((e) => e.id !== selectedEnvId)
      const next = remaining[0]?.id ?? null
      setSelectedEnvId(next)
      loadVariables(next)
    } catch (err) {
      window.alert(err.message) // e.g. "Cannot delete the only environment"
    }
  }

  function addRow() {
    setRows((prev) => [
      ...prev,
      { localId: nextLocalId++, key: '', value: '', originalKey: null, originalValue: null, revealed: false },
    ])
  }

  function updateRow(localId, patch) {
    setRows((prev) => prev.map((r) => (r.localId === localId ? { ...r, ...patch } : r)))
  }

  function removeRow(localId) {
    setRows((prev) => {
      const row = prev.find((r) => r.localId === localId)
      if (row?.id != null) {
        setDeletedIds((d) => new Set(d).add(row.id))
      }
      return prev.filter((r) => r.localId !== localId)
    })
  }

  async function handleSave() {
    if (selectedEnvId == null) return
    setBusy(true)
    setError(null)
    try {
      for (const id of deletedIds) {
        await deleteVariable(selectedEnvId, id)
      }
      for (const row of rows) {
        const key = row.key.trim()
        if (!key) continue // skip blank rows
        if (row.id == null) {
          await createVariable(selectedEnvId, { key, value: row.value })
        } else if (key !== row.originalKey || row.value !== row.originalValue) {
          await updateVariable(selectedEnvId, row.id, { key, value: row.value })
        }
      }
      await loadVariables(selectedEnvId) // show the server's truth
      onChanged()
      setSavedNote(true)
      clearTimeout(savedTimer.current)
      savedTimer.current = setTimeout(() => setSavedNote(false), 1600)
    } catch (err) {
      setError(err.message)
      loadVariables(selectedEnvId) // partial writes may have landed; resync
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-overlay" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="env-modal-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="env-modal-title">Manage Environments</h2>
          <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>

        <div className="env-modal__pick">
          <select
            className="input"
            value={selectedEnvId ?? ''}
            onChange={(e) => selectEnv(e.target.value)}
          >
            {environments.length === 0 && <option value="">no environments</option>}
            {environments.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </select>
          <button type="button" className="btn btn--tiny" onClick={handleRenameEnvironment} disabled={selectedEnvId == null}>
            Rename
          </button>
          <button type="button" className="btn btn--tiny" onClick={handleDeleteEnvironment} disabled={selectedEnvId == null}>
            Delete
          </button>
          <button type="button" className="btn btn--tiny" onClick={handleNewEnvironment}>
            + New Environment
          </button>
        </div>

        {selectedEnvId != null && (
          <>
            <div className="env-vars__head">
              <span>KEY</span>
              <span>VALUE</span>
              <span />
            </div>

            <div className="env-vars">
              {rows.length === 0 && <p className="sidebar__empty">No variables yet.</p>}
              {rows.map((row) => {
                const masked = isSensitiveName(row.key) && !row.revealed
                return (
                  <div className="env-var-row" key={row.localId}>
                    <input
                      className="input"
                      value={row.key}
                      placeholder="BASE_URL"
                      spellCheck={false}
                      autoComplete="off"
                      aria-label="Variable key"
                      onChange={(e) => updateRow(row.localId, { key: e.target.value })}
                    />
                    <div className="env-var-row__value">
                      <input
                        className="input"
                        type={masked ? 'password' : 'text'}
                        value={row.value}
                        placeholder="https://api.example.com"
                        spellCheck={false}
                        autoComplete="off"
                        aria-label="Variable value"
                        onChange={(e) => updateRow(row.localId, { value: e.target.value })}
                      />
                      {isSensitiveName(row.key) && (
                        <button
                          type="button"
                          className="btn btn--tiny"
                          onClick={() => updateRow(row.localId, { revealed: !row.revealed })}
                          aria-pressed={row.revealed}
                          title={row.revealed ? 'Hide value' : 'Show value'}
                        >
                          {row.revealed ? 'Hide' : 'Show'}
                        </button>
                      )}
                    </div>
                    <button
                      type="button"
                      className="btn btn--icon"
                      onClick={() => removeRow(row.localId)}
                      aria-label="Delete variable"
                      title="Delete variable"
                    >
                      &times;
                    </button>
                  </div>
                )
              })}
            </div>

            <button type="button" className="btn btn--ghost" onClick={addRow}>
              + Add Variable
            </button>
          </>
        )}

        {error && <div className="modal__error" role="alert">{error}</div>}

        <div className="modal__actions">
          <span className="export-curl__status">{savedNote ? '✓ Saved' : ''}</span>
          <button type="button" className="btn" onClick={onClose}>
            Close
          </button>
          <button
            type="button"
            className="btn btn--primary"
            onClick={handleSave}
            disabled={busy || selectedEnvId == null}
          >
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}
