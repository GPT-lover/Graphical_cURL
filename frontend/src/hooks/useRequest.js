import { useCallback, useState } from 'react'
import { createInitialRequest, makeCookie, makeHeader } from '../lib/request.js'

/**
 * Holds the entire request-being-edited in one piece of React state and returns
 * small helper functions to change parts of it.
 *
 * Why one object instead of many useState calls (method, url, headers, cookies,
 * body)?
 * - "Import cURL" and the sidebar (later phases) need to load a whole request
 *   at once.
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

  // --- Headers --------------------------------------------------------

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

  // --- Cookies (same pattern as headers) ----------------------------

  const addCookie = useCallback(() => {
    setRequest((prev) => ({ ...prev, cookies: [...prev.cookies, makeCookie()] }))
  }, [])

  const removeCookie = useCallback((id) => {
    setRequest((prev) => {
      const cookies = prev.cookies.filter((cookie) => cookie.id !== id)
      return { ...prev, cookies: cookies.length > 0 ? cookies : [makeCookie()] }
    })
  }, [])

  const updateCookie = useCallback((id, field, value) => {
    setRequest((prev) => ({
      ...prev,
      cookies: prev.cookies.map((cookie) =>
        cookie.id === id ? { ...cookie, [field]: value } : cookie,
      ),
    }))
  }, [])

  /**
   * Remove every cookie from THIS request. Leaves one blank row so the section
   * stays usable. Does not touch headers, body, URL, the response, or any
   * browser cookies.
   */
  const clearCookies = useCallback(() => {
    setRequest((prev) => ({ ...prev, cookies: [makeCookie()] }))
  }, [])

  /**
   * Replace the whole request. Used by "Import cURL" now, and by the sidebar in
   * later phases. Accepts the plain shape
   * { method, url, headers:[{key,value}], cookies:[{key,value}], body }.
   * Missing lists fall back to a single blank row so the editor stays usable.
   */
  const loadRequest = useCallback((incoming) => {
    setRequest({
      method: incoming.method ?? 'GET',
      url: incoming.url ?? '',
      headers:
        incoming.headers && incoming.headers.length > 0
          ? incoming.headers.map((header) => makeHeader(header.key, header.value))
          : [makeHeader()],
      cookies:
        incoming.cookies && incoming.cookies.length > 0
          ? incoming.cookies.map((cookie) => makeCookie(cookie.key, cookie.value))
          : [makeCookie()],
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
    addCookie,
    removeCookie,
    updateCookie,
    clearCookies,
    loadRequest,
    resetRequest,
  }
}
