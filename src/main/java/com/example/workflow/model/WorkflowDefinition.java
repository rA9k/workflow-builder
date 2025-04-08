package com.example.workflow.model;

import com.example.workflow.components.nodes.WorkflowNode;
import com.example.workflow.components.nodes.WorkflowNodeFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a workflow definition with its nodes and properties
 */
public class WorkflowDefinition {

    private Long id;
    private String name;
    private List<WorkflowNode> nodes = new ArrayList<>();
    private WorkflowJsonEntity entity;

    /**
     * Create an empty workflow definition
     */
    public WorkflowDefinition() {
    }

    /**
     * Create a workflow definition from a database entity
     * 
     * @param entity The workflow JSON entity
     * @throws RuntimeException if there's an error parsing the workflow data
     */
    public WorkflowDefinition(WorkflowJsonEntity entity) {
        this.entity = entity;
        this.id = entity.getId();
        this.name = entity.getName();

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodesData = mapper.readValue(
                    entity.getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> nodeData : nodesData) {
                WorkflowNode node = WorkflowNodeFactory.fromMap(nodeData);
                nodes.add(node);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing workflow definition", e);
        }
    }

    /**
     * Get the workflow ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Set the workflow ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Get the workflow name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the workflow name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get all nodes in the workflow
     */
    public List<WorkflowNode> getNodes() {
        return nodes;
    }

    /**
     * Set the workflow nodes
     */
    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * Add a node to the workflow
     */
    public void addNode(WorkflowNode node) {
        this.nodes.add(node);
    }

    /**
     * Get the number of nodes in the workflow
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Get a node at a specific index
     * 
     * @param index The index of the node
     * @return The node at the specified index, or null if the index is out of
     *         bounds
     */
    public WorkflowNode getNodeAt(int index) {
        if (index < 0 || index >= nodes.size()) {
            return null;
        }
        return nodes.get(index);
    }

    /**
     * Get the original entity this definition was created from
     */
    public WorkflowJsonEntity getEntity() {
        return entity;
    }

    /**
     * Set the original entity
     */
    public void setEntity(WorkflowJsonEntity entity) {
        this.entity = entity;
    }

    /**
     * Convert the workflow definition back to JSON
     * 
     * @return JSON string representation of the workflow
     * @throws RuntimeException if there's an error serializing the workflow
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodesData = new ArrayList<>();

            for (int i = 0; i < nodes.size(); i++) {
                WorkflowNode node = nodes.get(i);
                Map<String, Object> nodeMap = Map.of(
                        "name", node.getName(),
                        "type", node.getType(),
                        "description", node.getDescription(),
                        "props", node.getProperties(),
                        "order", i);
                nodesData.add(nodeMap);
            }

            return mapper.writeValueAsString(nodesData);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing workflow definition", e);
        }
    }
}
