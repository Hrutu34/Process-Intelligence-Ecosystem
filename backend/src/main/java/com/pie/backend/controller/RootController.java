package com.pie.backend.controller;

import com.pie.shared.dto.ProcessKnowledgeDTO;
import com.pie.backend.service.KnowledgeExtractionService;
import com.pie.backend.service.DocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List; // THIS is the critical import that fixes the Object mismatch

@Controller
public class RootController {
    @GetMapping("/")
    public String root() {
        return "redirect:/h2-console/";
    }
}