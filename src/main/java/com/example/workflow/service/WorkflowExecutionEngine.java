package com.example.workflow.service;

import com.example.workflow.components.nodes.WorkflowNode;
import com.example.workflow.model.WorkflowDefinition;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing workflow execution
 */
@Service
public class WorkflowExecutionEngine {

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private WorkflowOPAService opaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Start a new workflow execution
     * 
     * @param definition The workflow definition
     * @param username   The user starting the execution
     * @return The created workflow execution entity
     */
    @Transactional
    public WorkflowExecutionEntity startExecution(WorkflowDefinition definition, String username) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflow(definition.getEntity());
        execution.setStatus("In Progress");
        execution.setCurrentNodeIndex(0);
        execution.setCreatedBy(username);

        // Initialize node statuses
        Map<String, String> nodeStatuses = new HashMap<>();
        for (int i = 0; i < definition.getNodeCount(); i++) {
            WorkflowNode node = definition.getNodeAt(i);
            nodeStatuses.put(node.getName(), i == 0 ? "In Progress" : "Pending");
        }

        try {
            execution.setNodeStatuses(objectMapper.writeValueAsString(nodeStatuses));
            execution.setWorkflowData(objectMapper.writeValueAsString(new HashMap<>()));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing workflow execution", e);
        }

        return executionRepository.save(execution);
    }

    /**
     * Get a workflow execution by ID
     * 
     * @param executionId The execution ID
     * @return The workflow execution entity
     * @throws RuntimeException if the execution is not found
     */
    public WorkflowExecutionEntity getExecution(Long executionId) {
        Optional<WorkflowExecutionEntity> executionOpt = executionRepository.findById(executionId);
        if (executionOpt.isEmpty()) {
            throw new RuntimeException("Execution not found: " + executionId);
        }
        return executionOpt.get();
    }

    /**
     * Create an execution context for a workflow execution
     * 
     * @param execution The workflow execution
     * @return A map containing the execution context
     */
    public Map<String, Object> createExecutionContext(WorkflowExecutionEntity execution) {
        Map<String, Object> context = new HashMap<>();

        // Add basic execution info
        context.put("executionId", execution.getId());
        context.put("workflowId", execution.getWorkflow().getId());
        context.put("currentNodeIndex", execution.getCurrentNodeIndex());
        context.put("status", execution.getStatus());

        // Add document info
        context.put("uploadedDocument", execution.getUploadedDocument());
        context.put("uploadedFileName", execution.getUploadedFileName());
        context.put("mimeType", execution.getMimeType());

        // Add services
        context.put("opaService", opaService);
        context.put("workflowExecutionEngine", this);

        // Add workflow data
        try {
            Map<String, Object> workflowData = execution.getWorkflowDataAsMap();
            context.put("workflowData", workflowData);

            // Add review/approval info for convenience
            context.put("reviewDecision", execution.getReviewDecision());
            context.put("reviewNotes", execution.getReviewNotes());
            context.put("approvalDecision", execution.getApprovalDecision());
            context.put("approvalNotes", execution.getApprovalNotes());
        } catch (Exception e) {
            context.put("workflowData", new HashMap<>());
        }

        return context;
    }

    /**
     * Advance a workflow to the next node based on the execution context
     * 
     * @param execution The workflow execution to advance
     * @param context   The execution context containing workflow state
     * @return The updated workflow execution
     */
    @Transactional
    public WorkflowExecutionEntity advanceWorkflow(WorkflowExecutionEntity execution, Map<String, Object> context) {
        // Check if we should advance the workflow
        if (context.containsKey("advanceWorkflow") && (boolean) context.get("advanceWorkflow")) {
            try {
                // Get workflow definition
                WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());

                // Get current node
                int currentNodeIndex = execution.getCurrentNodeIndex();
                WorkflowNode currentNode = definition.getNodeAt(currentNodeIndex);

                // Update node statuses
                Map<String, String> nodeStatuses = execution.getNodeStatusesAsMap();

                // Check if we need to return to upload node
                if (context.containsKey("returnToUpload") && (boolean) context.get("returnToUpload")) {
                    // Find the upload node
                    for (int i = 0; i < definition.getNodeCount(); i++) {
                        WorkflowNode node = definition.getNodeAt(i);
                        if ("Upload".equals(node.getType())) {
                            // Update current node status
                            if (currentNode != null) {
                                String status = context.containsKey("nodeStatus")
                                        ? (String) context.get("nodeStatus")
                                        : "Completed";
                                nodeStatuses.put(currentNode.getName(), status);
                            }

                            // Set the upload node as current
                            execution.setCurrentNodeIndex(i);
                            nodeStatuses.put(node.getName(), "In Progress");
                            break;
                        }
                    }
                } else {
                    // Normal advancement to next node
                    if (currentNode != null) {
                        String status = context.containsKey("nodeStatus")
                                ? (String) context.get("nodeStatus")
                                : "Completed";
                        nodeStatuses.put(currentNode.getName(), status);
                    }

                    // Move to next node
                    execution.setCurrentNodeIndex(currentNodeIndex + 1);

                    // If there's a next node, mark it as in progress
                    if (currentNodeIndex + 1 < definition.getNodeCount()) {
                        WorkflowNode nextNode = definition.getNodeAt(currentNodeIndex + 1);
                        nodeStatuses.put(nextNode.getName(), "In Progress");
                    }
                }

                // Update workflow status if specified
                if (context.containsKey("workflowStatus")) {
                    execution.setStatus((String) context.get("workflowStatus"));
                }

                // Update document if uploaded
                if (context.containsKey("uploadedDocument")) {
                    execution.setUploadedDocument((byte[]) context.get("uploadedDocument"));
                }

                if (context.containsKey("uploadedFileName")) {
                    execution.setUploadedFileName((String) context.get("uploadedFileName"));
                }

                if (context.containsKey("mimeType")) {
                    execution.setMimeType((String) context.get("mimeType"));
                }

                // Update workflow data
                Map<String, Object> workflowData = (Map<String, Object>) context.get("workflowData");
                if (workflowData != null) {
                    execution.setWorkflowData(objectMapper.writeValueAsString(workflowData));

                    // Update review/approval fields
                    if (workflowData.containsKey("reviewDecision")) {
                        execution.setReviewDecision((String) workflowData.get("reviewDecision"));
                    }

                    if (workflowData.containsKey("reviewNotes")) {
                        execution.setReviewNotes((String) workflowData.get("reviewNotes"));
                    }

                    if (workflowData.containsKey("approvalDecision")) {
                        execution.setApprovalDecision((String) workflowData.get("approvalDecision"));
                    }

                    if (workflowData.containsKey("approvalNotes")) {
                        execution.setApprovalNotes((String) workflowData.get("approvalNotes"));
                    }
                }

                // Update node statuses
                execution.setNodeStatuses(objectMapper.writeValueAsString(nodeStatuses));

                // Check if workflow is complete
                if (execution.getCurrentNodeIndex() >= definition.getNodeCount() &&
                        !"Rejected".equals(execution.getStatus())) {
                    execution.setStatus("Completed");
                }

                // Save the updated execution
                return executionRepository.save(execution);
            } catch (Exception e) {
                throw new RuntimeException("Error advancing workflow", e);
            }
        }

        // If no advancement needed, just return the current execution
        return execution;
    }
}
