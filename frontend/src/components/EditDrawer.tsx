import { useEffect, useState } from "react";

interface Props {
  open: boolean;
  onToggle: () => void;
  onClose: () => void;
  sourceText: string;
  onRegenerate: (text: string) => void | Promise<void>;
  regenerating?: boolean;
}

export default function EditDrawer({ open, onToggle, onClose, sourceText, onRegenerate, regenerating }: Props) {
  const [draft, setDraft] = useState<string>(sourceText || "");

  useEffect(() => { setDraft(sourceText || ""); }, [sourceText]);

  function handleRegenerateClick() {
    if (!draft.trim()) return;
    onRegenerate(draft.trim());
  }

  return (
    <>
      <button className={"edge-tab" + (open ? " open" : "")} title="Edit source text" onClick={onToggle}>
        <svg
          style={{ transform: open ? "rotate(180deg)" : "none", transition: "transform .25s ease" }}
          viewBox="0 0 24 24" width="16" height="16" fill="none"
        >
          <path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      <div className={"edit-backdrop" + (open ? " open" : "")} onClick={onClose}></div>
      <aside className={"edit-drawer" + (open ? " open" : "")}>
        <div className="edit-drawer-head">
          <h3>Edit source text</h3>
          <button className="icon-btn" onClick={onClose}>✕</button>
        </div>
        <p className="edit-drawer-sub">
          Make changes below and regenerate the BPMN without leaving this page. Manual edits made in the canvas will be replaced.
        </p>
        <textarea value={draft} onChange={(e) => setDraft(e.target.value)} />
        <button className="primary-btn" onClick={handleRegenerateClick} disabled={regenerating}>
          {regenerating ? "Regenerating…" : "Regenerate Diagram"}
        </button>
      </aside>
    </>
  );
}
