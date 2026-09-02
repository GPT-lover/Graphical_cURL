// Frontend-only helper for deciding whether to MASK a value in the UI.
// (The backend has its own SensitiveHeaders for what to persist; this one is
// purely about not showing secrets on screen by default.) Case-insensitive,
// conservative - it should not mask a harmless "BASE_URL" or "USER_ID".

const FRAGMENTS = [
  'token',
  'secret',
  'password',
  'passwd',
  'apikey',
  'api-key',
  'api_key',
  'auth',
  'credential',
  'private',
]

/** True if a name (e.g. an environment-variable key) looks like a credential. */
export function isSensitiveName(name) {
  if (typeof name !== 'string') return false
  const n = name.trim().toLowerCase()
  if (!n) return false
  return FRAGMENTS.some((f) => n.includes(f))
}
