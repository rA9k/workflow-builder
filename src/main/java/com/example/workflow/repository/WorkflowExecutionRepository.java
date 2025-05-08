package com.example.workflow.repository;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {
    @Transactional
    List<WorkflowExecutionEntity> findByWorkflow(WorkflowJsonEntity workflow);

    List<WorkflowExecutionEntity> findByStatus(String status);

    List<WorkflowExecutionEntity> findByCreatedBy(String username);

    List<WorkflowExecutionEntity> findAllByOrderByUpdatedAtDesc();

    List<WorkflowExecutionEntity> findByOrganizationOrderByUpdatedAtDesc(OrganizationEntity organization);

    Optional<WorkflowExecutionEntity> findByIdAndOrganization(Long id, OrganizationEntity organization);

    List<WorkflowExecutionEntity> findByWorkflowAndOrganization(WorkflowJsonEntity workflow,
            OrganizationEntity organization);
}