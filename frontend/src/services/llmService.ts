/**
 * Process Intelligence LLM Service
 * Inters operational simulation parameters and synthesizes dynamic contextual charts
 * Supports Ollama (local), OpenAI (GPT-4o/mini), and Google Gemini.
 */

import type {
  ProcessTopology,
  SimulationParameters,
  SimulationResult,
  DynamicChartSpec,
  TaskParameter,
  DurationDistribution
} from './simulationTypes';

export interface LlmProviderConfig {
  provider?: 'ollama' | 'gemini' | 'openai';
  ollamaModel?: string;
  ollamaUrl?: string;
  apiKey?: string;
  customEndpoint?: string;
  userContext?: string;
}

export async function inferParametersWithLLM(
  parsedBpmn: ProcessTopology,
  providerConfig: LlmProviderConfig = {},
  userContextText?: string
): Promise<SimulationParameters> {
  const provider = providerConfig.provider || 'ollama';
  const ollamaModel = providerConfig.ollamaModel || 'llama3.2:3b';
  const ollamaUrl = providerConfig.ollamaUrl || 'http://localhost:11434';
  const apiKey = providerConfig.apiKey || '';
  const customEndpoint = providerConfig.customEndpoint || '';
  const userContext = userContextText || providerConfig.userContext || '';

  const taskList = parsedBpmn.tasks.map((t) => ({ id: t.id, name: t.name }));
  const gatewayList = (parsedBpmn.gateways || [])
    .filter((g) => g.type === 'exclusiveGateway')
    .map((g) => ({
      id: g.id,
      name: g.name,
      branches: (g.outgoing || []).map((flowId) => {
        const flow = parsedBpmn.sequenceFlows[flowId];
        return { flowId, name: flow ? flow.name : flowId };
      })
    }));

  const prompt = `You are a Principal Process Mining & Operations Research Engineer.
Analyze the following BPMN 2.0 process model and user context document.
Produce realistic, quantitative discrete-event simulation parameters.

PROCESS MODEL NAME: "${parsedBpmn.name || 'Enterprise Process'}"
TASKS:
${JSON.stringify(taskList, null, 2)}

EXCLUSIVE (XOR) GATEWAYS:
${JSON.stringify(gatewayList, null, 2)}

USER DOCUMENT CONTEXT / SOP / INSTRUCTIONS:
"${userContext || 'Standard enterprise operational workflow with finite human and automated resources.'}"

CONSTRAINTS:
1. "distribution" MUST strictly be either "normal" (human manual work) or "constant" (machine/automated task).
2. For "constant", set "stdDev" to 0.
3. For "normal", set "stdDev" to approximately 20-30% of "meanDuration".
4. "resourceCapacity": worker headcount between 1 and 10.
5. "hourlyLaborRate": between $15 and $120.
6. For each exclusiveGateway branch, set realistic probabilities that sum to 1.0.

RETURN ONLY VALID RAW JSON (no markdown backticks, no explanatory chat):
{
  "domain": "<industry and process domain>",
  "entityName": "<plural entities e.g. Claims, Cars, Support Tickets, Orders>",
  "entitySingular": "<singular entity e.g. Claim, Car, Ticket, Order>",
  "domainSummary": "<2-sentence process overview>",
  "keyBottleneckPrediction": "<name of predicted bottleneck activity>",
  "global": {
    "caseCount": 400,
    "arrivalMeanMinutes": 15,
    "slaThresholdHours": 12,
    "caseCountLabel": "<e.g. Total Claims to Process>",
    "arrivalLabel": "<e.g. Claim Intake Frequency>",
    "slaLabel": "<e.g. Claim Settlement SLA Target>"
  },
  "tasks": {
    ${taskList
      .map(
        (t) =>
          `"${t.id}": { "meanDuration": 30, "stdDev": 8, "distribution": "normal", "resourceCapacity": 2, "hourlyLaborRate": 35, "costPerExecution": 15, "reworkRate": 0.05, "rationale": "Reasoning" }`
      )
      .join(',\n    ')}
  },
  "gateways": {
    ${gatewayList
      .map(
        (g) =>
          `"${g.id}": { "branches": [ ${g.branches
            .map(
              (b) =>
                `{"flowId": "${b.flowId}", "name": "${b.name}", "probability": 0.5, "ruleDescription": "Condition"}`
            )
            .join(', ')} ] }`
      )
      .join(',\n    ')}
  }
}`;

  try {
    let rawJsonText = '';

    if (provider === 'ollama') {
      const primaryUrl = `${ollamaUrl}/api/generate`;
      let res: Response;
      try {
        res = await fetch(primaryUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: ollamaModel,
            prompt,
            format: 'json',
            stream: false,
            options: { temperature: 0.2 }
          })
        });
      } catch {
        res = await fetch('http://localhost:11434/api/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: ollamaModel,
            prompt,
            format: 'json',
            stream: false,
            options: { temperature: 0.2 }
          })
        });
      }

      if (!res.ok) {
        throw new Error(`Ollama error ${res.status}: ${res.statusText}`);
      }
      const data = await res.json();
      rawJsonText = data.response;
    } else if (provider === 'gemini') {
      const key = apiKey || 'AIzaSyDemoPlaceholder';
      const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${key}`;
      const res = await fetch(geminiUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { responseMimeType: 'application/json', temperature: 0.2 }
        })
      });
      if (!res.ok) throw new Error(`Gemini API error ${res.status}`);
      const data = await res.json();
      rawJsonText = data.candidates?.[0]?.content?.parts?.[0]?.text || '{}';
    } else if (provider === 'openai') {
      const ep = customEndpoint || 'https://api.openai.com/v1/chat/completions';
      const res = await fetch(ep, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey}`
        },
        body: JSON.stringify({
          model: 'gpt-4o-mini',
          messages: [{ role: 'user', content: prompt }],
          response_format: { type: 'json_object' }
        })
      });
      if (!res.ok) throw new Error(`OpenAI API error ${res.status}`);
      const data = await res.json();
      rawJsonText = data.choices?.[0]?.message?.content || '{}';
    }

    const cleaned = rawJsonText.replace(/```json/g, '').replace(/```/g, '').trim();
    const parsedJson = JSON.parse(cleaned);
    return formatLlmParameters(parsedJson, parsedBpmn);
  } catch (err) {
    console.warn('LLM Parameter Inference failed, falling back to heuristic parameters:', err);
    throw err;
  }
}

