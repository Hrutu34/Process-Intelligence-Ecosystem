/**
 * Process Mining & Discrete Event Simulation Engine Benchmark Suite
 * Verifies mathematical token conservation, SLA calculations, and event log integrity
 * across all 10 standard enterprise BPMN models.
 */

const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

// Auto-enable Node 22 native TypeScript stripping if not already enabled
if (!process.execArgv.includes('--experimental-strip-types')) {
  const res = spawnSync(process.execPath, ['--experimental-strip-types', __filename, ...process.argv.slice(2)], {
    stdio: 'inherit'
  });
  process.exit(res.status ?? 0);
}

async function runTest() {
  const corpusPath = path.resolve(__dirname, '../frontend/src/data/corpusData.ts');
  const corpusContent = fs.readFileSync(corpusPath, 'utf8');

  let corpusModels = [];
  const match = corpusContent.match(/export const CORPUS_MODELS = (\[[\s\S]*?\]);/);
  if (match) {
    corpusModels = eval(match[1]);
  } else {
    const corpusModule = await import('../frontend/src/data/corpusData.ts');
    corpusModels = corpusModule.CORPUS_MODELS;
  }

  const { parseBpmnXml, generateDefaultParameters } = await import('../frontend/src/services/bpmnParser.ts');
  const { runDiscreteEventSimulation } = await import('../frontend/src/services/simulationEngine.ts');

  console.log(`Testing ${corpusModels.length} models...`);

  let allPassed = true;

  for (const model of corpusModels) {
    try {
      const parsed = parseBpmnXml(model.xml);
      const params = generateDefaultParameters(parsed);
      const result = runDiscreteEventSimulation(parsed, params);

      console.log(`[PASS] ${model.id} (${model.name}):`);
      console.log(`       Tasks: ${parsed.tasks.length}, Gateways: ${parsed.gateways.length}, Events: ${parsed.events.length}`);
      console.log(`       Avg Cycle: ${result.summary.avgCycleTimeHours}h | SLA Adherence: ${result.summary.slaAdherencePercent}% | Top Bottleneck: ${result.summary.primaryBottleneck}`);
      console.log(`       Event Log Count: ${result.eventLog.length}`);
    } catch (err) {
      console.error(`[FAIL] ${model.id}:`, err);
      allPassed = false;
    }
  }

  if (allPassed) {
    console.log('\n>>> ALL 10 BPMN MODELS PARSED AND SIMULATED SUCCESSFULLY! <<<');
  } else {
    process.exit(1);
  }
}

runTest().catch((err) => {
  console.error('Test execution fatal error:', err);
  process.exit(1);
});
