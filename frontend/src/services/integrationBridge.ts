/**
 * ProcessIQ External Integration Bridge
 * Enables seamless integration with upstream projects (e.g. Text-to-BPMN generators,
 * LLM agent pipelines, parent iframes, or external web services).
 */

import type { EventLogEntry, SimulationParameters, SimulationResult } from './simulationTypes';

export interface BridgeHandlers {
  onLoadBpmn?: (xml: string, name?: string, customParams?: SimulationParameters | null) => void;
  onSetParameters?: (params: SimulationParameters) => void;
  onRunSimulation?: () => void;
  getResults?: () => SimulationResult | null;
  exportCsv?: () => void;
}

declare global {
  interface Window {
    ProcessIQ_Bridge?: IntegrationBridge;
    ProcessIQ?: {
      loadBpmn?: (xml: string, name?: string, customParams?: SimulationParameters | null) => void;
      setParameters?: (params: SimulationParameters) => void;
      runSimulation?: () => void;
      getResults?: () => SimulationResult | null;
      exportCsv?: () => void;
    };
  }
}

export class IntegrationBridge {
  private handlers: BridgeHandlers;

  constructor(handlers: BridgeHandlers = {}) {
    this.handlers = handlers;
    this.init();
  }

  init() {
    if (typeof window === 'undefined') return;

    window.addEventListener('message', (event) => {
      try {
        const data = event.data;
        if (!data || typeof data !== 'object') return;

        switch (data.type) {
          case 'PROCESS_IQ_LOAD_BPMN':
          case 'LOAD_BPMN_XML':
            if (data.xml && this.handlers.onLoadBpmn) {
              this.handlers.onLoadBpmn(data.xml, data.name || 'External BPMN', data.parameters);
            }
            break;

          case 'PROCESS_IQ_SET_PARAMETERS':
          case 'SET_PARAMETERS':
            if (data.parameters && this.handlers.onSetParameters) {
              this.handlers.onSetParameters(data.parameters);
            }
            break;

          case 'PROCESS_IQ_RUN_SIMULATION':
          case 'RUN_SIMULATION':
            if (this.handlers.onRunSimulation) {
              this.handlers.onRunSimulation();
            }
            break;

          default:
            break;
        }
      } catch (err) {
        console.warn('ProcessIQ postMessage handling error:', err);
      }
    });

    window.ProcessIQ = {
      loadBpmn: (xml, name = 'External Process', customParams = null) => {
        if (this.handlers.onLoadBpmn) {
          return this.handlers.onLoadBpmn(xml, name, customParams);
        }
      },
      setParameters: (params) => {
        if (this.handlers.onSetParameters) {
          return this.handlers.onSetParameters(params);
        }
      },
      runSimulation: () => {
        if (this.handlers.onRunSimulation) {
          return this.handlers.onRunSimulation();
        }
      },
      getResults: () => {
        if (this.handlers.getResults) {
          return this.handlers.getResults();
        }
        return null;
      },
      exportCsv: () => {
        if (this.handlers.exportCsv) {
          return this.handlers.exportCsv();
        }
      }
    };
  }

  notifySimulationComplete(results: SimulationResult) {
    if (typeof window === 'undefined') return;

    if (window.parent && window.parent !== window) {
      window.parent.postMessage(
        {
          type: 'PROCESS_IQ_SIMULATION_RESULT',
          summary: results.summary,
          activityBottlenecks: results.activityBottlenecks
        },
        '*'
      );
    }
  }
}

/**
 * Event Log Exporter: formats simulation log into standard CSV
 * Compatible with Celonis, Disco, ProM, and PM4Py
 */
export function exportEventLogToCsv(eventLog: EventLogEntry[], filename = 'process_event_log.csv') {
  if (!eventLog || eventLog.length === 0) {
    alert('No simulation event log available to export. Please run a simulation first.');
    return;
  }

  const headers = [
    'Case ID',
    'Activity ID',
    'Activity Name',
    'Queue Entry Time (min)',
    'Start Time (min)',
    'Complete Time (min)',
    'Queue Wait Time (min)',
    'Duration (min)',
    'Cost ($)',
    'Resource Allocated'
  ];

  const rows = eventLog.map((e) => [
    e.caseId,
    e.activityId,
    `"${(e.activityName || '').replace(/"/g, '""')}"`,
    e.queueEntryTime,
    e.startTime,
    e.completeTime,
    e.waitTimeMinutes,
    e.durationMinutes,
    e.cost,
    `"${(e.resource || '').replace(/"/g, '""')}"`
  ]);

  const csvContent =
    'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');

  const encodedUri = encodeURI(csvContent);
  const link = document.createElement('a');
  link.setAttribute('href', encodedUri);
  link.setAttribute('download', filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
