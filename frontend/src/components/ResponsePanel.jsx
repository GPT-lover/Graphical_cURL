/**
 * Placeholder for the response area. Becomes functional in Phase 3 once the
 * backend can actually send the request and return status / headers / body /
 * timing.
 */
export default function ResponsePanel() {
  return (
    <section className="panel response-panel">
      <div className="panel__header">
        <h2 className="panel__title">Response</h2>
      </div>

      <div className="response-panel__placeholder">
        Status, headers, body and timing will appear here after the request is
        sent (Phase 3).
      </div>
    </section>
  )
}
