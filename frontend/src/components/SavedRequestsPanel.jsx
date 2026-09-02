import { useState } from 'react'

/**
 * The Saved Requests section of the sidebar: a tree of collections, each holding
 * saved-request rows.
 *
 * Rename / delete use plain window.prompt / window.confirm (kept simple on
 * purpose). The actual API calls + refresh happen in App via these callbacks:
 *
 *   onNewCollection(name)
 *   onRenameCollection(id, newName)
 *   onDeleteCollection(id)
 *   onOpenRequest(id)                 - load it into the editor
 *   onRenameRequest(id, newName)
 *   onDeleteRequest(id)
 *
 * Props also: collections, loadError, activeSavedRequestId (to highlight).
 */
export default function SavedRequestsPanel({
  collections,
  loadError,
  activeSavedRequestId,
  onNewCollection,
  onRenameCollection,
  onDeleteCollection,
  onOpenRequest,
  onRenameRequest,
  onDeleteRequest,
}) {
  const [collapsed, setCollapsed] = useState(() => new Set())

  function toggle(id) {
    setCollapsed((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  function handleNewCollection() {
    const name = window.prompt('Collection name:')
    if (name && name.trim()) onNewCollection(name.trim())
  }

  function handleRenameCollection(collection) {
    const name = window.prompt('Collection name:', collection.name)
    if (name && name.trim() && name.trim() !== collection.name) {
      onRenameCollection(collection.id, name.trim())
    }
  }

  function handleDeleteCollection(collection) {
    const n = collection.requests.length
    const suffix = n === 0 ? '' : ` and its ${n} saved request${n === 1 ? '' : 's'}`
    if (window.confirm(`Delete "${collection.name}"${suffix}? This cannot be undone.`)) {
      onDeleteCollection(collection.id)
    }
  }

  function handleRenameRequest(req) {
    const name = window.prompt('Request name:', req.name)
    if (name && name.trim() && name.trim() !== req.name) {
      onRenameRequest(req.id, name.trim())
    }
  }

  function handleDeleteRequest(req) {
    if (window.confirm(`Delete saved request "${req.name}"?`)) {
      onDeleteRequest(req.id)
    }
  }

  return (
    <div className="sidebar__section">
      <div className="history__header">
        <h2 className="sidebar__title">Saved Requests</h2>
        <button type="button" className="btn btn--tiny" onClick={handleNewCollection}>
          + New Collection
        </button>
      </div>

      {loadError && <p className="sidebar__empty">Saved requests could not be loaded.</p>}

      {!loadError && collections.length === 0 && (
        <p className="sidebar__empty">No collections yet.</p>
      )}

      <ul className="tree">
        {collections.map((collection) => {
          const isCollapsed = collapsed.has(collection.id)
          return (
            <li key={collection.id} className="tree-collection">
              <div className="tree-collection__row">
                <button
                  type="button"
                  className="tree-collection__toggle"
                  onClick={() => toggle(collection.id)}
                  aria-expanded={!isCollapsed}
                >
                  <span className="tree-collection__caret">{isCollapsed ? '▸' : '▾'}</span>
                  <span className="tree-collection__name">{collection.name}</span>
                  <span className="tree-collection__count">{collection.requests.length}</span>
                </button>
                <span className="tree-actions">
                  <button
                    type="button"
                    className="btn btn--icon"
                    title="Rename collection"
                    aria-label="Rename collection"
                    onClick={() => handleRenameCollection(collection)}
                  >
                    &#9998;
                  </button>
                  <button
                    type="button"
                    className="btn btn--icon"
                    title="Delete collection"
                    aria-label="Delete collection"
                    onClick={() => handleDeleteCollection(collection)}
                  >
                    &times;
                  </button>
                </span>
              </div>

              {!isCollapsed && (
                <ul className="tree-requests">
                  {collection.requests.length === 0 && (
                    <li className="tree-requests__empty">empty</li>
                  )}
                  {collection.requests.map((req) => (
                    <li
                      key={req.id}
                      className={
                        'tree-request' +
                        (req.id === activeSavedRequestId ? ' tree-request--active' : '')
                      }
                    >
                      <button
                        type="button"
                        className="tree-request__open"
                        onClick={() => onOpenRequest(req.id)}
                        title={req.name}
                      >
                        {req.name}
                      </button>
                      <span className="tree-actions">
                        <button
                          type="button"
                          className="btn btn--icon"
                          title="Rename request"
                          aria-label="Rename request"
                          onClick={() => handleRenameRequest(req)}
                        >
                          &#9998;
                        </button>
                        <button
                          type="button"
                          className="btn btn--icon"
                          title="Delete request"
                          aria-label="Delete request"
                          onClick={() => handleDeleteRequest(req)}
                        >
                          &times;
                        </button>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
