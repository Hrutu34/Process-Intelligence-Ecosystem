package com.pie.backend.controller;

import com.pie.backend.model.DocumentEntity;
import com.pie.backend.repository.DocumentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

@RestController
public class DocumentAdminController {

    private final DocumentRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocumentAdminController(DocumentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/admin/storage")
    public List<Map<String, Object>> listStorage() {
        List<Map<String, Object>> out = new ArrayList<>();
        Path root = Path.of("storage", "documents");
        if (!root.toFile().exists()) {
            return out;
        }
        File[] dirs = root.toFile().listFiles(File::isDirectory);
        if (dirs == null)
            return out;
        for (File d : dirs) {
            File meta = d.toPath().resolve("metadata.json").toFile();
            if (meta.exists()) {
                try {
                    Map m = mapper.readValue(meta, Map.class);
                    out.add(m);
                } catch (Exception e) {
                    out.add(Map.of("documentId", d.getName(), "error", e.getMessage()));
                }
            } else {
                out.add(Map.of("documentId", d.getName(), "metadata", "missing"));
            }
        }
        return out;
    }

    @GetMapping("/admin/documents")
    public List<Map<String, Object>> recentDocuments(@RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean fullText) {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .limit(Math.max(1, limit))
                .map(e -> toMap(e, fullText))
                .collect(Collectors.toList());
    }

    @GetMapping("/admin/documents/count")
    public Map<String, Object> count() {
        long c = repository.count();
        return Map.of("count", c);
    }

    private Map<String, Object> toMap(DocumentEntity e, boolean fullText) {
        String text = e.extractedText == null ? null : e.extractedText;
        if (!fullText && text != null && text.length() > 1000) {
            text = text.substring(0, 1000) + "...";
        }
        return Map.of(
                "documentId", e.documentId,
                "fileName", e.fileName,
                "category", e.category,
                "confidence", e.confidence,
                "createdAt", e.createdAt,
                "extractedText", text);
    }
}
