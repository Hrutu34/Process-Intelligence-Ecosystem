import type {
  GlobalKnowledgeStats,
  KnowledgeActorItem,
  KnowledgeActivityItem,
  KnowledgeDecisionItem
} from './types';
import {
  GLOBAL_KNOWLEDGE_ACTORS,
  GLOBAL_KNOWLEDGE_ACTIVITIES,
  GLOBAL_KNOWLEDGE_DECISIONS
} from './mockData';
import { processService } from './processService';

class KnowledgeService {
  public getGlobalStats(): GlobalKnowledgeStats {
    const processes = processService.getProcesses();
    let totalActors = 0;
    let totalActivities = 0;
    let totalDecisions = 0;
    let totalEvents = 0;
    let totalSystems = 0;

    processes.forEach((p) => {
      totalActors += p.knowledge?.actors?.length || 0;
      totalActivities += p.knowledge?.activities?.length || 0;
      totalDecisions += p.knowledge?.gateways?.length || 0;
      totalEvents += p.knowledge?.events?.length || 0;
      totalSystems += p.knowledge?.systems?.length || 0;
    });

    return {
      actorsCount: totalActors || 18,
      activitiesCount: totalActivities || 32,
      decisionsCount: totalDecisions || 9,
      eventsCount: totalEvents || 8,
      systemsCount: totalSystems || 7,
    };
  }

  public getActors(): KnowledgeActorItem[] {
    return GLOBAL_KNOWLEDGE_ACTORS;
  }

  public getActivities(): KnowledgeActivityItem[] {
    return GLOBAL_KNOWLEDGE_ACTIVITIES;
  }

  public getDecisions(): KnowledgeDecisionItem[] {
    return GLOBAL_KNOWLEDGE_DECISIONS;
  }
}

export const knowledgeService = new KnowledgeService();
