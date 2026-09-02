import { useState } from 'react'
import Sidebar from './components/Sidebar.jsx'
import RequestEditor from './components/RequestEditor.jsx'
import ResponsePanel from './components/ResponsePanel.jsx'
import BackendStatus from './components/BackendStatus.jsx'
import ImportCurlModal from './components/ImportCurlModal.jsx'
import ExportCurlModal from './components/ExportCurlModal.jsx'
import { useRequest } from './hooks/useRequest.js'
import { useSendRequest } from './hooks/useSendRequest.js'
import { useCurlExport } from './hooks/useCurlExport.js'
import { useHistory } from './hooks/useHistory.js'

/**
 * Top-level layout:
 *
 *   topbar:  brand ................................ backend status
 *   body:    [ Sidebar (History) ] [ RequestEditor + ResponsePanel ]
 *
 * State owners:
 *   useRequest      - the request being edited (method/url/headers/cookies/body)
 *   useSendRequest  - sending it and the result/error/loading state
 *   useCurlExport   - "Copy as cURL": generate + clipboard + preview modal
 *   useHistory      - the History sidebar list (SQLite-backed, via the API)
 *   importOpen      - whether the "Import cURL" dialog is showing
 */
export default function App() {
  const { request, loadRequest, ...actions } = useRequest()
  const history = useHistory()
  // The backend saves history as part of /send, so just reload the list after.
  const { result, error, isSending, send } = useSendRequest(history.refresh)
  const exportCurl = useCurlExport()
  const [importOpen, setImportOpen] = useState(false)

  function handleImported(parsed) {
    // parsed is the backend's ParsedRequestDto. Load it into the editor for the
    // user to review - it is NOT sent automatically.
    loadRequest(parsed)
    setImportOpen(false)
  }

  function handleRestoreFromHistory(entry) {
    // Restore method / url / headers / body. Cookies were never persisted, so
    // loadRequest resets them to a single blank row (no reconstruction).
    loadRequest(entry)
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
        <Sidebar history={history} onRestore={handleRestoreFromHistory} />
        <main className="app__main">
          <RequestEditor
            request={request}
            actions={actions}
            onSend={send}
            isSending={isSending}
            onImportClick={() => setImportOpen(true)}
            onExport={exportCurl.run}
            exportCopied={exportCurl.copied}
          />
          <ResponsePanel result={result} error={error} isSending={isSending} />
        </main>
      </div>

      <ImportCurlModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        onImported={handleImported}
      />

      <ExportCurlModal
        status={exportCurl.status}
        curl={exportCurl.curl}
        error={exportCurl.error}
        copied={exportCurl.copied}
        onCopy={exportCurl.copy}
        onClose={exportCurl.close}
      />
    </div>
  )
}
