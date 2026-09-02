import Sidebar from './components/Sidebar.jsx'
import RequestEditor from './components/RequestEditor.jsx'
import ResponsePanel from './components/ResponsePanel.jsx'
import BackendStatus from './components/BackendStatus.jsx'
import { useRequest } from './hooks/useRequest.js'

/**
 * Top-level layout:
 *
 *   topbar:  brand ................................ backend status
 *   body:    [ Sidebar ] [ RequestEditor + ResponsePanel ]
 *
 * The request state lives here (via useRequest) so that later phases can let the
 * Sidebar load a request into the editor. For now only RequestEditor uses it.
 */
export default function App() {
  const { request, ...actions } = useRequest()

  return (
    <div className="app">
      <header className="app__topbar">
        <div className="app__brand">
          <span className="app__logo">cURL GUI</span>
          <span className="app__tag">HTTP request builder</span>
        </div>
        <BackendStatus />
      </header>

      <div className="app__body">
        <Sidebar />
        <main className="app__main">
          <RequestEditor request={request} actions={actions} />
          <ResponsePanel />
        </main>
      </div>
    </div>
  )
}
