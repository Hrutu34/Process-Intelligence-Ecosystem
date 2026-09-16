package com.pie.backend.controller;

import com.pie.backend.dto.SessionStatusDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "http://localhost:5173")
public class AgentSessionController {

    private final Map<String, SessionStatusDTO> sessions = new ConcurrentHashMap<>();

    @PostMapping("/{sessionId}/init")
    public ResponseEntity<Void> initSession(@PathVariable String sessionId) {
        sessions.put(sessionId, new SessionStatusDTO(
            sessionId,
            "READING_INPUT",
            new ArrayList<>(),
            null,
            "Initializing pipeline...",
            0
        ));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{sessionId}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String sessionId, @RequestBody SessionStatusDTO status) {
        sessions.put(sessionId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}/status")
    public ResponseEntity<SessionStatusDTO> getStatus(@PathVariable String sessionId) {
        SessionStatusDTO status = sessions.get(sessionId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}

