package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentLogger {
    private static final Logger log = LoggerFactory.getLogger("AGENT_TELEMETRY");
    private final ObjectMapper mapper = new ObjectMapper();
    private static final ThreadLocal<String> currentSession = new InheritableThreadLocal<>();

    public void setSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "SESSION-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        currentSession.set(sessionId);
    }

    public String getSessionId() {
        if (currentSession.get() == null) {
            setSessionId(null);
        }
        return currentSession.get();
    }

    public void clearSession() {
        currentSession.remove();
    }

    public void logStart(String stage) {
        logTelemetry(stage, "STARTED", 0, false, null);
    }

    public void logSuccess(String stage, long durationMs) {
        logTelemetry(stage, "SUCCESS", durationMs, false, null);
    }

    /**
     * Records completion of a stage that was served by mock/fallback data rather than
     * a real agent result, so SUCCESS is never reported for canned output.
     */
    public void logCompletion(String stage, long durationMs, boolean fallbackUsed) {
        if (fallbackUsed) {
            logTelemetry(stage, "COMPLETED_WITH_FALLBACK", durationMs, true, "FALLBACK_DATA");
        } else {
            logTelemetry(stage, "SUCCESS", durationMs, false, null);
        }
    }

    /**
     * True when a FallbackMockPipeline result carries the fallback marker.
     */
    public static boolean isFallbackResult(Object result) {
        return result instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("fallbackUsed"));
    }
    
    public void logFallbackTriggered(String stage, String reason) {
        logTelemetry(stage, "FALLBACK_TRIGGERED", 0, true, reason);
    }

    public void logError(String stage, long durationMs, String errorCode) {
        logTelemetry(stage, "ERROR", durationMs, false, errorCode);
    }

    private void logTelemetry(String stage, String status, long durationMs, boolean fallbackUsed, String errorCode) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("sessionId", getSessionId());
        logData.put("stage", stage);
        logData.put("status", status);
        if (durationMs > 0) {
            logData.put("durationMs", durationMs);
        }
        if (fallbackUsed || errorCode != null) {
            logData.put("fallbackUsed", fallbackUsed);
            if (errorCode != null) logData.put("errorCode", errorCode);
        }

        try {
            log.info(mapper.writeValueAsString(logData));
        } catch (Exception e) {
            log.error("Failed to serialize telemetry log", e);
        }
    }
}
