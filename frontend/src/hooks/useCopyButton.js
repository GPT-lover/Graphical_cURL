import { useCallback, useEffect, useState } from 'react'

/**
 * Backs a small "Copy" button: writes text to the clipboard and briefly reports
 * the outcome. Clipboard failure is not an error the app should crash on - it
 * just shows "Copy failed" for a moment.
 *
 *   state - 'idle' | 'copied' | 'failed'
 *   copy(text) - attempt the copy
 */
export function useCopyButton() {
  const [state, setState] = useState('idle')

  useEffect(() => {
    if (state === 'idle') return undefined
    const timer = setTimeout(() => setState('idle'), 1600)
    return () => clearTimeout(timer)
  }, [state])

  const copy = useCallback(async (text) => {
    try {
      await navigator.clipboard.writeText(text ?? '')
      setState('copied')
    } catch {
      setState('failed')
    }
  }, [])

  return { state, copy }
}
