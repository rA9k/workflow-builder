package com.example.workflow.service;

import com.example.workflow.model.Workflow;
import com.example.workflow.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowService {

    @Autowired
    private WorkflowRepository workflowRepository;

    public List<Workflow> getAllWorkflows() {
        return workflowRepository.findAll();
    }

    public Workflow createWorkflow(Workflow workflow) {
        return workflowRepository.save(workflow);
    }
}
