package com.example.workflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowJsonEntity;

@Repository
public interface WorkflowJsonRepository extends JpaRepository<WorkflowJsonEntity, Long> {
    List<WorkflowJsonEntity> findByOrganization(OrganizationEntity organization);
}
