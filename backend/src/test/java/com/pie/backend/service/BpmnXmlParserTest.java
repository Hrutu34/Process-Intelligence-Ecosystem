package com.pie.backend.service;

import com.pie.shared.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class BpmnXmlParserTest {

    private BpmnXmlParser parser;
    private ProcessQualityValidator validator;

    @BeforeEach
    void setUp() {
        parser = new BpmnXmlParser();
        validator = new ProcessQualityValidator();
    }

    @Test
    @DisplayName("Parse clean F01 purchase requisition with collaboration and lanes")
    void testParseF01Clean() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F01_clean_purchase_requisition.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F01_clean_purchase_requisition.bpmn");
        }
        assertTrue(f.exists(), "F01 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        assertNotNull(res.graph());
        assertEquals("Purchase Requisition Approval", res.processName());
        assertTrue(res.graph().getNodes().stream().anyMatch(n -> n.getType() == NodeType.Role));
        assertTrue(res.graph().getNodes().stream().anyMatch(n -> n.getType() == NodeType.Activity));
        assertTrue(res.graph().getNodes().stream().anyMatch(n -> n.getType() == NodeType.Gateway));

        // Validate clean file - should have high quality score
        ProcessQualityReportDTO report = validator.validateQuality(res.graph());
        assertTrue(report.qualityScore() >= 80, "Clean file should score >= 80, got: " + report.qualityScore());
    }

    @Test
    @DisplayName("Parse defective F03 and detect planted defects D01 (missing end) and D02 (vague task name)")
    void testParseF03Defects() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F03_defect_invoice_processing.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F03_defect_invoice_processing.bpmn");
        }
        assertTrue(f.exists(), "F03 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        ProcessQualityReportDTO report = validator.validateQuality(res.graph());

        // D01: Missing end event / abrupt termination
        assertTrue(report.issues().stream().anyMatch(i -> "MISSING_END_EVENT".equals(i.ruleId())), "Must detect missing end event");
        // D02: Vague task name "Process"
        assertTrue(report.issues().stream().anyMatch(i -> "TASK_NAME_QUALITY_RULE".equals(i.ruleId()) && i.issue().contains("Process")), "Must detect vague task 'Process'");
    }

    @Test
    @DisplayName("Parse defective F04 and detect planted defects D03 (missing start) and D04 (single outgoing gateway)")
    void testParseF04Defects() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F04_defect_customer_complaint.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F04_defect_customer_complaint.bpmn");
        }
        assertTrue(f.exists(), "F04 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        ProcessQualityReportDTO report = validator.validateQuality(res.graph());

        // D03: Missing start event
        assertTrue(report.issues().stream().anyMatch(i -> "START_EVENT_RULE".equals(i.ruleId())), "Must detect missing start event");
        // D04: Misused gateway with single outgoing flow
        assertTrue(report.issues().stream().anyMatch(i -> "GATEWAY_RULE_SINGLE_BRANCH".equals(i.ruleId())), "Must detect single-branch gateway");
    }

    @Test
    @DisplayName("Parse defective F05 and detect planted defects D05 (orphan node) and D06 (vague task 'Do needful')")
    void testParseF05Defects() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F05_defect_leave_request.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F05_defect_leave_request.bpmn");
        }
        assertTrue(f.exists(), "F05 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        ProcessQualityReportDTO report = validator.validateQuality(res.graph());

        // D05: Orphan node
        assertTrue(report.issues().stream().anyMatch(i -> "ORPHAN_NODE_RULE".equals(i.ruleId()) && i.issue().contains("Update HR calendar")), "Must detect orphan task");
        // D06: Vague task "Do needful"
        assertTrue(report.issues().stream().anyMatch(i -> "TASK_NAME_QUALITY_RULE".equals(i.ruleId()) && i.issue().contains("Do needful")), "Must detect vague task 'Do needful'");
    }

    @Test
    @DisplayName("Parse defective F06 and detect planted defects D07 (missing parallel join) and D08 (dangling flow)")
    void testParseF06Defects() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F06_defect_order_fulfillment.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F06_defect_order_fulfillment.bpmn");
        }
        assertTrue(f.exists(), "F06 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        ProcessQualityReportDTO report = validator.validateQuality(res.graph());

        // D07: Missing parallel join
        assertTrue(report.issues().stream().anyMatch(i -> "PARALLEL_SPLIT_JOIN_RULE".equals(i.ruleId())), "Must detect missing parallel join");
        // D08: Dangling flow / dead-end activity that fails to reach End Event
        assertTrue(report.issues().stream().anyMatch(i ->
                ("DEAD_END_ACTIVITY".equals(i.ruleId()) || "UNREACHABLE_END".equals(i.ruleId()))
                        && i.issue().contains("Generate invoice")), "Must detect dangling invoice flow");
    }

    @Test
    @DisplayName("Parse defective F08 and detect planted defects D11 (duplicate task) and D13 (linear complexity)")
    void testParseF08Defects() throws Exception {
        File f = new File("../Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F08_defect_it_ticket.bpmn");
        if (!f.exists()) {
            f = new File("Ai copilot for BPMN/ProcessIQ_BPMN_Corpus/F08_defect_it_ticket.bpmn");
        }
        assertTrue(f.exists(), "F08 file must exist");

        String xml = Files.readString(f.toPath());
        var res = parser.parse(xml);

        ProcessQualityReportDTO report = validator.validateQuality(res.graph());

        // D11: Duplicate task "Investigate issue"
        assertTrue(report.issues().stream().anyMatch(i -> "DUPLICATE_ACTIVITY_RULE".equals(i.ruleId())), "Must detect duplicate activity");
        // D13: Linear complexity
        assertTrue(report.issues().stream().anyMatch(i -> "LINEAR_COMPLEXITY_RULE".equals(i.ruleId())), "Must detect linear complexity");
    }
}
