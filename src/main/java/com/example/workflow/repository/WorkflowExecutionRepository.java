package com.example.workflow.repository;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {
    @Transactional
    List<WorkflowExecutionEntity> findByWorkflow(WorkflowJsonEntity workflow);
    List<WorkflowExecutionEntity> findByStatus(String status);
    List<WorkflowExecutionEntity> findByCreatedBy(String username);
    List<WorkflowExecutionEntity> findAllByOrderByUpdatedAtDesc();
}