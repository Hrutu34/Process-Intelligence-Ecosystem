package com.pie.backend.service;

import com.pie.backend.model.DocumentRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentStorageServiceTest {

    @Test
    public void testPersistFileAndText() throws Exception {
        DocumentStorageService svc = new DocumentStorageService(java.util.Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());
        String docId = svc.createDocumentId();
        DocumentRecord rec = svc.persistFileAndExtractedText(docId, file, "Hello World Extracted");

        File original = new File(rec.originalFilePath);
        assertTrue(original.exists());

        var extractedPath = new File("storage/documents/" + docId + "/extracted/content.txt");
        assertTrue(extractedPath.exists());
        String content = Files.readString(extractedPath.toPath());
        assertEquals("Hello World Extracted", content);

        var meta = new File("storage/documents/" + docId + "/metadata.json");
        assertTrue(meta.exists());
    }
}
