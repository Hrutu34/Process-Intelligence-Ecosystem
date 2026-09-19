package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class FallbackMockPipeline {

    private static final Logger log = LoggerFactory.getLogger(FallbackMockPipeline.class);

    @Value("${pie.demo-mode.enabled:false}")
    private boolean demoModeEnabled;

    @Value("${pie.fallback.enabled:false}")
    private boolean fallbackEnabled;

    @Value("${pie.agent.timeout-ms:30000}")
    private long timeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentLogger agentLogger;

    public FallbackMockPipeline(AgentLogger agentLogger) {
        this.agentLogger = agentLogger;
    }

    public <T> Object executeWithFallback(String scenarioHint, String outputType, Class<T> returnType, Callable<T> actualCall) {
        if (!demoModeEnabled && !fallbackEnabled) {
            try {
                return actualCall.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(actualCall);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("LLM_TIMEOUT: Execution exceeded {} ms. Triggering fallback.", timeoutMs);
            agentLogger.logFallbackTriggered(mapOutputTypeToStage(outputType), "LLM_TIMEOUT");
            return loadFallback(scenarioHint, outputType, returnType, "LLM_TIMEOUT");
        } catch (Exception e) {
            log.warn("EXECUTION_FAILED: Exception during pipeline step. Triggering fallback.", e);
            agentLogger.logFallbackTriggered(mapOutputTypeToStage(outputType), "EXECUTION_FAILED");
            return loadFallback(scenarioHint, outputType, returnType, "EXECUTION_FAILED");
        } finally {
            executor.shutdownNow();
        }
    }

    private String mapOutputTypeToStage(String outputType) {
        if ("knowledge".equals(outputType)) return "KNOWLEDGE_EXTRACTION";
        if ("review".equals(outputType)) return "PROCESS_INTELLIGENCE";
        if ("bpmn".equals(outputType)) return "BPMN_MODELLING";
        if ("summary".equals(outputType)) return "PROCESS_REVIEW";
        return outputType.toUpperCase();
    }

    private <T> Object loadFallback(String hint, String type, Class<T> returnType, String reason) {
        String scenario = determineScenario(hint);
        String fileName = scenario + "-" + type + ".json";
        
        // Handle BPMN xml which isn't json
        if ("bpmn".equals(type) || "xml".equals(type)) {
            // For hackathon fallback, just return an empty valid bpmn if generation fails
            String mockBpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"Definitions_1\"><bpmn:process id=\"Process_1\" isExecutable=\"false\"></bpmn:process></bpmn:definitions><!-- FALLBACK_USED: " + reason + " -->";
            log.info("Loaded mock BPMN XML fallback");
            return mockBpmn;
        }

        File fallbackFile = new File("../samples/expected-output/" + fileName);
        if (!fallbackFile.exists()) {
            fallbackFile = new File("samples/expected-output/" + fileName);
        }
        
        T data = null;
        try {
            if (fallbackFile.exists()) {
                data = objectMapper.readValue(fallbackFile, returnType);
            } else {
                log.warn("Fallback file not found: {}", fallbackFile.getAbsolutePath());
                // Instantiate a basic mock object if file not found
                data = returnType.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            log.error("Failed to parse fallback JSON from {}", fallbackFile.getAbsolutePath(), e);
        }

        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("fallbackUsed", true);
        fallbackResponse.put("reason", reason);
        fallbackResponse.put("source", fallbackFile.getPath());
        fallbackResponse.put("data", data);

        return fallbackResponse;
    }

    private String determineScenario(String hint) {
        if (hint == null) return "travel-request";
        String lower = hint.toLowerCase();
        if (lower.contains("leave")) return "leave-approval";
        if (lower.contains("purchase")) return "purchase-request";
        return "travel-request";
    }
}
