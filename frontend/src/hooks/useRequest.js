import { useCallback, useState } from 'react'
import { createInitialRequest, makeHeader } from '../lib/request.js'

/**
 * Holds the entire request-being-edited in one piece of React state and returns
 * small helper functions to change parts of it.
 *
 * Why one object instead of four useState calls (method, url, headers, body)?
 * - The sidebar (later phases) needs to load a whole saved request at once.
 * - "Copy as cURL" / "Send" need a single snapshot of everything.
 * One object keeps those operations trivial.
 *
 * Every helper uses the functional form of setState (prev => next) so rapid
 * edits (typing fast, clicking Add several times) can't clobber each other.
 * They're wrapped in useCallback so their identity is stable across renders.
 */
export function useRequest() {
  const [request, setRequest] = useState(createInitialRequest)

  const setMethod = useCallback((method) => {
    setRequest((prev) => ({ ...prev, method }))
  }, [])

  const setUrl = useCallback((url) => {
    setRequest((prev) => ({ ...prev, url }))
  }, [])

  const setBody = useCallback((body) => {
    setRequest((prev) => ({ ...prev, body }))
  }, [])

  const addHeader = useCallback(() => {
    setRequest((prev) => ({ ...prev, headers: [...prev.headers, makeHeader()] }))
  }, [])

  const removeHeader = useCallback((id) => {
    setRequest((prev) => {
      const headers = prev.headers.filter((header) => header.id !== id)
      // Keep at least one row so the editor never looks broken/empty.
      return { ...prev, headers: headers.length > 0 ? headers : [makeHeader()] }
    })
  }, [])

  const updateHeader = useCallback((id, field, value) => {
    setRequest((prev) => ({
      ...prev,
      headers: prev.headers.map((header) =>
        header.id === id ? { ...header, [field]: value } : header,
      ),
    }))
  }, [])

  /**
   * Replace the whole request - used by the sidebar in later phases to load a
   * history / saved entry into the editor. Accepts the plain
   * { method, url, headers: [{key, value}], body } shape.
   */
  const loadRequest = useCallback((incoming) => {
    setRequest({
      method: incoming.method ?? 'GET',
      url: incoming.url ?? '',
      headers:
        incoming.headers && incoming.headers.length > 0
          ? incoming.headers.map((header) => makeHeader(header.key, header.value))
          : [makeHeader()],
      body: incoming.body ?? '',
    })
  }, [])

  const resetRequest = useCallback(() => {
    setRequest(createInitialRequest())
  }, [])

  return {
    request,
    setMethod,
    setUrl,
    setBody,
    addHeader,
    removeHeader,
    updateHeader,
    loadRequest,
    resetRequest,
  }
}
