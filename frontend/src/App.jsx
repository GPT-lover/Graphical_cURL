import BackendStatus from './components/BackendStatus.jsx'

// Phase 1 shell. The real layout (sidebar + request editor + response panel)
// arrives in Phase 2. For now this page exists only to verify the two apps
// can talk to each other.
export default function App() {
  return (
    <div className="app">
      <header className="app__header">
        <h1>cURL GUI</h1>
        <p className="app__subtitle">Phase 1 — project skeleton</p>
      </header>

      <main className="app__main">
        <BackendStatus />

        <p className="app__hint">
          If the banner above is green, the React frontend successfully reached
          the Spring Boot backend. The request editor comes next (Phase 2).
        </p>
      </main>
    </div>
  )
}
