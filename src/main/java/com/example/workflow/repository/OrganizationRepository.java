package com.example.workflow.repository;

import com.example.workflow.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
    Optional<OrganizationEntity> findByName(String name);
}