import { useCallback, useRef, useState } from 'react'
import { sendRequest } from '../api/client.js'
import { toRequestPayload } from '../lib/request.js'

/**
 * Owns everything about "sending the request and getting a result back":
 *
 *   result    - the SendResponseDto from the backend, or null
 *   error     - { message, detail? } for display, or null
 *   isSending - true while a request is in flight (drives the button + spinner)
 *   send(req) - kick off a send for the given editor request object
 *
 * Only one request runs at a time: `send` ignores calls made while one is
 * already in flight, and the Send button is also disabled - together that stops
 * duplicate requests from frantic clicking.
 */
export function useSendRequest() {
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [isSending, setIsSending] = useState(false)

  // A ref (not state) because we need the up-to-date value synchronously inside
  // send(), before React re-renders.
  const inFlight = useRef(false)

  const send = useCallback(async (request) => {
    if (inFlight.current) {
      return
    }

    const payload = toRequestPayload(request)

    // Light client-side check only. Real validation happens on the backend.
    if (!payload.url) {
      setResult(null)
      setError({ message: 'Enter a URL before sending.' })
      return
    }

    inFlight.current = true
    setIsSending(true)
    setError(null)
    setResult(null)

    try {
      const data = await sendRequest(payload)
      setResult(data)
    } catch (err) {
      // err is an ApiError from client.js (or any unexpected Error - still safe).
      setError({ message: err.message, detail: err.detail ?? null })
    } finally {
      inFlight.current = false
      setIsSending(false)
    }
  }, [])

  return { result, error, isSending, send }
}
