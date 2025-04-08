package com.example.workflow.controller;

import com.example.workflow.components.nodes.WorkflowNode;
import com.example.workflow.model.WorkflowDefinition;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionEngine;
import com.example.workflow.service.WorkflowOPAService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowJsonRepository workflowJsonRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private WorkflowExecutionEngine executionEngine;

    @Autowired
    private WorkflowOPAService opaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllWorkflows() {
        List<WorkflowJsonEntity> workflows = workflowJsonRepository.findAll();

        List<Map<String, Object>> result = workflows.stream()
                .map(w -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", w.getId());
                    map.put("name", w.getName());
                    map.put("documentType", w.getDocumentType());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowJsonEntity> getWorkflow(@PathVariable Long id) {
        Optional<WorkflowJsonEntity> workflow = workflowJsonRepository.findById(id);
        return workflow.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WorkflowJsonEntity> createWorkflow(@RequestBody Map<String, Object> workflowData) {
        try {
            String name = (String) workflowData.get("name");
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowData.get("nodes");

            WorkflowJsonEntity entity = new WorkflowJsonEntity();
            entity.setName(name);
            entity.setData(objectMapper.writeValueAsString(nodes));

            entity = workflowJsonRepository.save(entity);

            // Generate and deploy OPA policy
            WorkflowDefinition definition = new WorkflowDefinition(entity);
            String policy = generateWorkflowPolicy(definition);
            opaService.deployWorkflowPolicy(entity.getId(), policy);

            return ResponseEntity.ok(entity);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> startExecution(@PathVariable Long id) {
        try {
            Optional<WorkflowJsonEntity> workflowOpt = workflowJsonRepository.findById(id);
            if (!workflowOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            WorkflowJsonEntity workflowEntity = workflowOpt.get();
            WorkflowDefinition definition = new WorkflowDefinition(workflowEntity);

            String username = getCurrentUsername();
            WorkflowExecutionEntity execution = executionEngine.startExecution(definition, username);

            Map<String, Object> result = new HashMap<>();
            result.put("executionId", execution.getId());
            result.put("status", execution.getStatus());
            result.put("currentNodeIndex", execution.getCurrentNodeIndex());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable Long id) {
        Optional<WorkflowExecutionEntity> executionOpt = executionRepository.findById(id);
        if (!executionOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        WorkflowExecutionEntity execution = executionOpt.get();

        // Check permissions
        String username = getCurrentUsername();
        if (!execution.getCreatedBy().equals(username)) {
            // Check if user has role-based access
            boolean hasAccess = checkRoleBasedAccess(execution);
            if (!hasAccess) {
                return ResponseEntity.status(403).build();
            }
        }

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("id", execution.getId());
            result.put("status", execution.getStatus());
            result.put("currentNodeIndex", execution.getCurrentNodeIndex());
            result.put("createdBy", execution.getCreatedBy());
            result.put("createdAt", execution.getCreatedAt());
            result.put("updatedAt", execution.getUpdatedAt());

            // Add workflow definition
            WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());
            result.put("workflowName", definition.getName());
            result.put("workflowId", definition.getId());

            // Add current node info
            WorkflowNode currentNode = definition.getNodeAt(execution.getCurrentNodeIndex());
            if (currentNode != null) {
                Map<String, Object> nodeInfo = new HashMap<>();
                nodeInfo.put("name", currentNode.getName());
                nodeInfo.put("type", currentNode.getType());
                result.put("currentNode", nodeInfo);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/executions/{id}/advance")
    public ResponseEntity<Map<String, Object>> advanceExecution(
            @PathVariable Long id,
            @RequestBody Map<String, Object> actionData) {

        Optional<WorkflowExecutionEntity> executionOpt = executionRepository.findById(id);
        if (!executionOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        WorkflowExecutionEntity execution = executionOpt.get();

        // Check permissions
        String username = getCurrentUsername();
        if (!execution.getCreatedBy().equals(username)) {
            // Check if user has role-based access
            boolean hasAccess = checkRoleBasedAccess(execution);
            if (!hasAccess) {
                return ResponseEntity.status(403).build();
            }
        }

        try {
            // Create execution context
            Map<String, Object> context = executionEngine.createExecutionContext(execution);

            // Add action data to context
            context.putAll(actionData);

            // Set advance flag
            context.put("advanceWorkflow", true);

            // Process the workflow advancement
            executionEngine.advanceWorkflow(execution, context);

            // Reload execution to get updated state
            execution = executionRepository.findById(id).orElseThrow();

            Map<String, Object> result = new HashMap<>();
            result.put("id", execution.getId());
            result.put("status", execution.getStatus());
            result.put("currentNodeIndex", execution.getCurrentNodeIndex());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private boolean checkRoleBasedAccess(WorkflowExecutionEntity execution) {
        try {
            WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());
            int currentNodeIndex = execution.getCurrentNodeIndex();
            WorkflowNode currentNode = definition.getNodeAt(currentNodeIndex);

            if (currentNode == null) {
                return false;
            }

            String nodeType = currentNode.getType();
            Map<String, String> properties = currentNode.getProperties();

            // Get user roles
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            List<String> userRoles = authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .collect(Collectors.toList());

            // Check role-based access
            if ("Document Review".equals(nodeType)) {
                String reviewerRole = properties.getOrDefault("reviewerRole", "");
                return userRoles.contains(reviewerRole);
            } else if ("Approve/Reject".equals(nodeType)) {
                String approverRole = properties.getOrDefault("Approver Role", "");
                return userRoles.contains(approverRole);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String generateWorkflowPolicy(WorkflowDefinition definition) {
        StringBuilder policy = new StringBuilder();
        policy.append("package workflow_").append(definition.getId()).append("\n\n");
        policy.append("default allow = false\n\n");

        // Iterate over each node to generate appropriate policies
        for (WorkflowNode node : definition.getNodes()) {
            switch (node.getType()) {
                case "Document Review":
                    // Use reviewer role from the node properties
                    String reviewerRole = node.getProperties().getOrDefault("reviewerRole", "manager");
                    policy.append("allow if{\n")
                            .append("    input.action == \"review\"\n")
                            .append("    input.role == \"").append(reviewerRole).append("\"\n")
                            .append("}\n\n");
                    break;
                case "Approve/Reject":
                    // Use approver role from the node properties
                    String approverRole = node.getProperties().getOrDefault("Approver Role", "senior_manager");
                    policy.append("allow if{\n")
                            .append("    input.action == \"approve\"\n")
                            .append("    input.role == \"").append(approverRole).append("\"\n")
                            .append("}\n\n");
                    break;
                default:
                    // Handle additional node types here
                    break;
            }
        }

        // Add a general "allow" for uploads at the workflow level
        policy.append("allow if{\n")
                .append("    input.action == \"upload\"\n")
                .append("}\n\n");

        return policy.toString();
    }
}
