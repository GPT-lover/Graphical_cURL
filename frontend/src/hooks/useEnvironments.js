import { useCallback, useEffect, useState } from 'react'
import { fetchEnvironments } from '../api/client.js'

const STORAGE_KEY = 'curlgui.activeEnvironmentId'

function readStoredId() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const n = raw == null ? NaN : Number(raw)
    return Number.isFinite(n) ? n : null
  } catch {
    return null
  }
}

function writeStoredId(id) {
  try {
    if (id == null) window.localStorage.removeItem(STORAGE_KEY)
    else window.localStorage.setItem(STORAGE_KEY, String(id))
  } catch {
    // private mode / storage disabled - fine, it just won't persist across reloads
  }
}

/**
 * Loads the list of environments and tracks which one is active.
 *
 *   environments        - [{ id, name }], creation order
 *   activeEnvironmentId  - the id whose variables get substituted on the next Send
 *   setActiveEnvironmentId(id)
 *   loadError            - true if the list couldn't be loaded (non-blocking)
 *   refresh()            - reload the list (call after create/rename/delete)
 *
 * Only the id is kept in localStorage - never variable values.
 * On load: use the stored id if it still exists, else "Default", else the first.
 */
export function useEnvironments() {
  const [environments, setEnvironments] = useState([])
  const [activeEnvironmentId, setActiveId] = useState(null)
  const [loadError, setLoadError] = useState(false)

  const setActiveEnvironmentId = useCallback((id) => {
    const numeric = id == null ? null : Number(id)
    setActiveId(numeric)
    writeStoredId(numeric)
  }, [])

  const refresh = useCallback(async () => {
    try {
      const list = await fetchEnvironments()
      const envs = Array.isArray(list) ? list : []
      setEnvironments(envs)
      setLoadError(false)

      // Reconcile the active id against the fresh list.
      setActiveId((current) => {
        const stored = current ?? readStoredId()
        if (stored != null && envs.some((e) => e.id === stored)) return stored
        const fallback =
          envs.find((e) => e.name === 'Default')?.id ?? envs[0]?.id ?? null
        writeStoredId(fallback)
        return fallback
      })
    } catch {
      setLoadError(true)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return {
    environments,
    activeEnvironmentId,
    setActiveEnvironmentId,
    loadError,
    refresh,
  }
}
