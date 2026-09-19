import { useCallback, useRef, useState } from 'react'
import {
  createInitialRequest,
  makeCookie,
  makeHeader,
  makeMultipartField,
  reconcileBodyEdit,
} from '../lib/request.js'

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

  // The Raw body exactly as it was last loaded (cURL import / saved request /
  // history), before any edit in this editor session. setBody compares
  // against it so a <textarea>'s silent CRLF-to-LF rewrite (see
  // reconcileBodyEdit in lib/request.js) can't corrupt a byte-exact body the
  // user never actually changed. Not request state: it must not itself be
  // rewritten by the normalisation it exists to guard against.
  const pristineBodyRef = useRef(null)

  const setMethod = useCallback((method) => {
    setRequest((prev) => ({ ...prev, method }))
  }, [])

  const setUrl = useCallback((url) => {
    setRequest((prev) => ({ ...prev, url }))
  }, [])

  const setBody = useCallback((body) => {
    setRequest((prev) => ({ ...prev, body: reconcileBodyEdit(pristineBodyRef.current, body) }))
  }, [])

  const setBodyType = useCallback((bodyType) => {
    setRequest((prev) => ({
      ...prev,
      bodyType,
      // Starting a multipart body with no fields yet is a confusing empty
      // state - seed one blank text row, same as Headers/Cookies do.
      multipart:
        bodyType === 'multipart' && (prev.multipart ?? []).length === 0
          ? [makeMultipartField()]
          : prev.multipart,
    }))
  }, [])

  // --- Multipart fields (Body Type: Multipart Form) --------------------

  const addMultipartField = useCallback((type = 'text') => {
    setRequest((prev) => ({
      ...prev,
      multipart: [...(prev.multipart ?? []), makeMultipartField(type)],
    }))
  }, [])

  const removeMultipartField = useCallback((id) => {
    setRequest((prev) => ({
      ...prev,
      multipart: (prev.multipart ?? []).filter((field) => field.id !== id),
    }))
  }, [])

  const updateMultipartField = useCallback((id, changes) => {
    setRequest((prev) => ({
      ...prev,
      multipart: (prev.multipart ?? []).map((field) =>
        field.id === id ? { ...field, ...changes } : field,
      ),
    }))
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
    const bodyType = incoming.bodyType ?? 'raw'
    // New byte-exact baseline for this freshly-loaded body (see setBody).
    pristineBodyRef.current = incoming.body ?? ''
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
      bodyType,
      multipart:
        incoming.multipart && incoming.multipart.length > 0
          ? incoming.multipart.map((field) =>
              makeMultipartField(field.type, field.name, field.value, field.contentType ?? ''),
            )
          : bodyType === 'multipart'
            ? [makeMultipartField()]
            : [],
      // cURL import supplies curlOptions (--compressed, --http1.1, -L, -k,
      // timeouts, --proxy); History / Saved Requests do not, so it resets to
      // null there. Carried opaquely; no editor UI.
      curlOptions: incoming.curlOptions ?? null,
    })
  }, [])

  const resetRequest = useCallback(() => {
    pristineBodyRef.current = null
    setRequest(createInitialRequest())
  }, [])

  return {
    request,
    setMethod,
    setUrl,
    setBody,
    setBodyType,
    addMultipartField,
    removeMultipartField,
    updateMultipartField,
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
