// Display helpers for the History sidebar.

/**
 * Shorten a URL for the compact sidebar: "host + path" without the scheme, and
 * without a trailing "/". The full URL is still kept on the history entry and
 * restored in full when the item is clicked - this is display only.
 */
export function shortUrl(url) {
  if (!url) return ''
  try {
    const u = new URL(url)
    let text = u.host + u.pathname
    if (text.length > 1 && text.endsWith('/')) text = text.slice(0, -1)
    return u.search ? text + u.search : text
  } catch {
    // Not a parseable URL - just strip a leading scheme if present.
    return url.replace(/^[a-z]+:\/\//i, '')
  }
}

/** "just now", "3 min ago", "2 h ago", "5 d ago", else a short date. */
export function timeAgo(iso) {
  if (!iso) return ''
  const then = Date.parse(iso)
  if (Number.isNaN(then)) return ''
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000))
  if (seconds < 45) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days} d ago`
  return new Date(then).toLocaleDateString()
}
