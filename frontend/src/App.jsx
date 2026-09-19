import { useCallback, useEffect, useState } from 'react'
import FeaturesPage from './pages/FeaturesPage.jsx'
import Sidebar from './components/Sidebar.jsx'
import RequestEditor from './components/RequestEditor.jsx'
import ResponsePanel from './components/ResponsePanel.jsx'
import BackendStatus from './components/BackendStatus.jsx'
import ImportCurlModal from './components/ImportCurlModal.jsx'
import ExportCurlModal from './components/ExportCurlModal.jsx'
import SaveRequestModal from './components/SaveRequestModal.jsx'
import { useRequest } from './hooks/useRequest.js'
import { useSendRequest } from './hooks/useSendRequest.js'
import { useCurlExport } from './hooks/useCurlExport.js'
import { useHistory } from './hooks/useHistory.js'
import { useCollections } from './hooks/useCollections.js'
import { useEnvironments } from './hooks/useEnvironments.js'
import { useRunMultiple } from './hooks/useRunMultiple.js'
import { useChain } from './hooks/useChain.js'
import { useChainBuilder } from './hooks/useChainBuilder.js'
import { useHydra } from './hooks/useHydra.js'
import EnvironmentModal from './components/EnvironmentModal.jsx'
import RunMultipleModal from './components/RunMultipleModal.jsx'
import ChainModal from './components/ChainModal.jsx'
import HydraModal from './components/HydraModal.jsx'
import { toRequestPayload } from './lib/request.js'
import {
  createCollection,
  createSavedRequest,
  deleteCollection,
  deleteSavedRequest,
  getSavedRequest,
  renameCollection,
  updateSavedRequest,
} from './api/client.js'

/** Tracks `window.location.hash` so the app can switch to a standalone page (e.g. #/features). */
function useHashRoute() {
  const [hash, setHash] = useState(() => window.location.hash)
  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash)
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])
  return hash
}

/**
 * Best-effort guess at Hydra's target/path/form fields from the request
 * currently loaded in the editor. Deliberately simple (see App - Hydra
 * integration): if the URL doesn't parse, or the body isn't a plain
 * `key=value&...` string, the corresponding fields are just left for the user
 * to fill in manually rather than attempting anything cleverer.
 */
function hydraPrefillFromRequest(req) {
  const prefill = { host: '', port: '', protocol: 'http', path: '/', formParams: '' }
  try {
    const u = new URL(req.url)
    prefill.protocol = u.protocol === 'https:' ? 'https' : 'http'
    prefill.host = u.hostname
    prefill.port = u.port || (prefill.protocol === 'https' ? '443' : '80')
    prefill.path = u.pathname || '/'
  } catch {
    // leave the defaults; the user fills in the target manually
  }
  if (typeof req.body === 'string' && /^[^=&]+=[^&]*(&[^=&]+=[^&]*)*$/.test(req.body.trim())) {
    prefill.formParams = req.body.trim()
  }
  return prefill
}

/**
 * Top-level layout and cross-cutting wiring.
 *
 * State owners:
 *   useRequest      - the request being edited
 *   useSendRequest  - sending + result/loading/error
 *   useCurlExport   - "Copy as cURL"
 *   useHistory      - the History sidebar list (Phase 7)
 *   useCollections  - the Saved Requests tree (Phase 8)
 *   saved           - { id, name, collectionId } of the loaded saved request, or
 *                     null. Drives Save vs Update / Save As.
 */