function formatLlmParameters(llmOut: any, parsedBpmn: ProcessTopology): SimulationParameters {
  const domainName = llmOut.domain || 'Enterprise Workflow';
  const entityName = llmOut.entityName || 'Process Cases';
  const entitySingular = llmOut.entitySingular || 'Case';

  const global = {
    caseCount: Number(llmOut.global?.caseCount) || 400,
    arrivalMeanMinutes: Number(llmOut.global?.arrivalMeanMinutes) || 20,
    arrivalDistribution: 'exponential' as const,
    slaThresholdHours: Number(llmOut.global?.slaThresholdHours) || 12,
    domainName,
    entityName,
    entitySingular,
    caseCountLabel: llmOut.global?.caseCountLabel || `Total ${entityName} to Simulate`,
    arrivalLabel: llmOut.global?.arrivalLabel || `${entitySingular} Submission Frequency`,
    slaLabel: llmOut.global?.slaLabel || `${entitySingular} SLA Target Limit`,
    arrivalHelpText: `Average time between incoming ${entityName.toLowerCase()}.`,
    slaHelpText: `Target deadline before a ${entitySingular.toLowerCase()} is flagged as late.`,
    domain: domainName,
    domainSummary: llmOut.domainSummary || '',
    keyBottleneckPrediction: llmOut.keyBottleneckPrediction || ''
  };

  const tasks: Record<string, TaskParameter> = {};
  parsedBpmn.tasks.forEach((task) => {
    const aiTask = llmOut.tasks?.[task.id] || {};
    const mean = Math.max(1, Number(aiTask.meanDuration) || 30);
    const capacity = Math.max(1, Number(aiTask.resourceCapacity) || 2);
    const isConstant = aiTask.distribution === 'constant';
    const distribution: DurationDistribution = isConstant ? 'constant' : 'normal';

    tasks[task.id] = {
      id: task.id,
      name: task.name,
      distribution,
      meanDuration: mean,
      stdDev: isConstant ? 0 : Math.max(0.5, Number(aiTask.stdDev) || Math.round(mean * 0.25)),
      minDuration: Math.max(1, Math.round(mean * 0.5)),
      maxDuration: Math.round(mean * 1.8),
      resourceCapacity: capacity,
      hourlyLaborRate: Number(aiTask.hourlyLaborRate) || 35,
      costPerExecution: Number(aiTask.costPerExecution) || 15,
      reworkRate: Math.min(0.3, Math.max(0, Number(aiTask.reworkRate) || 0.05)),
      rationale: aiTask.rationale || 'Inferred by Process Intelligence LLM'
    };
  });

  const gateways: Record<string, { id: string; name: string; branches: Array<{ flowId: string; name: string; probability: number; ruleDescription?: string }> }> = {};
  parsedBpmn.gateways.forEach((gw) => {
    if (gw.type === 'exclusiveGateway' && gw.outgoing.length > 0) {
      const aiGw = llmOut.gateways?.[gw.id];
      const branches: Array<{ flowId: string; name: string; probability: number; ruleDescription?: string }> = [];

      gw.outgoing.forEach((flowId) => {
        const flow = parsedBpmn.sequenceFlows[flowId];
        const aiBranch = aiGw?.branches?.find((b: any) => b.flowId === flowId);
        const prob = aiBranch ? Number(aiBranch.probability) : 1 / gw.outgoing.length;

        branches.push({
          flowId,
          name: flow ? flow.name : flowId,
          probability: prob,
          ruleDescription: aiBranch?.ruleDescription || 'Routing rule'
        });
      });

      const sum = branches.reduce((acc, b) => acc + b.probability, 0) || 1;
      branches.forEach((b) => {
        b.probability = Number((b.probability / sum).toFixed(2));
      });

      gateways[gw.id] = {
        id: gw.id,
        name: gw.name,
        branches
      };
    }
  });

  return {
    global,
    tasks,
    gateways,
    meta: {
      inferredBy: 'Process Intelligence LLM',
      timestamp: new Date().toISOString(),
      domain: global.domain,
      summary: global.domainSummary,
      predictedBottleneck: global.keyBottleneckPrediction
    }
  };
}

