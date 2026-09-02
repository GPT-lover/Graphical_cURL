import { useCallback, useEffect, useState } from 'react'
import { clearHistory, deleteHistoryEntry, fetchHistory } from '../api/client.js'

/**
 * Loads and manages the request-history list.
 *
 *   entries   - array of history DTOs, newest first
 *   loadError - true if the last load failed (history is a convenience; a failed
 *               load must never stop the editor from working)
 *   refresh() - reload from the backend (called on startup and after each Send)
 *   remove(id)- delete one entry (optimistic: removed from the list immediately)
 *   clear()   - delete every entry
 *
 * History never contains cookies or credential headers - the backend strips
 * those before storing - so nothing sensitive is held here.
 */
export function useHistory() {
  const [entries, setEntries] = useState([])
  const [loadError, setLoadError] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchHistory()
      setEntries(Array.isArray(data) ? data : [])
      setLoadError(false)
    } catch {
      setLoadError(true)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const remove = useCallback(async (id) => {
    setEntries((prev) => prev.filter((entry) => entry.id !== id))
    try {
      await deleteHistoryEntry(id)
    } catch {
      // Re-sync so the UI matches the backend if the delete didn't land.
      refresh()
    }
  }, [refresh])

  const clear = useCallback(async () => {
    setEntries([])
    try {
      await clearHistory()
    } catch {
      refresh()
    }
  }, [refresh])

  return { entries, loadError, refresh, remove, clear }
}
