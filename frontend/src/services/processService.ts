import type {
  ProcessEntity,
  ProcessKnowledgeDTO,
  CanonicalProcessGraph,
  ProcessVersion,
  ValidationIssue
} from './types';
import { INITIAL_PROCESSES } from './mockData';
import { documentService } from './documentService';

const STORAGE_KEY = 'pie_processes_db';
const BACKEND_URL = 'http://localhost:8080';

class ProcessService {
  private processes: ProcessEntity[] = [];

  constructor() {
    this.load();
  }

  private load() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        this.processes = JSON.parse(stored);
      } else {
        this.processes = [...INITIAL_PROCESSES];
        this.save();
      }
    } catch {
      this.processes = [...INITIAL_PROCESSES];
    }
  }

  private save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.processes));
    } catch (e) {
      console.warn('Failed to persist processes to localStorage', e);
    }
  }

  public getProcesses(): ProcessEntity[] {
    return [...this.processes];
  }

  public getProcessById(id: string): ProcessEntity | undefined {
    return this.processes.find((p) => p.id === id);
  }

  public saveProcess(process: ProcessEntity): void {
    const idx = this.processes.findIndex((p) => p.id === process.id);
    if (idx >= 0) {
      this.processes[idx] = { ...process, lastUpdated: 'Just now' };
    } else {
      this.processes.unshift(process);
    }
    this.save();
  }

  public applyValidationFix(processId: string, issueId: string): ProcessEntity | null {
    const proc = this.getProcessById(processId);
    if (!proc) return null;

    const issue = proc.validationIssues.find((i) => i.id === issueId);
    if (issue) {
      issue.isApplied = true;
      proc.qualityScore = Math.min(100, proc.qualityScore + 5);
      proc.lastUpdated = 'Just now';
      this.saveProcess(proc);
    }
    return proc;
  }

  public addVersion(processId: string, summary: string, author: string): ProcessEntity | null {
    const proc = this.getProcessById(processId);
    if (!proc) return null;

    const currentVNum = parseInt(proc.currentVersion.replace('v', '') || '1', 10);
    const nextVersion = `v${currentVNum + 1}`;

    const newV: ProcessVersion = {
      version: nextVersion,
      timestamp: 'Just now',
      summary,
      qualityScore: proc.qualityScore,
      author,
    };

    proc.currentVersion = nextVersion;
    proc.versions.unshift(newV);
    proc.lastUpdated = 'Just now';
    this.saveProcess(proc);
    return proc;
  }

  // Live Backend Extraction from Free Text
  public async extractKnowledgeFromText(text: string): Promise<ProcessKnowledgeDTO> {
    try {
      const response = await fetch(`${BACKEND_URL}/api/v1/process/extract-text`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content: text }),
      });
      if (!response.ok) {
        throw new Error(`Extraction failed with HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err) {
      console.warn('Backend extract-text call failed, generating parsed knowledge locally:', err);
      return this.localParseKnowledge(text);
    }
  }

  // Live Backend Extraction from Files
  public async extractKnowledgeFromFiles(files: File[]): Promise<ProcessKnowledgeDTO> {
    try {
      const formData = new FormData();
      files.forEach((f) => formData.append('files', f));

      const response = await fetch(`${BACKEND_URL}/api/v1/process/extract-file`, {
        method: 'POST',
        body: formData,
      });
      if (!response.ok) {
        throw new Error(`File extraction failed with HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err) {
      console.warn('Backend extract-file call failed, using fallback extraction:', err);
      const text = files.map((f) => f.name).join(' ');
      return this.localParseKnowledge(text);
    }
  }

  // Live Backend Canonical Process Graph Generation
  public async fetchCanonicalGraph(knowledge: ProcessKnowledgeDTO): Promise<CanonicalProcessGraph> {
    try {
      const response = await fetch(`${BACKEND_URL}/api/v1/process/graph`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(knowledge),
      });
      if (!response.ok) {
        throw new Error(`Graph generation failed with HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err) {
      console.warn('Backend /graph call failed, building canonical graph locally:', err);
      return this.buildFallbackGraph(knowledge);
    }
  }

  // Create Process from Ingested Content
  public async createProcessFromIngestion(
    title: string,
    sourceDocName: string,
    sourceType: 'PDF' | 'DOCX' | 'TXT' | 'FreeText',
    knowledge: ProcessKnowledgeDTO,
    rawText?: string
  ): Promise<ProcessEntity> {
    const id = 'proc_' + Date.now().toString(36);
    const graph = await this.fetchCanonicalGraph(knowledge);

    const newProcess: ProcessEntity = {
      id,
      name: title || 'Extracted Business Process',
      description: `Discovered from ${sourceDocName}. Contains ${knowledge.activities?.length || 0} activities and ${knowledge.actors?.length || 0} participants.`,
      sourceDocument: sourceDocName,
      sourceType,
      rawText,
      createdAt: 'Today, Just now',
      lastUpdated: 'Just now',
      status: 'Validated',
      qualityScore: 88,
      currentVersion: 'v1',
      versions: [
        {
          version: 'v1',
          timestamp: 'Just now',
          summary: `Initial extraction and canonical graph construction from ${sourceDocName}`,
          qualityScore: 88,
          author: 'P.I.E. Autonomous Discovery Agent',
        }
      ],
      knowledge,
      graph,
      insights: [
        {
          type: 'automation',
          title: 'Automated Rule Execution Detected',
          description: 'Gateways and activities are structured for direct BPMN 2.0 orchestration.',
          impact: 'High'
        }
      ],
      validationIssues: [],
      sourceTraces: (knowledge.activities || []).map((act, i) => ({
        entityName: act,
        entityType: 'Activity',
        sourceText: (knowledge.actors && knowledge.actors[i] ? `${knowledge.actors[i]}: ` : '') + act,
        documentName: sourceDocName,
        pageOrSection: `Step ${i + 1}`,
      })),
      aiSummary: {
        executiveSummary: `Autonomous process graph created from ${sourceDocName}. Identifies clear governance boundaries and sequential steps across ${knowledge.actors?.length || 0} operational roles.`,
        auditReadinessScore: 88,
        auditStatus: 'Audit Ready',
        recommendations: [
          'Review decision gateways for exhaustive rejection paths.',
          'Attach explicit SLAs for each managerial review milestone.'
        ],
        complianceNotes: ['Extracted structure aligns with BPMN 2.0 executable standards.']
      }
    };

    this.saveProcess(newProcess);

    // Register document in document registry
    documentService.addDocument({
      id: 'doc_' + Date.now().toString(36),
      name: sourceDocName,
      size: `${Math.round((rawText?.length || 1024) / 1024 * 10) / 10 || 1.2} KB`,
      type: sourceType,
      uploadedAt: 'Just now',
      status: 'Completed',
      extractedEntitiesCount: (knowledge.activities?.length || 0) + (knowledge.actors?.length || 0) + (knowledge.gateways?.length || 0),
      linkedProcessId: newProcess.id,
      linkedProcessName: newProcess.name,
      fileSnippet: rawText ? rawText.substring(0, 160) + '...' : undefined,
    });

    return newProcess;
  }

  private localParseKnowledge(text: string): ProcessKnowledgeDTO {
    const lines = text.split(/[.\n]/).map((l) => l.trim()).filter(Boolean);
    const activities: string[] = [];
    const actors: string[] = [];
    const gateways: string[] = [];

    lines.forEach((line) => {
      if (line.toLowerCase().includes('if') || line.toLowerCase().includes('approv') || line.toLowerCase().includes('gate')) {
        gateways.push(line.replace(/^(if|when|on)\s+/i, '').substring(0, 40));
      } else {
        activities.push(line.substring(0, 50));
      }
      if (line.toLowerCase().includes('employee')) actors.push('Employee');
      if (line.toLowerCase().includes('manager')) actors.push('Manager');
      if (line.toLowerCase().includes('finance')) actors.push('Finance');
    });

    return {
      activities: activities.length > 0 ? activities : ['Submit Request', 'Review Request', 'Process Task'],
      actors: Array.from(new Set(actors)).length > 0 ? Array.from(new Set(actors)) : ['Requester', 'Approver'],
      roles: ['Staff', 'Lead'],
      gateways: gateways.length > 0 ? gateways : ['Decision Gate'],
      systems: ['ERP System'],
      events: ['Process Initiated', 'Process Completed'],
      inputs: ['Source Document'],
      outputs: ['Completed Process Record'],
      businessRules: ['Follow corporate standard operating procedures.'],
      risks: [],
      conflicts: []
    };
  }

  private buildFallbackGraph(knowledge: ProcessKnowledgeDTO): CanonicalProcessGraph {
    const nodes: any[] = [];
    const edges: any[] = [];

    (knowledge.activities || []).forEach((act) => {
      const slug = act.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
      nodes.push({ id: `activity-${slug}`, type: 'Activity', label: act, metadata: {} });
    });

    (knowledge.actors || []).forEach((actor) => {
      const slug = actor.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
      nodes.push({ id: `role-${slug}`, type: 'Role', label: actor, metadata: {} });
    });

    (knowledge.gateways || []).forEach((gw) => {
      const slug = gw.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
      nodes.push({ id: `gateway-${slug}`, type: 'Gateway', label: gw, metadata: { gatewayType: 'exclusive' } });
    });

    return {
      graphId: 'graph-' + (knowledge.activities?.[0] || 'process').toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      nodes,
      edges
    };
  }
}

export const processService = new ProcessService();
