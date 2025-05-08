package com.example.workflow.model;

import com.example.workflow.entity.OrganizationEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private WorkflowJsonEntity workflow;

    private int currentNodeIndex;

    private String status;

    @Column(name = "uploaded_document", columnDefinition = "bytea")
    private byte[] uploadedDocument;

    private String uploadedFileName;

    private String mimeType;

    @Column(name = "node_statuses", columnDefinition = "text")
    private String nodeStatuses;

    @Column(name = "workflow_data", columnDefinition = "text")
    private String workflowData;

    private String reviewDecision;

    @Column(columnDefinition = "text")
    private String reviewNotes;

    private String approvalDecision;

    @Column(columnDefinition = "text")
    private String approvalNotes;

    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    // Add getter and setter
    public OrganizationEntity getOrganization() {
        return organization;
    }

    public void setOrganization(OrganizationEntity organization) {
        this.organization = organization;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkflowJsonEntity getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowJsonEntity workflow) {
        this.workflow = workflow;
    }

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public void setCurrentNodeIndex(int currentNodeIndex) {
        this.currentNodeIndex = currentNodeIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public byte[] getUploadedDocument() {
        return uploadedDocument;
    }

    public void setUploadedDocument(byte[] uploadedDocument) {
        this.uploadedDocument = uploadedDocument;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }

    public void setUploadedFileName(String uploadedFileName) {
        this.uploadedFileName = uploadedFileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getNodeStatuses() {
        return nodeStatuses;
    }

    public void setNodeStatuses(String nodeStatuses) {
        this.nodeStatuses = nodeStatuses;
    }

    public String getWorkflowData() {
        return workflowData;
    }

    public void setWorkflowData(String workflowData) {
        this.workflowData = workflowData;
    }

    public String getReviewDecision() {
        return reviewDecision;
    }

    public void setReviewDecision(String reviewDecision) {
        this.reviewDecision = reviewDecision;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public String getApprovalDecision() {
        return approvalDecision;
    }

    public void setApprovalDecision(String approvalDecision) {
        this.approvalDecision = approvalDecision;
    }

    public String getApprovalNotes() {
        return approvalNotes;
    }

    public void setApprovalNotes(String approvalNotes) {
        this.approvalNotes = approvalNotes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods for working with JSON data

    /**
     * Get node statuses as a map
     */
    public Map<String, String> getNodeStatusesAsMap() {
        if (nodeStatuses == null || nodeStatuses.isEmpty()) {
            return new HashMap<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(nodeStatuses, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Get workflow data as a map
     */
    public Map<String, Object> getWorkflowDataAsMap() {
        if (workflowData == null || workflowData.isEmpty()) {
            return new HashMap<>();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(workflowData, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String documentType;

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    private String currentNodeType;
    private String requiredRole;

    public String getCurrentNodeType() {
        return currentNodeType;
    }

    public void setCurrentNodeType(String currentNodeType) {
        this.currentNodeType = currentNodeType;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }
}