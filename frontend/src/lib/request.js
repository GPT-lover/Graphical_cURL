// Pure (non-React) helpers for the request object.
//
// The request the user is editing looks like this:
//
//   {
//     method: "GET",
//     url: "",
//     headers: [ { id: 1, key: "", value: "" } ],
//     body: ""
//   }
//
// The `id` on each header is a UI-only concern: React needs a stable key for
// each row so that adding/removing rows doesn't muddle which <input> is which
// (using the array index breaks focus and value tracking when rows move).
// It is stripped out by toRequestPayload() before the request leaves the editor.

let nextHeaderId = 1

/** Create one header row. */
export function makeHeader(key = '', value = '') {
  return { id: nextHeaderId++, key, value }
}

/** The request the editor starts with: GET, empty everything, one blank header. */
export function createInitialRequest() {
  return {
    method: 'GET',
    url: '',
    headers: [makeHeader()],
    body: '',
  }
}

/**
 * Turn the editor state into the plain shape later phases care about:
 * { method, url, headers: [{ key, value }], body }.
 * Drops the UI-only `id` and any header whose key is blank.
 */
export function toRequestPayload(request) {
  return {
    method: request.method,
    url: request.url.trim(),
    headers: request.headers
      .filter((header) => header.key.trim() !== '')
      .map((header) => ({ key: header.key.trim(), value: header.value })),
    body: request.body,
  }
}
