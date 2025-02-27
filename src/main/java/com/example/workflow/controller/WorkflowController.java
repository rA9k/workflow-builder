package com.example.workflow.controller;

import com.example.workflow.model.Workflow;
import com.example.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @GetMapping
    public List<Workflow> getAllWorkflows() {
        return workflowService.getAllWorkflows();
    }

    @PostMapping
    public Workflow createWorkflow(@RequestBody Workflow workflow) {
        return workflowService.createWorkflow(workflow);
    }
}
