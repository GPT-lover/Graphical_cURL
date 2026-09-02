import HistoryPanel from './HistoryPanel.jsx'

/**
 * Left rail: the functional History panel plus a Saved Requests placeholder
 * (a later phase).
 *
 * Props: history = the object returned by useHistory(); onRestore(entry).
 */
export default function Sidebar({ history, onRestore }) {
  return (
    <aside className="sidebar">
      <HistoryPanel
        entries={history.entries}
        loadError={history.loadError}
        onRestore={onRestore}
        onDelete={history.remove}
        onClear={history.clear}
      />

      <div className="sidebar__section">
        <h2 className="sidebar__title">Saved Requests</h2>
        <p className="sidebar__empty">Requests you save will show up here (later phase).</p>
      </div>
    </aside>
  )
}
