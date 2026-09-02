// Tiny wrapper around fetch() so every component talks to the backend the same
// way. As the API grows, new functions get added here rather than scattering
// fetch() calls through components.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * One error type for everything that can go wrong talking to our backend, so the
 * UI can show a useful message instead of crashing. `kind` lets the UI phrase it:
 *
 *   'network'   - the backend couldn't be reached at all (not running, wrong
 *                 port, CORS blocked). fetch() itself rejected.
 *   'backend'   - the backend answered with a 4xx/5xx and an ErrorResponseDto.
 *   'malformed' - the backend answered but the body wasn't the JSON we expected.
 */
export class ApiError extends Error {
  constructor(kind, message, detail = null, status = null) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.detail = detail
    this.status = status
  }
}

/**
 * Send `payload` as JSON to `path` with the given method and return the parsed
 * JSON body. Shared by every write endpoint. Throws an {@link ApiError} for
 * network failure, a non-2xx response, or a non-JSON body - never leaves fetch's
 * raw rejection to bubble up.
 */
async function sendJson(method, path, payload) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
  } catch {
    throw new ApiError(
      'network',
      `Could not connect to the backend at ${BASE_URL}.`,
      'Make sure the Spring Boot backend is running on port 8080 (./gradlew bootRun).',
    )
  }

  // Read the body once, as text, so we can handle "not JSON" without a second
  // exception.
  const raw = await response.text()
  let data = null
  if (raw) {
    try {
      data = JSON.parse(raw)
    } catch {
      throw new ApiError(
        'malformed',
        `The backend returned a response that isn't valid JSON (HTTP ${response.status}).`,
        raw.slice(0, 500),
        response.status,
      )
    }
  }

  if (!response.ok) {
    // Spring's *built-in* error body looks like
    //   { timestamp, status, error, path }
    // and means the request never reached our controller at all - almost always
    // because the running backend is an older build without this endpoint. Say
    // that plainly instead of surfacing a bare "Not Found".
    const isFrameworkError =
      data && data.timestamp !== undefined && data.path !== undefined

    if (isFrameworkError) {
      const hint =
        response.status === 404
          ? `The running backend has no "${path}" endpoint. Stop it and run ` +
            `"gradlew.bat bootRun" again so it compiles the latest code.`
          : `The backend rejected the request before the app code ran ` +
            `(${data.error ?? response.status}).`
      throw new ApiError(
        'backend',
        `Backend responded ${response.status} for ${path}`,
        hint,
        response.status,
      )
    }

    // Our own ErrorResponseDto: { error, message } - `error` is a message meant
    // to be shown to the user.
    const message = data?.error ?? `Backend error (HTTP ${response.status}).`
    throw new ApiError('backend', message, data?.message ?? null, response.status)
  }

  return data
}

const postJson = (path, payload) => sendJson('POST', path, payload)
const putJson = (path, payload) => sendJson('PUT', path, payload)

/**
 * Ask the backend to perform the request the user built and return a
 * SendResponseDto: { statusCode, headers, body, durationMs, warnings }.
 *
 * A 404/500 from the *target* server is a normal success here - it comes back
 * inside that object. This only throws for failures of *our* pipeline.
 */
export function sendRequest(payload) {
  return postJson('/api/requests/send', payload)
}

/**
 * Ask the backend to parse a pasted cURL command. Returns a ParsedRequestDto:
 * { method, url, headers, cookies, body, warnings }.
 * The string is only ever parsed on the backend - never executed.
 */
export function importCurl(curl) {
  return postJson('/api/requests/import-curl', { curl })
}

/**
 * Ask the backend to generate a POSIX-shell cURL command from the current
 * request. Returns { curl }. The command is generated as text only - never run.
 */
export function exportCurl(payload) {
  return postJson('/api/requests/export-curl', payload)
}

/**
 * GET a JSON endpoint and return the parsed body.
 * Throws a plain Error on network failure or non-2xx status.
 */
export async function getJson(path) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`)
  } catch {
    // fetch() only rejects on network-level problems (server down, DNS, CORS).
    throw new Error(`Could not reach the backend at ${BASE_URL}. Is it running?`)
  }

  if (!response.ok) {
    throw new Error(`Backend responded with HTTP ${response.status}`)
  }
  return response.json()
}

/** Send a DELETE and resolve on success (2xx or 404 - already gone counts as done). */
async function del(path) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, { method: 'DELETE' })
  } catch {
    throw new ApiError('network', `Could not connect to the backend at ${BASE_URL}.`)
  }
  if (!response.ok && response.status !== 404) {
    throw new ApiError('backend', `Delete failed (HTTP ${response.status}).`, null, response.status)
  }
}

/** Convenience call for the Phase 1 handshake endpoint. */
export function fetchHealth() {
  return getJson('/api/health')
}

// ---- Request history --------------------------------------------------

/** GET /api/history - all history entries, newest first. */
export function fetchHistory() {
  return getJson('/api/history')
}

/** DELETE /api/history/{id} - remove one entry. */
export function deleteHistoryEntry(id) {
  return del(`/api/history/${id}`)
}

/** DELETE /api/history - remove every entry. */
export function clearHistory() {
  return del('/api/history')
}

// ---- Collections & saved requests -----------------------------------

/** GET /api/collections - collections, each with its saved-request summaries. */
export function fetchCollections() {
  return getJson('/api/collections')
}

/** POST /api/collections - create a collection. Returns the created collection. */
export function createCollection(name) {
  return postJson('/api/collections', { name })
}

/** PUT /api/collections/{id} - rename a collection. */
export function renameCollection(id, name) {
  return putJson(`/api/collections/${id}`, { name })
}

/** DELETE /api/collections/{id} - delete a collection and its saved requests. */
export function deleteCollection(id) {
  return del(`/api/collections/${id}`)
}

/** GET /api/saved-requests/{id} - one full saved request. */
export function getSavedRequest(id) {
  return getJson(`/api/saved-requests/${id}`)
}

/**
 * POST /api/saved-requests - create a saved request.
 * `payload` = { name, collectionId, method, url, headers, body } (no cookies).
 */
export function createSavedRequest(payload) {
  return postJson('/api/saved-requests', payload)
}

/** PUT /api/saved-requests/{id} - update an existing saved request. */
export function updateSavedRequest(id, payload) {
  return putJson(`/api/saved-requests/${id}`, payload)
}

/** DELETE /api/saved-requests/{id}. */
export function deleteSavedRequest(id) {
  return del(`/api/saved-requests/${id}`)
}
