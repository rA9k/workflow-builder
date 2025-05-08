package com.example.workflow.service;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowDefinition;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkflowExecutionService {

    private final WorkflowExecutionRepository workflowExecutionRepository;

    @Autowired
    private OrganizationService organizationService;

    public WorkflowExecutionService(WorkflowExecutionRepository workflowExecutionRepository) {
        this.workflowExecutionRepository = workflowExecutionRepository;
    }

    @Transactional
    public void deleteWorkflowExecution(Long executionId) {
        OrganizationEntity organization = organizationService.getCurrentOrganization();
        Optional<WorkflowExecutionEntity> executionOpt = workflowExecutionRepository.findByIdAndOrganization(
                executionId, organization);

        if (executionOpt.isEmpty()) {
            throw new RuntimeException("Execution not found or you don't have permission to delete it");
        }

        workflowExecutionRepository.delete(executionOpt.get());
    }

    @Transactional
    public List<WorkflowExecutionEntity> getWorkflowExecutions() {
        OrganizationEntity organization = organizationService.getCurrentOrganization();
        return workflowExecutionRepository.findByOrganizationOrderByUpdatedAtDesc(organization);
    }

    @Transactional
    public WorkflowExecutionEntity createWorkflowExecution(WorkflowJsonEntity workflow, String uploadedFileName,
            byte[] document, String mimeType, String createdBy) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflow(workflow);
        execution.setUploadedFileName(uploadedFileName);
        execution.setUploadedDocument(document);
        execution.setMimeType(mimeType);
        execution.setCreatedBy(createdBy);
        String documentType = extractDocumentTypeFromWorkflow(workflow);
        execution.setDocumentType(documentType);

        // Set organization from the workflow's organization
        // This ensures consistency between workflow and its executions
        execution.setOrganization(workflow.getOrganization());

        // Set initial node type and required role
        updateNodeTypeAndRequiredRole(execution);

        return workflowExecutionRepository.save(execution);
    }

    @Transactional
    public WorkflowExecutionEntity updateWorkflowExecution(WorkflowExecutionEntity execution) {
        // Update node type and required role based on current node index
        updateNodeTypeAndRequiredRole(execution);
        execution.setUpdatedAt(LocalDateTime.now());
        return workflowExecutionRepository.save(execution);
    }

    private void updateNodeTypeAndRequiredRole(WorkflowExecutionEntity execution) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodes = mapper.readValue(
                    execution.getWorkflow().getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            int currentNodeIndex = execution.getCurrentNodeIndex();
            if (currentNodeIndex >= 0 && currentNodeIndex < nodes.size()) {
                Map<String, Object> currentNode = nodes.get(currentNodeIndex);
                String nodeType = (String) currentNode.get("type");
                execution.setCurrentNodeType(nodeType);

                // Extract required role based on node type
                String requiredRole = null;

                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) currentNode.get("props");
                if (props != null) {
                    if ("Document Review".equals(nodeType) || "Doc Review".equals(nodeType)) {
                        requiredRole = (String) props.get("reviewerRole");
                        System.out.println("Setting reviewer role to: " + requiredRole + " for node type: " + nodeType);
                    } else if ("Approve/Reject".equals(nodeType) || "Approval".equals(nodeType)) {
                        requiredRole = (String) props.get("Approver Role");
                        System.out.println("Setting approver role to: " + requiredRole + " for node type: " + nodeType);
                    }
                }

                execution.setRequiredRole(requiredRole);
                System.out.println("Updated node type to: " + nodeType + " and required role to: " + requiredRole);
            }
        } catch (Exception e) {
            // Log error
            System.err.println("Error updating node type and required role: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * * Get workflow executions visible to a specific user based on their roles
     */
    public List<WorkflowExecutionEntity> getWorkflowExecutionsForUser(String username, List<String> userRoles) {
        List<WorkflowExecutionEntity> allExecutions = workflowExecutionRepository.findAllByOrderByUpdatedAtDesc();

        return allExecutions.stream()
                .filter(execution -> {
                    // Case 1: User created the workflow
                    if (execution.getCreatedBy() != null && execution.getCreatedBy().equals(username)) {
                        return true;
                    }

                    // Case 2: Check if the user has the required role for the current node
                    try {
                        ObjectMapper mapper = new ObjectMapper();

                        // First parse the JSON as a Map
                        Map<String, Object> workflowDataMap = mapper.readValue(
                                execution.getWorkflow().getData(),
                                new TypeReference<Map<String, Object>>() {
                                });

                        // Then extract the nodes list from the map
                        List<Map<String, Object>> workflowNodes = (List<Map<String, Object>>) workflowDataMap
                                .get("nodes");

                        if (workflowNodes == null) {
                            System.err.println("No nodes found in workflow data");
                            return false;
                        }

                        int currentNodeIndex = execution.getCurrentNodeIndex();
                        if (currentNodeIndex >= 0 && currentNodeIndex < workflowNodes.size()) {
                            Map<String, Object> currentNode = workflowNodes.get(currentNodeIndex);
                            String nodeType = (String) currentNode.get("type");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> props = (Map<String, Object>) currentNode.get("props");

                            if (props != null) {
                                // For Document Review nodes
                                if ("Document Review".equals(nodeType) || "Doc Review".equals(nodeType)) {
                                    String reviewerRole = (String) props.get("reviewerRole");
                                    if (reviewerRole != null && userRoles.contains(reviewerRole)) {
                                        return true;
                                    }
                                }
                                // For Approval nodes
                                else if ("Approve/Reject".equals(nodeType) || "Approval".equals(nodeType)) {
                                    String approverRole = (String) props.get("Approver Role");
                                    if (approverRole != null && userRoles.contains(approverRole)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log error but don't fail the filter
                        System.err.println("Error checking workflow roles: " + e.getMessage());
                        e.printStackTrace(); // Add stack trace for better debugging
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

    private String extractDocumentTypeFromWorkflow(WorkflowJsonEntity workflow) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodes = mapper.readValue(
                    workflow.getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> node : nodes) {
                if ("Upload".equals(node.get("type"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> props = (Map<String, String>) node.get("props");
                    if (props != null && props.containsKey("documentType")) {
                        return props.get("documentType");
                    }
                }
            }
        } catch (Exception e) {
            // Log error if needed
        }
        return "Unknown";
    }

    @Transactional
    public List<WorkflowExecutionEntity> getWorkflowExecutionsForUserAndOrganization(
            String username, List<String> roles, OrganizationEntity organization) {

        // Get all executions for the organization
        List<WorkflowExecutionEntity> executions = workflowExecutionRepository
                .findByOrganizationOrderByUpdatedAtDesc(organization);

        // Filter by user permissions
        return executions.stream()
                .filter(execution -> {
                    // User can see their own executions
                    if (execution.getCreatedBy().equals(username)) {
                        return true;
                    }

                    // Check if user has role required for current node
                    try {
                        WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());
                        int currentNodeIndex = execution.getCurrentNodeIndex();

                        if (currentNodeIndex >= 0 && currentNodeIndex < definition.getNodeCount()) {
                            var currentNode = definition.getNodeAt(currentNodeIndex);
                            String nodeType = currentNode.getType();
                            Map<String, String> props = currentNode.getProperties();

                            // For Document Review nodes
                            if ("Document Review".equals(nodeType)) {
                                String reviewerRole = props.getOrDefault("reviewerRole", "");
                                if (!reviewerRole.isEmpty() && roles.contains(reviewerRole)) {
                                    return true;
                                }
                            }
                            // For Approval nodes
                            else if ("Approve/Reject".equals(nodeType)) {
                                String approverRole = props.getOrDefault("Approver Role", "");
                                if (!approverRole.isEmpty() && roles.contains(approverRole)) {
                                    return true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log error but don't fail the check
                        System.err.println("Error checking workflow roles: " + e.getMessage());
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

}
