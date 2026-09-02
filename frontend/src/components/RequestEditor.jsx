import MethodSelector from './MethodSelector.jsx'
import UrlBar from './UrlBar.jsx'
import HeadersEditor from './HeadersEditor.jsx'
import CookiesEditor from './CookiesEditor.jsx'
import BodyEditor from './BodyEditor.jsx'
import EnvironmentBar from './EnvironmentBar.jsx'

/**
 * Assembles the request editor from its sub-components and wires each one to the
 * matching helper from the useRequest hook.
 *
 * Props:
 *   request          - the current request object (from useRequest)
 *   actions          - the setter helpers from useRequest
 *   onSend           - () send the request
 *   isSending        - true while a request is in flight (disables Send)
 *   onImportClick    - () open the "Import cURL" dialog
 *   onExport         - (request) generate + copy as a cURL command
 *   exportCopied     - true briefly after a successful "Copy as cURL"
 *   onRunMultiple    - () open the "Run Multiple" dialog
 *   savedRequestName - name of the loaded saved request, or null if unsaved
 *   onNewRequest     - () clear the editor + the saved-request selection
 *   onSave           - () open the Save dialog (create a new saved request)
 *   onUpdate         - () overwrite the currently loaded saved request
 *   onSaveAs         - () open the Save dialog to create a copy
 *   environments / activeEnvironmentId / environmentsError / onEnvironmentChange /
 *     onManageEnvironments - passed straight through to <EnvironmentBar>
 */
export default function RequestEditor({
  request,
  actions,
  onSend,
  isSending,
  onImportClick,
  onExport,
  exportCopied,
  onRunMultiple,
  savedRequestName,
  onNewRequest,
  onSave,
  onUpdate,
  onSaveAs,
  environments,
  activeEnvironmentId,
  environmentsError,
  onEnvironmentChange,
  onManageEnvironments,
}) {
  const {
    setMethod,
    setUrl,
    setBody,
    updateHeader,
    addHeader,
    removeHeader,
    updateCookie,
    addCookie,
    removeCookie,
    clearCookies,
  } = actions

  function handleSend() {
    if (isSending) {
      return // guard against Enter-key spam while a request runs
    }
    onSend(request)
  }

  return (
    <div className="request-editor">
      <EnvironmentBar
        environments={environments}
        activeId={activeEnvironmentId}
        loadError={environmentsError}
        onChange={onEnvironmentChange}
        onManage={onManageEnvironments}
      />

      <div className="editor-toolbar">
        <div className="editor-toolbar__left">
          <button type="button" className="btn" onClick={onNewRequest}>
            New Request
          </button>
          <button type="button" className="btn" onClick={onImportClick}>
            Import cURL
          </button>
        </div>

        <div className="editor-toolbar__right">
          {savedRequestName ? (
            <>
              <span className="editing-label" title={savedRequestName}>
                editing: {savedRequestName}
              </span>
              <button type="button" className="btn" onClick={onUpdate}>
                Update
              </button>
              <button type="button" className="btn" onClick={onSaveAs}>
                Save As
              </button>
            </>
          ) : (
            <button type="button" className="btn" onClick={onSave}>
              Save
            </button>
          )}
        </div>
      </div>

      <div className="request-bar">
        <MethodSelector value={request.method} onChange={setMethod} />
        <UrlBar value={request.url} onChange={setUrl} onSend={handleSend} />
        <button
          type="button"
          className="btn btn--primary"
          onClick={handleSend}
          disabled={isSending}
        >
          {isSending ? 'Sending…' : 'Send'}
        </button>
        <button type="button" className="btn" onClick={onRunMultiple}>
          Run Multiple
        </button>
        <button type="button" className="btn" onClick={() => onExport(request)}>
          Copy as cURL
        </button>
      </div>

      {exportCopied && <p className="copied-note">✓ cURL copied to clipboard</p>}

      <HeadersEditor
        headers={request.headers}
        onChange={updateHeader}
        onAdd={addHeader}
        onRemove={removeHeader}
      />

      <CookiesEditor
        cookies={request.cookies}
        onChange={updateCookie}
        onAdd={addCookie}
        onRemove={removeCookie}
        onClear={clearCookies}
      />

      <BodyEditor value={request.body} onChange={setBody} />
    </div>
  )
}
