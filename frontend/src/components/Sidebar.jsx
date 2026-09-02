/**
 * Left rail with two sections: History and Saved Requests.
 * Placeholders for now - History is filled in Phase 4, Saved in Phase 5.
 * Clicking an entry will call the hook's loadRequest() to populate the editor.
 */
export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar__section">
        <h2 className="sidebar__title">History</h2>
        <p className="sidebar__empty">
          Sent requests will show up here (Phase 4).
        </p>
      </div>

      <div className="sidebar__section">
        <h2 className="sidebar__title">Saved Requests</h2>
        <p className="sidebar__empty">
          Requests you save will show up here (Phase 5).
        </p>
      </div>
    </aside>
  )
}
