// Pure (non-React) helpers for the request object.
//
// The request the user is editing looks like this:
//
//   {
//     method: "GET",
//     url: "",
//     headers: [ { id: 1, key: "", value: "" } ],
//     cookies: [ { id: 2, key: "", value: "" } ],
//     body: "",
//     bodyType: "raw",   // "raw" | "multipart" | "binary"
//     multipart: [ { id: 3, type: "text" | "file", name: "", value: "", contentType: "" } ],
//   }
//
// bodyType picks which body the request actually sends:
//   "raw"       - `body` is sent as-is (unchanged from before multipart support).
//   "multipart" - `multipart` fields are sent as a real multipart/form-data
//                 request that curl itself builds (boundary, encoding, file
//                 streaming); `body` is ignored.
//   "binary"    - `body` holds the absolute path to a local file whose bytes
//                 are streamed as the request body (curl's --data-binary
//                 @file); the file is never read into the browser/editor.
//
// The `id` on each row is a UI-only concern: React needs a stable key for each
// row so that adding/removing rows doesn't muddle which <input> is which (using
// the array index breaks focus and value tracking when rows move). It is
// stripped out by toRequestPayload() before the request leaves the editor.

/**
 * Normalize line endings the same way a browser's <textarea> does whenever
 * its `.value` is read: CRLF and lone CR both collapse to LF. This is a DOM
 * behaviour (the HTML "API value" algorithm for textarea), not something
 * this app does on purpose - see reconcileBodyEdit for why it matters.
 */
export function normalizeTextareaNewlines(text) {
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

/**
 * Decide what the Raw body state should become after a `<textarea>` change
 * event.
 *
 * A `<textarea>`'s `.value` getter silently rewrites `\r\n`/`\r` to `\n`
 * every time it is read - including inside the very first `onChange` after
 * loading a body that still has literal `\r\n` (e.g. a multipart/form-data
 * body copied from Chrome DevTools, where `\r\n` is significant: RFC 2046
 * requires it around each boundary line). Without this check, one keystroke
 * - even one immediately undone - would permanently replace the imported
 * bytes with an LF-only copy: the text looks identical (nobody can see a
 * `\r`), but the bytes curl actually sends are now different, which is
 * enough to make a strict multipart parser reject the request.
 *
 * If the edited value is a no-op once both sides are newline-normalized
 * (nothing the user typed is visible as an actual content change - only
 * line endings the textarea rewrote on its own), keep the original,
 * byte-exact body instead of the browser's rewritten one. Once the user
 * makes a real content change, accept the browser's value as-is: a
 * `<textarea>` cannot produce a literal `\r` from typing, so there is
 * nothing further to preserve once real editing has happened.
 */
export function reconcileBodyEdit(pristineBody, editedValue) {
  if (
    pristineBody != null &&
    normalizeTextareaNewlines(editedValue) === normalizeTextareaNewlines(pristineBody)
  ) {
    return pristineBody
  }
  return editedValue
}

let nextRowId = 1

/** Create one header row. */
export function makeHeader(key = '', value = '') {
  return { id: nextRowId++, key, value }
}

/** Create one cookie row (same shape as a header row). */
export function makeCookie(key = '', value = '') {
  return { id: nextRowId++, key, value }
}

/** Create one multipart field row. `type` is "text" or "file". */
export function makeMultipartField(type = 'text', name = '', value = '', contentType = '') {
  return { id: nextRowId++, type, name, value, contentType }
}

/** The request the editor starts with: GET, empty everything, one blank row each. */
export function createInitialRequest() {
  return {
    method: 'GET',
    url: '',
    headers: [makeHeader()],
    cookies: [makeCookie()],
    body: '',
    bodyType: 'raw',
    multipart: [],
    // Transport options from an imported cURL command (--compressed, --http1.1,
    // -L, -k, --connect-timeout, --max-time, --proxy). The editor has no UI for
    // these yet; it just carries them so they reach the backend on Send. null
    // for a hand-built request.
    curlOptions: null,
  }
}

/**
 * Turn the editor state into the plain shape the backend expects:
 * { method, url, headers: [{key,value}], cookies: [{key,value}], body,
 *   bodyType, multipart: [{type,name,value,contentType}] }.
 * Drops the UI-only `id` and any row whose key/name is blank.
 */
export function toRequestPayload(request) {
  const bodyType = request.bodyType ?? 'raw'
  const isMultipart = bodyType === 'multipart'
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
    bodyType,
    multipart: isMultipart
      ? (request.multipart ?? [])
          .filter((field) => field.name.trim() !== '')
          .map((field) => ({
            type: field.type === 'file' ? 'file' : 'text',
            name: field.name.trim(),
            value: field.value ?? '',
            contentType: field.contentType?.trim() ? field.contentType.trim() : null,
          }))
      : null,
    // Passed straight through to the backend (see createInitialRequest).
    curlOptions: request.curlOptions ?? null,
  }
}
