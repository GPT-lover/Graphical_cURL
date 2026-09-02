// Tiny wrapper around fetch() so every component talks to the backend the same
// way. As the API grows (Phase 3+) new functions get added here rather than
// scattering fetch() calls through components.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * GET a JSON endpoint and return the parsed body.
 * Throws an Error with a useful message on network failure or non-2xx status.
 */
export async function getJson(path) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`)
  } catch (cause) {
    // fetch() only rejects on network-level problems (server down, DNS, CORS).
    throw new Error(`Could not reach the backend at ${BASE_URL}. Is it running?`)
  }

  if (!response.ok) {
    throw new Error(`Backend responded with HTTP ${response.status}`)
  }
  return response.json()
}

/** Convenience call for the Phase 1 handshake endpoint. */
export function fetchHealth() {
  return getJson('/api/health')
}
