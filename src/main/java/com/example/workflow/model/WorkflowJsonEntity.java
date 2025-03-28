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

    public String getDocumentType() {
    try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.List<java.util.Map<String, Object>> workflowNodes = mapper.readValue(
            this.data,
            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
        );

        System.out.println("Parsed workflow nodes: " + workflowNodes);

        for (java.util.Map<String, Object> node : workflowNodes) {
            System.out.println("Node type: " + node.get("type"));
            if ("Upload".equals(node.get("type"))) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> props = (java.util.Map<String, String>) node.get("props");
                System.out.println("Upload node props: " + props);
                if (props != null && props.containsKey("documentType")) {
                    return props.get("documentType");
                }
            }
        }
        
        // If we reach here, no document type was found
        System.out.println("No document type found in workflow JSON");
        
    } catch (Exception e) {
        // Log the exception for debugging
        System.err.println("Error parsing workflow JSON: " + e.getMessage());
        e.printStackTrace();
    }
    return null;
}
}