/**
 * Universal Dynamic Contextual Chart Generator
 * Analyzes simulation findings + user context to generate declarative chart specification
 */
export async function generateContextualChart(
  simulationResult: SimulationResult,
  userContext = '',
  providerConfig: LlmProviderConfig = {}
): Promise<DynamicChartSpec> {
  const topBottlenecks = simulationResult.activityBottlenecks.slice(0, 6);

  // Fallback heuristic chart generation if LLM call is omitted or fails
  const makeFallbackChart = (): DynamicChartSpec => {
    return {
      chartTitle: 'Bottleneck Workload vs. Active Processing Time',
      chartSubtitle: `Queue delays sitting in line vs. productive time across critical activities for ${simulationResult.summary.totalCases} cases.`,
      chartType: 'bar',
      xAxisKey: 'name',
      yAxisKeys: [
        { key: 'avgWaitMinutes', name: 'Queue Delay (min)', color: '#f43f5e' },
        { key: 'avgServiceMinutes', name: 'Active Service (min)', color: '#818cf8' }
      ],
      data: topBottlenecks.map((b) => ({
        name: b.name.length > 18 ? b.name.slice(0, 16) + '...' : b.name,
        avgWaitMinutes: b.avgWaitMinutes,
        avgServiceMinutes: b.avgServiceMinutes,
        utilization: b.utilizationPercent
      })),
      managerialInsight: `The primary system constraint is "${simulationResult.summary.primaryBottleneck}" where cases wait an average of ${topBottlenecks[0]?.avgWaitMinutes || 0} minutes in queue. Increasing headcount or automating this step will directly lift SLA adherence above ${simulationResult.summary.slaAdherencePercent}%.`
    };
  };

  const provider = providerConfig.provider;
  if (!provider) return makeFallbackChart();

  const prompt = `You are a Principal Process Mining Data Scientist.
Analyze this discrete-event simulation result and the user context inquiry.
Produce a single declarative DynamicChartSpec in JSON.

SIMULATION FINDINGS:
- Total Cases: ${simulationResult.summary.totalCases}
- SLA Adherence: ${simulationResult.summary.slaAdherencePercent}%
- Avg Cycle Time: ${simulationResult.summary.avgCycleTimeHours} hours
- Primary Bottleneck: ${simulationResult.summary.primaryBottleneck}
- Top Activities:
${JSON.stringify(topBottlenecks, null, 2)}

USER INQUIRY / CONTEXT:
"${userContext || 'Show me which activities create the biggest delays and how resources are stressed.'}"

CHOOSE THE BEST CHART TYPE:
- 'bar' (for activity wait rankings, costs, or headcount comparison)
- 'area' (for stage-by-stage cumulative duration or queue progression)
- 'line' (for cycle time trends or SLA threshold impact)
- 'radar' (for multi-dimensional activity comparison: speed, load, cost)

RETURN ONLY VALID JSON:
{
  "chartTitle": "Clear, informative executive chart title",
  "chartSubtitle": "Brief explanation of what this chart compares",
  "chartType": "bar" | "line" | "area" | "radar",
  "xAxisKey": "name",
  "yAxisKeys": [
    { "key": "string", "name": "Human-readable label", "color": "#hex" }
  ],
  "data": [
    { "name": "Activity A", "metric1": 12, "metric2": 34 }
  ],
  "managerialInsight": "1-2 sentence actionable executive recommendation based on the chart."
}`;

  try {
    let raw = '';
    if (provider === 'ollama') {
      const res = await fetch(`${providerConfig.ollamaUrl || 'http://localhost:11434'}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: providerConfig.ollamaModel || 'llama3.2:3b',
          prompt,
          format: 'json',
          stream: false,
          options: { temperature: 0.3 }
        })
      });
      if (!res.ok) return makeFallbackChart();
      const data = await res.json();
      raw = data.response;
    } else if (provider === 'openai' && providerConfig.apiKey) {
      const res = await fetch(providerConfig.customEndpoint || 'https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${providerConfig.apiKey}`
        },
        body: JSON.stringify({
          model: 'gpt-4o-mini',
          messages: [{ role: 'user', content: prompt }],
          response_format: { type: 'json_object' }
        })
      });
      if (!res.ok) return makeFallbackChart();
      const data = await res.json();
      raw = data.choices?.[0]?.message?.content || '{}';
    } else {
      return makeFallbackChart();
    }

    const cleaned = raw.replace(/```json/g, '').replace(/```/g, '').trim();
    const parsed = JSON.parse(cleaned);
    if (parsed.chartTitle && parsed.data && Array.isArray(parsed.data)) {
      return parsed;
    }
    return makeFallbackChart();
  } catch {
    return makeFallbackChart();
  }
}
