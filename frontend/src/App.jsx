import Sidebar from './components/Sidebar.jsx'
import RequestEditor from './components/RequestEditor.jsx'
import ResponsePanel from './components/ResponsePanel.jsx'
import BackendStatus from './components/BackendStatus.jsx'
import { useRequest } from './hooks/useRequest.js'
import { useSendRequest } from './hooks/useSendRequest.js'

/**
 * Top-level layout:
 *
 *   topbar:  brand ................................ backend status
 *   body:    [ Sidebar ] [ RequestEditor + ResponsePanel ]
 *
 * Two hooks own the state:
 *   useRequest      - the request being edited (method/url/headers/body)
 *   useSendRequest  - sending it and the result/error/loading state
 *
 * App holds both so RequestEditor (writes the request, triggers send) and
 * ResponsePanel (reads the result) can stay siblings.
 */
export default function App() {
  const { request, ...actions } = useRequest()
  const { result, error, isSending, send } = useSendRequest()

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
          <RequestEditor
            request={request}
            actions={actions}
            onSend={send}
            isSending={isSending}
          />
          <ResponsePanel result={result} error={error} isSending={isSending} />
        </main>
      </div>
    </div>
  )
}
