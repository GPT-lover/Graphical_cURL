import MethodSelector from './MethodSelector.jsx'
import UrlBar from './UrlBar.jsx'
import HeadersEditor from './HeadersEditor.jsx'
import BodyEditor from './BodyEditor.jsx'

/**
 * Assembles the request editor from its four sub-components and wires each one
 * to the matching helper from the useRequest hook.
 *
 * Props:
 *   request   - the current request object (from useRequest)
 *   actions   - { setMethod, setUrl, updateHeader, addHeader, removeHeader, setBody }
 *   onSend    - () called with the request when the user hits Send / Enter
 *   isSending - true while a request is in flight (disables the button)
 */
export default function RequestEditor({ request, actions, onSend, isSending }) {
  const { setMethod, setUrl, updateHeader, addHeader, removeHeader, setBody } =
    actions

  function handleSend() {
    if (isSending) {
      return // guard against Enter-key spam while a request runs
    }
    onSend(request)
  }

  return (
    <div className="request-editor">
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
