import { useState, useEffect } from 'react';
import type { TrackerState } from '../components/AgentExecutionTracker';

type Subscriber = (state: TrackerState) => void;

class TrackerStore {
  private state: TrackerState = {
    currentStage: 'INPUT_RECEIVED',
    completedStages: [],
    failedStage: null,
    statusMessage: 'System idle. Waiting for input.',
    progressPercentage: 0
  };
  private subscribers: Set<Subscriber> = new Set();

  getState() {
    return this.state;
  }

  updateState(partial: Partial<TrackerState>) {
    this.state = { ...this.state, ...partial };
    this.notify();
  }

  setStageActive(stage: string, message: string) {
    const allStages = [
      'INPUT_RECEIVED', 'CLASSIFYING', 'KNOWLEDGE_EXTRACTION', 'BUILD_GRAPH',
      'PROCESS_INTELLIGENCE', 'BPMN_MODELLING', 'PROCESS_REVIEW', 'FINAL_OUTPUT'
    ];
    const idx = allStages.indexOf(stage);
    this.state = {
      ...this.state,
      currentStage: stage,
      completedStages: allStages.slice(0, idx),
      failedStage: null,
      statusMessage: message,
      progressPercentage: Math.round((idx / (allStages.length - 1)) * 100)
    };
    this.notify();
  }
  
  setStageComplete(stage: string, message: string) {
    const allStages = [
      'INPUT_RECEIVED', 'CLASSIFYING', 'KNOWLEDGE_EXTRACTION', 'BUILD_GRAPH',
      'PROCESS_INTELLIGENCE', 'BPMN_MODELLING', 'PROCESS_REVIEW', 'FINAL_OUTPUT'
    ];
    const idx = allStages.indexOf(stage);
    // When completed, the active stage technically moves to the next one, but waiting.
    const nextStage = allStages[Math.min(idx + 1, allStages.length - 1)];
    this.state = {
      ...this.state,
      currentStage: nextStage,
      completedStages: allStages.slice(0, idx + 1),
      failedStage: null,
      statusMessage: message,
      progressPercentage: Math.round(((idx + 1) / allStages.length) * 100)
    };
    this.notify();
  }

  subscribe(callback: Subscriber) {
    this.subscribers.add(callback);
    return () => {
      this.subscribers.delete(callback);
    };
  }

  private notify() {
    this.subscribers.forEach(cb => cb(this.state));
  }
}

export const trackerStore = new TrackerStore();

export function useTrackerStore() {
  const [state, setState] = useState(trackerStore.getState());
  useEffect(() => {
    return trackerStore.subscribe(setState);
  }, []);
  return state;
}

