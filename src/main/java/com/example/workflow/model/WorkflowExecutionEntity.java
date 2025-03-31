package com.example.workflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private WorkflowJsonEntity workflow;

    @Column(name = "current_node_index")
    private Integer currentNodeIndex;

    @Column(name = "status")
    private String status;

    @Column(name = "uploaded_file_name")
    private String uploadedFileName;

    @Lob
    @Column(name = "uploaded_document")
    private byte[] uploadedDocument;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "document_type")
    private String documentType;

    @Lob
    @Column(name = "workflow_data")
    private String workflowData;

    @Column(name = "review_decision")
    private String reviewDecision;

    @Lob
    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "approval_decision")
    private String approvalDecision;

    @Lob
    @Column(name = "approval_notes")
    private String approvalNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "node_statuses")
    private String nodeStatuses;

    @Column(name = "current_node_type")
    private String currentNodeType;

    @Column(name = "required_role")
    private String requiredRole;

    // Constructors
    public WorkflowExecutionEntity() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = "In Progress";
        this.currentNodeIndex = 0;
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

    public Integer getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public void setCurrentNodeIndex(Integer currentNodeIndex) {
        this.currentNodeIndex = currentNodeIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }

    public void setUploadedFileName(String uploadedFileName) {
        this.uploadedFileName = uploadedFileName;
    }

    public byte[] getUploadedDocument() {
        return uploadedDocument;
    }

    public void setUploadedDocument(byte[] uploadedDocument) {
        this.uploadedDocument = uploadedDocument;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getNodeStatuses() {
        return nodeStatuses;
    }

    public void setNodeStatuses(String nodeStatuses) {
        this.nodeStatuses = nodeStatuses;
    }

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

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}