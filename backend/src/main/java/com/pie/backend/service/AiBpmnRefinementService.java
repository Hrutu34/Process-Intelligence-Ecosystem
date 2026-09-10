package com.pie.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pie.shared.dto.ProcessKnowledgeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AiBpmnRefinementService {

    private static final Logger log = LoggerFactory.getLogger(AiBpmnRefinementService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/bpmn-refinement-prompt.txt")
    private Resource refinementPromptResource;

    public AiBpmnRefinementService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    public String refineBpmn(String draftBpmnXml, ProcessKnowledgeDTO knowledge) {
        try {
            String knowledgeJson = objectMapper.writeValueAsString(knowledge);

            String systemPrompt;
            if (refinementPromptResource != null && refinementPromptResource.exists()) {
                systemPrompt = new String(refinementPromptResource.getContentAsByteArray(), StandardCharsets.UTF_8);
            } else {
                try {
                    Resource defaultRes = new org.springframework.core.io.ClassPathResource("prompts/bpmn-refinement-prompt.txt");
                    systemPrompt = new String(defaultRes.getContentAsByteArray(), StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    systemPrompt = """
                        You are an expert Enterprise Process Architect and BPMN 2.0 specialist.
                        Your task is to review a draft BPMN 2.0 XML and the extracted process knowledge,
                        and refine the BPMN 2.0 XML to be more precise, industry-relevant, and logically correct.
                        
                        Ensure the following:
                        - The BPMN is valid XML and adheres to the BPMN 2.0 standard.
                        - All necessary BPMNDI diagram elements are present and correctly mapped so that it renders beautifully in bpmn.io without overlapping edges where possible.
                        - Use the extracted knowledge to fix missing roles, tasks, or gateways in the draft XML.
                        
                        IMPORTANT: Output ONLY the raw valid BPMN 2.0 XML string starting with <?xml version="1.0" encoding="UTF-8"?> and ending with </bpmn:definitions>.
                        Do not include markdown code blocks (like ```xml), no explanations, no prefix, and no suffix.
                        """;
                }
            }

            String userPrompt = String.format("""
                Draft BPMN XML:
                %s
                
                Extracted Process Knowledge:
                %s
                """, draftBpmnXml, knowledgeJson);

            String rawResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("Model returned empty BPMN response, returning draft");
                return draftBpmnXml;
            }

            // Cleanup potential markdown formatting if the LLM ignores instructions
            String cleanedResponse = rawResponse.trim();
            if (cleanedResponse.startsWith("```xml")) {
                cleanedResponse = cleanedResponse.substring(6);
            } else if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            
            cleanedResponse = cleanedResponse.trim();
            
            if (!cleanedResponse.startsWith("<?xml")) {
                // Try to find <?xml
                int idx = cleanedResponse.indexOf("<?xml");
                if (idx >= 0) {
                    cleanedResponse = cleanedResponse.substring(idx);
                } else {
                    log.warn("Response doesn't start with <?xml, returning draft");
                    return draftBpmnXml;
                }
            }
            
            return cleanedResponse;

        } catch (Exception e) {
            log.error("Failed to refine BPMN: {}", e.getMessage());
            return draftBpmnXml; // Fallback to draft if refinement fails
        }
    }
}

