package com.pie.backend.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

@Service
public class DocumentParsingService {
    
    public String parseDocument(MultipartFile file) {
        try (InputStream stream = file.getInputStream()) {
            Tika tika = new Tika();
            return tika.parseToString(stream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }
}