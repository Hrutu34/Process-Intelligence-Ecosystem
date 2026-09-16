import { timeAgo } from "../services/historyStore";
import type { HistoryEntry } from "../services/historyStore";

interface Props {
  open: boolean;
  onClose: () => void;
  entries: HistoryEntry[];
  onClear: () => void;
  onDeleteEntry: (id: string) => void;
  onReopenEntry: (entry: HistoryEntry) => void;
}

export default function HistoryPanel({ open, onClose, entries, onClear, onDeleteEntry, onReopenEntry }: Props) {
  return (
    <>
      <div className={"history-backdrop" + (open ? " open" : "")} onClick={onClose}></div>
      <aside className={"history-panel" + (open ? " open" : "")}>
        <div className="history-panel-head">
          <h3>History</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <div className="history-panel-actions">
          <button className="text-btn" onClick={onClear}>Clear all</button>
        </div>
        <div className="history-list">
          {entries.length === 0 && (
            <div className="history-empty">
              No processes yet. Ingested documents and generated BPMN diagrams will appear here.
            </div>
          )}
          {entries.map((entry) => (
            <div key={entry.id} className="history-item" onClick={() => onReopenEntry(entry)}>
              <button
                className="history-delete"
                title="Delete"
                onClick={(e) => { e.stopPropagation(); onDeleteEntry(entry.id); }}
              >✕</button>
              <div className="history-item-top">
                <span className={"history-type-pill history-type-pill--" + entry.type}>
                  {entry.type === "diagram" ? "Diagram" : "Summary"}
                </span>
                <span className="history-time">{timeAgo(entry.createdAt)}</span>
              </div>
              <p className="history-title">{entry.title}</p>
              <p className="history-preview">{entry.preview}</p>
            </div>
          ))}
        </div>
      </aside>
    </>
  );
}
