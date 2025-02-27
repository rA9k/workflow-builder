package com.example.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.workflow.model.WorkflowJsonEntity;

@Repository
public interface WorkflowJsonRepository extends JpaRepository<WorkflowJsonEntity, Long> {
}
