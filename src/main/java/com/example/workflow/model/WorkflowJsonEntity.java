package com.example.workflow.model;

import jakarta.persistence.*;

@Entity
@Table(name = "workflow_json")
public class WorkflowJsonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // optional descriptive name

    @Column(name = "data", columnDefinition = "text")
    private String data; // entire workflow in JSON

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
