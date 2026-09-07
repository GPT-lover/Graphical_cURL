import { useCallback, useState } from 'react'

let nextStepId = 1

/**
 * Holds the ordered list of requests that make up a "Request Chain", the loop
 * count and the between-loops cooldown, plus small helpers to
 * add/remove/reorder/edit steps.
 *
 * A step is a plain { id, method, url, headers, body } snapshot - the same
 * shape `toRequestPayload()` produces for the main editor (minus cookies,
 * which chain steps don't carry; see App.jsx's handleAddToChain). `id` is a
 * UI-only React key, stripped before the chain is sent to the backend.
 *
 * `cooldown` is the pause (in ms, as a string for the <input>) applied BETWEEN
 * complete loop iterations - never between the requests inside one iteration,
 * and never after the last iteration. '0' (the default) means no cooldown and
 * preserves the original behaviour exactly.
 */
export function useChainBuilder() {
  const [steps, setSteps] = useState([])
  const [loops, setLoops] = useState('1')
  const [cooldown, setCooldown] = useState('0')

  const addStep = useCallback((snapshot) => {
    setSteps((prev) => [...prev, { id: nextStepId++, ...snapshot }])
  }, [])

  const removeStep = useCallback((id) => {
    setSteps((prev) => prev.filter((step) => step.id !== id))
  }, [])

  const updateStep = useCallback((id, field, value) => {
    setSteps((prev) => prev.map((step) => (step.id === id ? { ...step, [field]: value } : step)))
  }, [])

  /** Swap the step at `id` with its neighbour (direction -1 = up, +1 = down). */
  const moveStep = useCallback((id, direction) => {
    setSteps((prev) => {
      const index = prev.findIndex((step) => step.id === id)
      const swapWith = index + direction
      if (index < 0 || swapWith < 0 || swapWith >= prev.length) {
        return prev
      }
      const next = [...prev]
      const tmp = next[index]
      next[index] = next[swapWith]
      next[swapWith] = tmp
      return next
    })
  }, [])

  const clearSteps = useCallback(() => setSteps([]), [])

  return {
    steps,
    loops,
    setLoops,
    cooldown,
    setCooldown,
    addStep,
    removeStep,
    updateStep,
    moveStep,
    clearSteps,
  }
}
