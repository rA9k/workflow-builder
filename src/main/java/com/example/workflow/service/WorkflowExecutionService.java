package com.example.workflow.service;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowExecutionService {

    private final WorkflowExecutionRepository workflowExecutionRepository;

    public WorkflowExecutionService(WorkflowExecutionRepository workflowExecutionRepository) {
        this.workflowExecutionRepository = workflowExecutionRepository;
    }

    @Transactional
    public void deleteWorkflowExecution(Long id) {
        workflowExecutionRepository.deleteById(id);
    }

    @Transactional
    public List<WorkflowExecutionEntity> getWorkflowExecutions() {
        return workflowExecutionRepository.findAllByOrderByUpdatedAtDesc();
    }

    public WorkflowExecutionEntity createWorkflowExecution(WorkflowJsonEntity workflow, String uploadedFileName, byte[] document, String mimeType, String createdBy) {
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setWorkflow(workflow);
        execution.setUploadedFileName(uploadedFileName);
        execution.setUploadedDocument(document);
        execution.setMimeType(mimeType);
        execution.setCreatedBy(createdBy);
        String documentType = extractDocumentTypeFromWorkflow(workflow);
        execution.setDocumentType(documentType);
        return workflowExecutionRepository.save(execution);
    }

    private String extractDocumentTypeFromWorkflow(WorkflowJsonEntity workflow) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodes = mapper.readValue(
                workflow.getData(),
                new TypeReference<List<Map<String, Object>>>() {}
            );
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
}
