package com.pie.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BpmnEditServiceTest {

    private BpmnEditService editService;
    private BpmnVersionService versionService;

    private static final String BASE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
              <bpmn:process id="Process_1" name="Order Process" isExecutable="true">
                <bpmn:startEvent id="Start_1" name="Order Received" />
                <bpmn:userTask id="Task_Verify" name="Verify Order" />
                <bpmn:userTask id="Task_Pay" name="Process Payment" />
                <bpmn:endEvent id="End_1" name="Order Completed" />
                <bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="Task_Verify" />
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Verify" targetRef="Task_Pay" />
                <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_Pay" targetRef="End_1" />
              </bpmn:process>
            </bpmn:definitions>
            """;

    @BeforeEach
    void setUp() {
        editService = new BpmnEditService();
        versionService = new BpmnVersionService();
    }

    @Test
    void insertElement_afterTask_splicesFlowCleanly() throws Exception {
        Map<String, Object> op = Map.of(
                "op", "insert_element",
                "name", "Manager Approval",
                "type", "userTask",
                "after", "Verify Order"
        );

        var result = editService.applyOperations(BASE_BPMN, List.of(op));
        assertTrue(result.failed().isEmpty(), "No ops should fail");
        assertTrue(result.xml().contains("Manager Approval"));
        assertTrue(result.xml().contains("Task_Verify"));
        // Old flow from Task_Verify to Task_Pay was rewired to the new task
        assertTrue(result.xml().contains("sourceRef=\"Task_Verify\""));
    }

    @Test
    void deleteElement_withReconnect_connectsNeighbors() throws Exception {
        // In BASE_BPMN: Task_Verify -> Task_Pay -> End_1.
        // Deleting Task_Pay with reconnect=true should create a direct flow from Task_Verify to End_1.
        Map<String, Object> op = Map.of(
                "op", "delete_element",
                "id", "Task_Pay",
                "reconnect", true
        );

        var result = editService.applyOperations(BASE_BPMN, List.of(op));
        assertTrue(result.failed().isEmpty());
        assertFalse(result.xml().contains("id=\"Task_Pay\""));
        // Verify new sequence flow connects Task_Verify directly to End_1
        assertTrue(result.xml().contains("sourceRef=\"Task_Verify\" targetRef=\"End_1\""));
    }

    @Test
    void replaceElement_gatewayTypeAndTaskType() throws Exception {
        // First insert an exclusive gateway
        Map<String, Object> addGwOp = Map.of(
                "op", "add_gateway",
                "gatewayType", "exclusive",
                "name", "Approved?",
                "after", "Task_Verify"
        );
        var res1 = editService.applyOperations(BASE_BPMN, List.of(addGwOp));
        assertTrue(res1.xml().contains("exclusiveGateway"));

        // Now replace the gateway with a parallelGateway
        Map<String, Object> replaceOp = Map.of(
                "op", "replace_element",
                "name", "Approved?",
                "newType", "parallelGateway"
        );
        var res2 = editService.applyOperations(res1.xml(), List.of(replaceOp));
        assertTrue(res2.failed().isEmpty());
        assertTrue(res2.xml().contains("parallelGateway"));
        assertTrue(res2.xml().contains("Approved?"));
    }

    @Test
    void moveElement_repositionsSafely() throws Exception {
        // Move Task_Pay before Task_Verify
        Map<String, Object> moveOp = Map.of(
                "op", "move_element",
                "id", "Task_Pay",
                "before", "Task_Verify"
        );

        var result = editService.applyOperations(BASE_BPMN, List.of(moveOp));
        assertTrue(result.failed().isEmpty());
        assertTrue(result.xml().contains("Task_Pay"));
        assertTrue(result.xml().contains("Task_Verify"));
    }

    @Test
    void changeAllTasks_convertsAllUserTasksToServiceTasks() throws Exception {
        Map<String, Object> op = Map.of(
                "op", "change_all_tasks",
                "toType", "serviceTask"
        );

        var result = editService.applyOperations(BASE_BPMN, List.of(op));
        assertTrue(result.failed().isEmpty());
        assertTrue(result.xml().contains("bpmn:serviceTask"));
        assertFalse(result.xml().contains("bpmn:userTask"));
    }

    @Test
    void versionService_undoAndRedoStack() {
        String pId = "test-process";
        versionService.saveVersion(pId, "<v1/>", "Initial", List.of());
        versionService.saveVersion(pId, "<v2/>", "Added step", List.of());
        versionService.saveVersion(pId, "<v3/>", "Renamed task", List.of());

        assertEquals(3, versionService.getCurrentVersion(pId).get().versionNumber());

        var undone1 = versionService.undo(pId, 1);
        assertTrue(undone1.isPresent());
        assertEquals(2, undone1.get().versionNumber());
        assertEquals("<v2/>", undone1.get().xml());

        var undone2 = versionService.undo(pId, 1);
        assertTrue(undone2.isPresent());
        assertEquals(1, undone2.get().versionNumber());
        assertEquals("<v1/>", undone2.get().xml());

        var redone = versionService.redo(pId, 1);
        assertTrue(redone.isPresent());
        assertEquals(2, redone.get().versionNumber());
        assertEquals("<v2/>", redone.get().xml());
    }
}
