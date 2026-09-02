import { useState } from 'react'
import Sidebar from './components/Sidebar.jsx'
import RequestEditor from './components/RequestEditor.jsx'
import ResponsePanel from './components/ResponsePanel.jsx'
import BackendStatus from './components/BackendStatus.jsx'
import ImportCurlModal from './components/ImportCurlModal.jsx'
import { useRequest } from './hooks/useRequest.js'
import { useSendRequest } from './hooks/useSendRequest.js'

/**
 * Top-level layout:
 *
 *   topbar:  brand ................................ backend status
 *   body:    [ Sidebar ] [ RequestEditor + ResponsePanel ]
 *
 * State owners:
 *   useRequest      - the request being edited (method/url/headers/cookies/body)
 *   useSendRequest  - sending it and the result/error/loading state
 *   importOpen      - whether the "Import cURL" dialog is showing
 *
 * App holds these so RequestEditor (edits the request, triggers send/import) and
 * ResponsePanel (reads the result) can stay siblings.
 */
export default function App() {
  const { request, loadRequest, ...actions } = useRequest()
  const { result, error, isSending, send } = useSendRequest()
  const [importOpen, setImportOpen] = useState(false)

  function handleImported(parsed) {
    // parsed is the backend's ParsedRequestDto. Load it into the editor for the
    // user to review - it is NOT sent automatically.
    loadRequest(parsed)
    setImportOpen(false)
  }

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
            onImportClick={() => setImportOpen(true)}
          />
          <ResponsePanel result={result} error={error} isSending={isSending} />
        </main>
      </div>

      <ImportCurlModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={handleImported}
      />
    </div>
  )
}
