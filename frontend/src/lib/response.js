// Pure helpers for the Response viewer. No React.

// Human-readable text for common status codes. Unknown codes just show the
// number (never crash).
const STATUS_TEXT = {
  200: 'OK', 201: 'Created', 202: 'Accepted', 203: 'Non-Authoritative Information',
  204: 'No Content', 205: 'Reset Content', 206: 'Partial Content',
  301: 'Moved Permanently', 302: 'Found', 303: 'See Other', 304: 'Not Modified',
  307: 'Temporary Redirect', 308: 'Permanent Redirect',
  400: 'Bad Request', 401: 'Unauthorized', 402: 'Payment Required', 403: 'Forbidden',
  404: 'Not Found', 405: 'Method Not Allowed', 406: 'Not Acceptable',
  407: 'Proxy Authentication Required', 408: 'Request Timeout', 409: 'Conflict',
  410: 'Gone', 411: 'Length Required', 412: 'Precondition Failed',
  413: 'Payload Too Large', 414: 'URI Too Long', 415: 'Unsupported Media Type',
  416: 'Range Not Satisfiable', 417: 'Expectation Failed', 418: "I'm a teapot",
  421: 'Misdirected Request', 422: 'Unprocessable Entity', 423: 'Locked',
  425: 'Too Early', 426: 'Upgrade Required', 428: 'Precondition Required',
  429: 'Too Many Requests', 431: 'Request Header Fields Too Large',
  451: 'Unavailable For Legal Reasons',
  500: 'Internal Server Error', 501: 'Not Implemented', 502: 'Bad Gateway',
  503: 'Service Unavailable', 504: 'Gateway Timeout', 505: 'HTTP Version Not Supported',
  507: 'Insufficient Storage', 511: 'Network Authentication Required',
}

export function statusText(code) {
  return STATUS_TEXT[code] ?? ''
}

/** UTF-8 byte length of a string (what actually crossed the wire, not JS chars). */
export function byteLength(str) {
  if (typeof str !== 'string' || str.length === 0) return 0
  try {
    return new TextEncoder().encode(str).length
  } catch {
    return new Blob([str]).size // very old fallback
  }
}

/** "532 B", "1.42 KB", "3.10 MB". */
export function formatBytes(n) {
  if (!Number.isFinite(n) || n < 0) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(2)} KB`
  return `${(n / (1024 * 1024)).toFixed(2)} MB`
}

/** Case-insensitive lookup in the flat headers object the backend returns. */
export function headerValue(headers, name) {
  if (!headers) return null
  const target = name.toLowerCase()
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === target) return headers[key]
  }
  return null
}

/** The MIME type (no parameters), lower-cased, from a Content-Type value. */
export function mimeType(headers) {
  const raw = headerValue(headers, 'content-type')
  if (!raw) return ''
  return String(raw).split(';')[0].trim().toLowerCase()
}

/**
 * Classify the body from the Content-Type:
 *   'json' | 'html' | 'xml' | 'text' | 'binary' | 'unknown'
 * '+json' / '+xml' suffixes are honoured. Unknown text-ish types fall back to
 * 'text' (shown as plain text); obvious binary types to 'binary'.
 */
export function classifyBody(headers) {
  const mime = mimeType(headers)
  if (!mime) return 'unknown'
  if (mime === 'application/json' || mime === 'text/json' || mime.endsWith('+json')) return 'json'
  if (mime === 'text/html' || mime === 'application/xhtml+xml') return 'html'
  if (mime === 'application/xml' || mime === 'text/xml' || mime.endsWith('+xml')) return 'xml'
  if (mime.startsWith('text/')) return 'text'
  if (
    mime.startsWith('image/') ||
    mime.startsWith('audio/') ||
    mime.startsWith('video/') ||
    mime.startsWith('font/') ||
    mime === 'application/octet-stream' ||
    mime === 'application/pdf' ||
    mime === 'application/zip' ||
    mime === 'application/gzip' ||
    mime === 'application/x-protobuf' ||
    mime.startsWith('application/vnd.')
  ) {
    return 'binary'
  }
  return 'text'
}

/**
 * Try to pretty-print `body` as JSON.
 *   { ok: true,  text: "<indented>" }   when it parses
 *   { ok: false, text: body }           when it doesn't (caller shows raw)
 * Never throws.
 */
export function tryFormatJson(body) {
  if (typeof body !== 'string' || body.trim() === '') {
    return { ok: false, text: body ?? '' }
  }
  try {
    return { ok: true, text: JSON.stringify(JSON.parse(body), null, 2) }
  } catch {
    return { ok: false, text: body }
  }
}

/** A quick "does this look like JSON?" check for bodies with no useful Content-Type. */
export function looksLikeJson(body) {
  if (typeof body !== 'string') return false
  const t = body.trim()
  return t.startsWith('{') || t.startsWith('[')
}

/**
 * A readable approximation of the raw response (NOT the exact bytes on the wire):
 * status line, headers, blank line, body.
 */
export function rawText(statusCode, headers, body) {
  const lines = [`Status: ${statusCode}${statusText(statusCode) ? ' ' + statusText(statusCode) : ''}`, '']
  const entries = headers ? Object.entries(headers) : []
  for (const [name, value] of entries) {
    lines.push(`${name}: ${value}`)
  }
  lines.push('')
  lines.push(body && body.length > 0 ? body : '(no response body)')
  return lines.join('\n')
}
