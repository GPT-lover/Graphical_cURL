import HistoryPanel from './HistoryPanel.jsx'
import SavedRequestsPanel from './SavedRequestsPanel.jsx'

/**
 * Left rail: the functional History panel (Phase 7) and the Saved Requests tree
 * (Phase 8). Both are independent.
 *
 * Props:
 *   history        - object from useHistory()
 *   collections    - object from useCollections()
 *   onRestore(entry)          - load a history entry into the editor
 *   activeSavedRequestId      - highlight the currently loaded saved request
 *   savedRequestActions       - { openRequest, newCollection, renameCollection,
 *                                 deleteCollection, renameRequest, deleteRequest }
 */
export default function Sidebar({
  history,
  collections,
  onRestore,
  activeSavedRequestId,
  savedRequestActions,
}) {
  return (
    <aside className="sidebar">
      <HistoryPanel
        entries={history.entries}
        loadError={history.loadError}
        onRestore={onRestore}
        onDelete={history.remove}
        onClear={history.clear}
      />

      <SavedRequestsPanel
        collections={collections.collections}
        loadError={collections.loadError}
        activeSavedRequestId={activeSavedRequestId}
        onNewCollection={savedRequestActions.newCollection}
        onRenameCollection={savedRequestActions.renameCollection}
        onDeleteCollection={savedRequestActions.deleteCollection}
        onOpenRequest={savedRequestActions.openRequest}
        onRenameRequest={savedRequestActions.renameRequest}
        onDeleteRequest={savedRequestActions.deleteRequest}
      />
    </aside>
  )
}
