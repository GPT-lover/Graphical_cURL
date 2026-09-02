import { useCallback, useEffect, useState } from 'react'
import { fetchCollections } from '../api/client.js'

/**
 * Loads the Saved Requests tree (collections + their request summaries).
 *
 *   collections - [{ id, name, requests: [{ id, name }] }], creation order
 *   loadError   - true if the last load failed (non-blocking; editor still works)
 *   refresh()   - reload; call after every create/rename/delete/save/update
 *
 * Only summaries are held here. The full method/url/headers/body of a saved
 * request is fetched on demand (getSavedRequest) when it's opened.
 */
export function useCollections() {
  const [collections, setCollections] = useState([])
  const [loadError, setLoadError] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchCollections()
      setCollections(Array.isArray(data) ? data : [])
      setLoadError(false)
    } catch {
      setLoadError(true)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return { collections, loadError, refresh }
}
