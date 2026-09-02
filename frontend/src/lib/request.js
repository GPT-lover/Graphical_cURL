// Pure (non-React) helpers for the request object.
//
// The request the user is editing looks like this:
//
//   {
//     method: "GET",
//     url: "",
//     headers: [ { id: 1, key: "", value: "" } ],
//     cookies: [ { id: 2, key: "", value: "" } ],
//     body: ""
//   }
//
// The `id` on each row is a UI-only concern: React needs a stable key for each
// row so that adding/removing rows doesn't muddle which <input> is which (using
// the array index breaks focus and value tracking when rows move). It is
// stripped out by toRequestPayload() before the request leaves the editor.

let nextRowId = 1

/** Create one header row. */
export function makeHeader(key = '', value = '') {
  return { id: nextRowId++, key, value }
}

/** Create one cookie row (same shape as a header row). */
export function makeCookie(key = '', value = '') {
  return { id: nextRowId++, key, value }
}

/** The request the editor starts with: GET, empty everything, one blank row each. */
export function createInitialRequest() {
  return {
    method: 'GET',
    url: '',
    headers: [makeHeader()],
    cookies: [makeCookie()],
    body: '',
  }
}

/**
 * Turn the editor state into the plain shape the backend expects:
 * { method, url, headers: [{key,value}], cookies: [{key,value}], body }.
 * Drops the UI-only `id` and any row whose key is blank.
 */
export function toRequestPayload(request) {
  return {
    method: request.method,
    url: request.url.trim(),
    headers: (request.headers ?? [])
      .filter((header) => header.key.trim() !== '')
      .map((header) => ({ key: header.key.trim(), value: header.value })),
    cookies: (request.cookies ?? [])
      .filter((cookie) => cookie.key.trim() !== '')
      .map((cookie) => ({ key: cookie.key.trim(), value: cookie.value })),
    body: request.body,
  }
}
