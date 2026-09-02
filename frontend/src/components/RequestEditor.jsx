import MethodSelector from './MethodSelector.jsx'
import UrlBar from './UrlBar.jsx'
import HeadersEditor from './HeadersEditor.jsx'
import BodyEditor from './BodyEditor.jsx'
import { toRequestPayload } from '../lib/request.js'

/**
 * Assembles the request editor from its four sub-components and wires each one
 * to the matching helper from the useRequest hook.
 *
 * Props:
 *   request - the current request object (from useRequest)
 *   actions - { setMethod, setUrl, updateHeader, addHeader, removeHeader, setBody }
 */
export default function RequestEditor({ request, actions }) {
  const { setMethod, setUrl, updateHeader, addHeader, removeHeader, setBody } =
    actions

  function handleSend() {
    // Phase 2: no network yet. Log the request so we can confirm the editor is
    // tracking state correctly. Phase 3 will POST this payload to the backend.
    const payload = toRequestPayload(request)
    console.log('[cURL GUI] Request payload →', payload)
    console.log('[cURL GUI] Raw editor state →', request)
  }

  return (
    <div className="request-editor">
      <div className="request-bar">
        <MethodSelector value={request.method} onChange={setMethod} />
        <UrlBar value={request.url} onChange={setUrl} onSend={handleSend} />
        <button type="button" className="btn btn--primary" onClick={handleSend}>
          Send
        </button>
      </div>

      <HeadersEditor
        headers={request.headers}
        onChange={updateHeader}
        onAdd={addHeader}
        onRemove={removeHeader}
      />

      <BodyEditor value={request.body} onChange={setBody} />
    </div>
  )
}
