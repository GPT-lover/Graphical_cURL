import { useEffect, useState } from 'react'
import { fetchHealth } from '../api/client.js'

/**
 * Calls GET /api/health once when it mounts and shows whether the backend is
 * reachable. This is the Phase 1 proof that frontend <-> backend communication
 * (including CORS) works end to end.
 */
export default function BackendStatus() {
  // 'loading' | 'ok' | 'error'
  const [state, setState] = useState('loading')
  const [detail, setDetail] = useState(null)

  useEffect(() => {
    let cancelled = false

    async function check() {
      try {
        const data = await fetchHealth()
        if (!cancelled) {
          setState('ok')
          setDetail(data)
        }
      } catch (err) {
        if (!cancelled) {
          setState('error')
          setDetail({ message: err.message })
        }
      }
    }

    check()
    // If the component unmounts before the request finishes, don't call setState.
    return () => {
      cancelled = true
    }
  }, [])

  // Compact pill for the top bar. Full detail is in the hover tooltip.
  const label = {
    loading: 'Backend: checking…',
    ok: 'Backend: connected',
    error: 'Backend: offline',
  }[state]

  const tooltip = {
    loading: 'Contacting GET /api/health …',
    ok: detail
      ? `${detail.service} reported ${detail.status} at ${detail.timestamp}`
      : 'Connected',
    error: detail?.message ?? 'Not reachable',
  }[state]

  return (
    <div className={`status status--${state}`} title={tooltip}>
      <span className="status__dot" aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
