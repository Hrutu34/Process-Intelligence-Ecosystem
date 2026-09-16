import type { ProcessKnowledgeDTO } from "../../../backend/src/main/java/com/pie/shared/types/dto";

export type HistoryType = "diagram" | "summary";

export interface HistoryEntry {
  id: string;
  type: HistoryType;
  title: string;
  preview: string;
  createdAt: number;
  bpmnXml?: string;
  sourceText?: string;
  knowledge?: ProcessKnowledgeDTO | null;
}

const KEY = "pie_history_v1";

function safeParse(): HistoryEntry[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch { return []; }
}

function safeWrite(entries: HistoryEntry[]) {
  try { localStorage.setItem(KEY, JSON.stringify(entries)); } catch {}
}

export const historyStore = {
  list(): HistoryEntry[] {
    return safeParse().sort((a, b) => b.createdAt - a.createdAt);
  },
  add(entry: Omit<HistoryEntry, "id" | "createdAt"> & { id?: string }): HistoryEntry {
    const entries = safeParse();
    const record: HistoryEntry = {
      id: entry.id || `h_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
      createdAt: Date.now(),
      type: entry.type,
      title: entry.title,
      preview: entry.preview,
      bpmnXml: entry.bpmnXml,
      sourceText: entry.sourceText,
      knowledge: entry.knowledge,
    };
    entries.unshift(record);
    safeWrite(entries.slice(0, 40));
    return record;
  },
  remove(id: string) {
    safeWrite(safeParse().filter((e) => e.id !== id));
  },
  clear() {
    safeWrite([]);
  },
  get(id: string): HistoryEntry | undefined {
    return safeParse().find((e) => e.id === id);
  },
};

export function timeAgo(ts: number): string {
  const sec = Math.max(1, Math.round((Date.now() - ts) / 1000));
  if (sec < 60) return `${sec}s ago`;
  const min = Math.round(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr}h ago`;
  const d = Math.round(hr / 24);
  return `${d}d ago`;
}
