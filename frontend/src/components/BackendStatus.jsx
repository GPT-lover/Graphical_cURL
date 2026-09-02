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

  return (
    <div className={`status status--${state}`}>
      {state === 'loading' && <span>Checking backend…</span>}

      {state === 'ok' && (
        <span>
          ● Backend connected — <code>{detail.service}</code> reported{' '}
          <strong>{detail.status}</strong> at {detail.timestamp}
        </span>
      )}

      {state === 'error' && (
        <span>
          ● Backend not reachable — {detail.message}
        </span>
      )}
    </div>
  )
}
