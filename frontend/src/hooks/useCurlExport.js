import { useCallback, useEffect, useState } from 'react'
import { exportCurl } from '../api/client.js'
import { toRequestPayload } from '../lib/request.js'

/**
 * Owns "Copy as cURL": ask the backend to generate the command, copy it to the
 * clipboard, and show it in a preview modal.
 *
 *   status  - 'idle' | 'working' | 'ready' | 'error'
 *   curl    - the generated command (when status === 'ready')
 *   error   - message to show (when status === 'error')
 *   copied  - true briefly after a successful clipboard write
 *   run(request) - generate + copy + open the preview
 *   copy(text)   - copy again (the modal's Copy button)
 *   close()      - close the preview
 *
 * Clipboard failure is NOT treated as an error: the modal still shows the
 * command so the user can select and copy it by hand.
 *
 * Nothing here logs the request or the generated command (both can contain
 * cookies / tokens).
 */
export function useCurlExport() {
  const [status, setStatus] = useState('idle')
  const [curl, setCurl] = useState('')
  const [error, setError] = useState(null)
  const [copied, setCopied] = useState(false)

  // Auto-hide the "copied" confirmation.
  useEffect(() => {
    if (!copied) return undefined
    const timer = setTimeout(() => setCopied(false), 2500)
    return () => clearTimeout(timer)
  }, [copied])

  const copy = useCallback(async (text) => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      // Clipboard blocked (permissions, insecure context, old browser). Fine -
      // the preview modal still shows the command to copy manually.
      setCopied(false)
    }
  }, [])

  const run = useCallback(
    async (request) => {
      if (status === 'working') return
      const payload = toRequestPayload(request)
      if (!payload.url) {
        setStatus('error')
        setError('Enter a URL before exporting to cURL.')
        setCurl('')
        return
      }
      setStatus('working')
      setError(null)
      setCopied(false)
      try {
        const data = await exportCurl(payload)
        setCurl(data.curl)
        setStatus('ready')
        copy(data.curl) // best effort; the modal opens regardless
      } catch (err) {
        setStatus('error')
        setError(err.detail ? `${err.message}\n${err.detail}` : err.message)
        setCurl('')
      }
    },
    [status, copy],
  )

  const close = useCallback(() => {
    setStatus('idle')
    setError(null)
    setCopied(false)
  }, [])

  return { status, curl, error, copied, run, copy, close }
}
