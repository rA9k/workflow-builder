package com.example.workflow.model;

import jakarta.persistence.*;

@Entity
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stepName;
    private String action;

    @ManyToOne
    @JoinColumn(name = "workflow_id")
    private Workflow workflow;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public String getStepName() {
        return stepName;
    }

    public String getAction() {
        return action;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }
}