export default function App() {
  const route = useHashRoute()
  const { request, loadRequest, resetRequest, ...actions } = useRequest()
  const history = useHistory()
  const collections = useCollections()
  const environments = useEnvironments()
  const { result, error, isSending, send } = useSendRequest(history.refresh)
  const exportCurl = useCurlExport()
  const runMultiple = useRunMultiple()
  const chainBuilder = useChainBuilder()
  const chainRun = useChain()
  const hydra = useHydra()

  const [importOpen, setImportOpen] = useState(false)
  const [envModalOpen, setEnvModalOpen] = useState(false)
  const [runMultipleOpen, setRunMultipleOpen] = useState(false)
  const [chainModalOpen, setChainModalOpen] = useState(false)
  const [hydraModalOpen, setHydraModalOpen] = useState(false)
  const [hydraPrefill, setHydraPrefill] = useState(null)
  const [saved, setSaved] = useState(null)
  const [saveModal, setSaveModal] = useState({ open: false, mode: 'save' })

  // Send with the active environment so the backend can resolve {{variables}}.
  const handleSend = useCallback(
    (req) => send(req, environments.activeEnvironmentId),
    [send, environments.activeEnvironmentId],
  )

  // Start a loop: snapshot the current request + active environment. The backend
  // runs this snapshot; editing the editor afterwards does not affect it.
  function handleRunMultiple({ runs, delayMs, mode, delayMode, jitterMs, windowMs }) {
    runMultiple.start({
      request: {
        ...toRequestPayload(request),
        environmentId: environments.activeEnvironmentId ?? null,
      },
      runs,
      delayMs,
      mode,
      delayMode,
      jitterMs,
      windowMs,
    })
    // The loop records ONE sanitised History entry when it finishes; the sidebar
    // picks it up when the dialog is closed (see closeRunMultiple).
  }

  function openRunMultiple() {
    runMultiple.reset()
    setRunMultipleOpen(true)
  }

  function closeRunMultiple() {
    if (runMultiple.phase === 'running') runMultiple.stop()
    setRunMultipleOpen(false)
    runMultiple.reset()
    history.refresh()
  }

  // --- request chain --------------------------------------------------

  // Snapshot the current editor request (method/url/headers/body - no cookies,
  // matching how "Save Request" already treats the editor) as a new chain step.
  function handleAddToChain() {
    chainBuilder.addStep(editorRequestFields())
  }

  // Start the chain: each step already carries its own method/url/headers/body:
  // just attach the active environment so {{variables}} resolve the same way
  // Send and Run Multiple do.
  function handleRunChain({ requests, loops, cooldownMs, delayMode, jitterMs, windowMs }) {
    chainRun.start({
      requests: requests.map((step) => ({
        method: step.method,
        url: step.url.trim(),
        headers: step.headers ?? [],
        body: step.body ?? '',
        bodyType: step.bodyType ?? null,
        multipart: step.multipart ?? null,
        environmentId: environments.activeEnvironmentId ?? null,
      })),
      loops,
      cooldownMs,
      delayMode,
      jitterMs,
      windowMs,
    })
  }

  function openChain() {
    chainRun.reset()
    setChainModalOpen(true)
  }

  function closeChain() {
    if (chainRun.phase === 'running') chainRun.stop()
    setChainModalOpen(false)
    chainRun.reset()
  }

  // --- Hydra ----------------------------------------------------------

  function openHydra() {
    hydra.reset()
    setHydraPrefill(hydraPrefillFromRequest(request))
    setHydraModalOpen(true)
  }

  function closeHydra() {
    if (hydra.phase === 'running') hydra.stop()
    setHydraModalOpen(false)
    hydra.reset()
  }

  // --- helpers ------------------------------------------------------

  /**
   * { method, url, headers, body, bodyType, multipart } from the editor - no
   * cookies (never saved).
   */
  function editorRequestFields() {
    const { method, url, headers, body, bodyType, multipart } = toRequestPayload(request)
    return { method, url, headers, body, bodyType, multipart }
  }

  function reportError(err) {
    window.alert(err?.message ?? 'Something went wrong.')
  }

  // If the loaded saved request vanished (its collection was deleted, or it was
  // deleted elsewhere), stop pretending we're editing it.
  useEffect(() => {
    if (!saved) return
    const stillExists = collections.collections.some((c) =>
      c.requests.some((r) => r.id === saved.id),
    )
    if (!stillExists && !collections.loadError) {
      setSaved(null)
    }
  }, [collections.collections, collections.loadError, saved])

  // --- import / history --------------------------------------------

  function handleImported(parsed) {
    loadRequest(parsed)
    setSaved(null) // an imported request is a new, unsaved request
    setImportOpen(false)
  }

  function handleRestoreFromHistory(entry) {
    loadRequest(entry) // no cookies field -> cookies reset to one blank row
    setSaved(null)
  }

  // --- saved requests: open / new --------------------------------

  const handleOpenSavedRequest = useCallback(
    async (id) => {
      try {
        const dto = await getSavedRequest(id)
        loadRequest(dto) // method/url/headers/body; cookies -> blank
        setSaved({ id: dto.id, name: dto.name, collectionId: dto.collectionId })
      } catch (err) {
        reportError(err)
      }
    },
    [loadRequest],
  )

  function handleNewRequest() {
    resetRequest()
    setSaved(null)
  }

  // --- saved requests: save / update / save as -----------------

  async function handleSaveSubmit({ name, collectionId }) {
    try {
      const created = await createSavedRequest({ name, collectionId, ...editorRequestFields() })
      setSaved({ id: created.id, name: created.name, collectionId: created.collectionId })
      setSaveModal({ open: false, mode: 'save' })
      collections.refresh()
    } catch (err) {
      reportError(err)
    }
  }

  async function handleUpdate() {
    if (!saved) return
    try {
      const updated = await updateSavedRequest(saved.id, {
        name: saved.name,
        collectionId: saved.collectionId,
        ...editorRequestFields(),
      })
      setSaved({ id: updated.id, name: updated.name, collectionId: updated.collectionId })
      collections.refresh()
    } catch (err) {
      reportError(err)
    }
  }

  // --- collection actions (from the sidebar) --------------------

  const savedRequestActions = {
    openRequest: handleOpenSavedRequest,

    newCollection: async (name) => {
      try {
        await createCollection(name)
        collections.refresh()
      } catch (err) {
        reportError(err)
      }
    },

    renameCollection: async (id, name) => {
      try {
        await renameCollection(id, name)
        collections.refresh()
      } catch (err) {
        reportError(err)
      }
    },

    deleteCollection: async (id) => {
      try {
        await deleteCollection(id)
        if (saved && saved.collectionId === id) setSaved(null)
        collections.refresh()
      } catch (err) {
        reportError(err)
      }
    },

    renameRequest: async (id, name) => {
      try {
        const full = await getSavedRequest(id)
        await updateSavedRequest(id, {
          name,
          collectionId: full.collectionId,
          method: full.method,
          url: full.url,
          headers: full.headers,
          body: full.body,
          bodyType: full.bodyType,
          multipart: full.multipart,
        })
        if (saved && saved.id === id) setSaved({ ...saved, name })
        collections.refresh()
      } catch (err) {
        reportError(err)
      }
    },

    deleteRequest: async (id) => {
      try {
        await deleteSavedRequest(id)
        if (saved && saved.id === id) setSaved(null)
        collections.refresh()
      } catch (err) {
        reportError(err)
      }
    },
  }

  const defaultCollectionId =
    saved?.collectionId ?? collections.collections[0]?.id ?? null

  if (route === '#/features') {
    return <FeaturesPage />
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
        <Sidebar
          history={history}
          collections={collections}
          onRestore={handleRestoreFromHistory}
          activeSavedRequestId={saved?.id ?? null}
          savedRequestActions={savedRequestActions}
        />
        <main className="app__main">
          <RequestEditor
            request={request}
            actions={actions}
            onSend={handleSend}
            isSending={isSending}
            onImportClick={() => setImportOpen(true)}
            onExport={exportCurl.run}
            exportCopied={exportCurl.copied}
            onRunMultiple={openRunMultiple}
            onAddToChain={handleAddToChain}
            onOpenChain={openChain}
            chainStepCount={chainBuilder.steps.length}
            onOpenHydra={openHydra}
            savedRequestName={saved?.name ?? null}
            onNewRequest={handleNewRequest}
            onSave={() => setSaveModal({ open: true, mode: 'save' })}
            onUpdate={handleUpdate}
            onSaveAs={() => setSaveModal({ open: true, mode: 'saveAs' })}
            environments={environments.environments}
            activeEnvironmentId={environments.activeEnvironmentId}
            environmentsError={environments.loadError}
            onEnvironmentChange={environments.setActiveEnvironmentId}
            onManageEnvironments={() => setEnvModalOpen(true)}
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

      <SaveRequestModal
        open={saveModal.open}
        title={saveModal.mode === 'saveAs' ? 'Save as new request' : 'Save Request'}
        collections={collections.collections}
        initialName={
          saveModal.mode === 'saveAs' && saved ? `${saved.name} Copy` : saved?.name ?? ''
        }
        initialCollectionId={defaultCollectionId}
        onSubmit={handleSaveSubmit}
        onClose={() => setSaveModal({ open: false, mode: 'save' })}
      />

      <EnvironmentModal
        open={envModalOpen}
        onClose={() => setEnvModalOpen(false)}
        environments={environments.environments}
        activeId={environments.activeEnvironmentId}
        onChanged={environments.refresh}
      />

      <RunMultipleModal
        open={runMultipleOpen}
        onClose={closeRunMultiple}
        run={runMultiple}
        onRun={handleRunMultiple}
      />

      <ChainModal
        open={chainModalOpen}
        onClose={closeChain}
        chain={chainBuilder}
        run={chainRun}
        onRun={handleRunChain}
      />

      <HydraModal open={hydraModalOpen} onClose={closeHydra} hydra={hydra} prefill={hydraPrefill} />
    </div>
  )
}
