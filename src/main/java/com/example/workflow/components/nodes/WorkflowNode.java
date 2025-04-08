package com.example.workflow.components.nodes;

import com.vaadin.flow.component.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all workflow node types
 */
public abstract class WorkflowNode {

    protected String name;
    protected String description;
    protected Map<String, String> properties = new HashMap<>();
    protected String status = "Pending";

    /**
     * Get the node type
     * 
     * @return The type of this node
     */
    public abstract String getType();

    /**
     * Create a component for displaying this node in the workflow designer
     * 
     * @return A component representing this node in the designer
     */
    public abstract Component createDesignerComponent();

    /**
     * Create a component for executing this node in a workflow
     * 
     * @param executionContext The execution context containing workflow state
     * @return A component for interacting with this node during execution
     */
    public abstract Component createExecutionComponent(Map<String, Object> executionContext);

    /**
     * Get the node name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the node name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the node description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the node description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the node properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Set the node properties
     */
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    /**
     * Add a property to this node
     */
    public void addProperty(String key, String value) {
        this.properties.put(key, value);
    }

    /**
     * Get the current execution status of this node
     */
    public String getStatus() {
        return status;
    }

    /**
     * Set the execution status of this node
     */
    public void setStatus(String status) {
        this.status = status;
    }
}
