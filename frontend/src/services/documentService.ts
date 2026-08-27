import type { DocumentItem } from './types';
import { INITIAL_DOCUMENTS } from './mockData';

const STORAGE_KEY = 'pie_documents_db';

class DocumentService {
  private documents: DocumentItem[] = [];

  constructor() {
    this.load();
  }

  private load() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        this.documents = JSON.parse(stored);
      } else {
        this.documents = [...INITIAL_DOCUMENTS];
        this.save();
      }
    } catch {
      this.documents = [...INITIAL_DOCUMENTS];
    }
  }

  private save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.documents));
    } catch (e) {
      console.warn('Failed to persist documents to localStorage', e);
    }
  }

  public getDocuments(): DocumentItem[] {
    return [...this.documents];
  }

  public getDocumentById(id: string): DocumentItem | undefined {
    return this.documents.find((d) => d.id === id);
  }

  public addDocument(doc: DocumentItem): void {
    this.documents.unshift(doc);
    this.save();
  }

  public deleteDocument(id: string): void {
    this.documents = this.documents.filter((d) => d.id !== id);
    this.save();
  }

  public searchDocuments(
    query?: string,
    typeFilter?: string,
    statusFilter?: string
  ): DocumentItem[] {
    return this.documents.filter((doc) => {
      if (query && query.trim()) {
        const q = query.toLowerCase();
        const matchName = doc.name.toLowerCase().includes(q);
        const matchSnippet = doc.fileSnippet?.toLowerCase().includes(q) || false;
        const matchProcess = doc.linkedProcessName?.toLowerCase().includes(q) || false;
        if (!matchName && !matchSnippet && !matchProcess) return false;
      }

      if (typeFilter && typeFilter !== 'ALL') {
        if (doc.type.toUpperCase() !== typeFilter.toUpperCase()) return false;
      }

      if (statusFilter && statusFilter !== 'ALL') {
        if (doc.status.toUpperCase() !== statusFilter.toUpperCase()) return false;
      }

      return true;
    });
  }
}

export const documentService = new DocumentService();
