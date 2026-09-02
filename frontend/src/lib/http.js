// Small HTTP display helpers shared by the response panel and the history list.

/**
 * Map a status code to a coarse class: 'ok' (2xx), 'redirect' (3xx), or 'error'
 * (4xx + 5xx). Drives the colour of the status pill - never hard-codes 200.
 */
export function statusClass(code) {
  if (code >= 200 && code < 300) return 'ok'
  if (code >= 300 && code < 400) return 'redirect'
  return 'error'
}

/** "143ms" under a second, "2.31s" above. */
export function formatDuration(ms) {
  if (typeof ms !== 'number' || Number.isNaN(ms)) return ''
  return ms < 1000 ? `${Math.round(ms)}ms` : `${(ms / 1000).toFixed(2)}s`
}
