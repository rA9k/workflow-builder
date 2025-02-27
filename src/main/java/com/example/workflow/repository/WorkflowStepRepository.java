package com.example.workflow.repository;

import com.example.workflow.model.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {
}
