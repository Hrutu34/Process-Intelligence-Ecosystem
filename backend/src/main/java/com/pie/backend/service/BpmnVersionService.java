package com.pie.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BpmnVersionService {

    private static final Logger log = LoggerFactory.getLogger(BpmnVersionService.class);
    private static final int MAX_VERSIONS_PER_PROCESS = 50;

    public record BpmnVersion(
            int versionNumber,
            String xml,
            String description,
            List<Map<String, Object>> operations,
            Instant timestamp
    ) {}

    public record VersionState(
            int currentVersionIndex,
            List<BpmnVersion> history
    ) {}

    // Map of processId -> list of versions
    private final Map<String, List<BpmnVersion>> historyMap = new ConcurrentHashMap<>();
    // Map of processId -> current pointer index (0-based) into history
    private final Map<String, Integer> pointerMap = new ConcurrentHashMap<>();

    /**
     * Initializes or saves a new version of BPMN XML.
     * If the user was in an "undone" state and makes a new edit, future redos are truncated.
     */
    public synchronized BpmnVersion saveVersion(String processId, String xml, String description, List<Map<String, Object>> operations) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }

        List<BpmnVersion> list = historyMap.computeIfAbsent(processId, k -> new ArrayList<>());
        int pointer = pointerMap.getOrDefault(processId, -1);

        // If pointer is not at the end of the history list, truncate any redo states
        if (pointer >= 0 && pointer < list.size() - 1) {
            list = new ArrayList<>(list.subList(0, pointer + 1));
            historyMap.put(processId, list);
        }

        int newVersionNumber = list.size() + 1;
        BpmnVersion version = new BpmnVersion(
                newVersionNumber,
                xml,
                description != null && !description.isBlank() ? description : "Version " + newVersionNumber,
                operations != null ? new ArrayList<>(operations) : List.of(),
                Instant.now()
        );

        list.add(version);
        if (list.size() > MAX_VERSIONS_PER_PROCESS) {
            list.remove(0);
        }

        pointerMap.put(processId, list.size() - 1);
        log.info("Saved BPMN version {} for process '{}': {}", version.versionNumber(), processId, version.description());
        return version;
    }

    /**
     * Reverts 'steps' versions in history.
     */
    public synchronized Optional<BpmnVersion> undo(String processId, int steps) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }

        List<BpmnVersion> list = historyMap.get(processId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        int pointer = pointerMap.getOrDefault(processId, list.size() - 1);
        int target = Math.max(0, pointer - Math.max(1, steps));

        if (target == pointer && pointer == 0) {
            // Already at the earliest version
            return Optional.of(list.get(0));
        }

        pointerMap.put(processId, target);
        BpmnVersion restored = list.get(target);
        log.info("Undid {} step(s) for process '{}'. Now at version {}", steps, processId, restored.versionNumber());
        return Optional.of(restored);
    }

    /**
     * Re-applies 'steps' versions forward in history.
     */
    public synchronized Optional<BpmnVersion> redo(String processId, int steps) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }

        List<BpmnVersion> list = historyMap.get(processId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        int pointer = pointerMap.getOrDefault(processId, list.size() - 1);
        int target = Math.min(list.size() - 1, pointer + Math.max(1, steps));

        if (target == pointer) {
            // Already at the latest version
            return Optional.of(list.get(pointer));
        }

        pointerMap.put(processId, target);
        BpmnVersion restored = list.get(target);
        log.info("Redid {} step(s) for process '{}'. Now at version {}", steps, processId, restored.versionNumber());
        return Optional.of(restored);
    }

    public synchronized Optional<BpmnVersion> getCurrentVersion(String processId) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }

        List<BpmnVersion> list = historyMap.get(processId);
        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        int pointer = pointerMap.getOrDefault(processId, list.size() - 1);
        if (pointer >= 0 && pointer < list.size()) {
            return Optional.of(list.get(pointer));
        }
        return Optional.of(list.get(list.size() - 1));
    }

    public synchronized VersionState getVersionState(String processId) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }
        List<BpmnVersion> list = historyMap.getOrDefault(processId, List.of());
        int pointer = pointerMap.getOrDefault(processId, list.isEmpty() ? -1 : list.size() - 1);
        return new VersionState(pointer, Collections.unmodifiableList(list));
    }

    public synchronized void resetHistory(String processId) {
        if (processId == null || processId.isBlank()) {
            processId = "default";
        }
        historyMap.remove(processId);
        pointerMap.remove(processId);
    }
}
