package com.example.workflow.components.nodes;

import java.util.Map;

/**
 * Factory class for creating workflow node instances based on type
 */
public class WorkflowNodeFactory {

    /**
     * Creates a new workflow node instance based on the specified type
     * 
     * @param type The type of node to create
     * @return A new instance of the appropriate WorkflowNode subclass
     * @throws IllegalArgumentException if the type is not recognized
     */
    public static WorkflowNode createNode(String type) {
        return switch (type) {
            case "Upload" -> new UploadNode();
            case "Document Review" -> new ReviewNode();
            case "Approve/Reject" -> new ApprovalNode();
            case "Custom Field" -> new CustomFieldNode();
            default -> throw new IllegalArgumentException("Unknown node type: " + type);
        };
    }

    /**
     * Creates a workflow node from a map of properties
     * 
     * @param nodeData Map containing node data
     * @return Configured workflow node instance
     */
    public static WorkflowNode fromMap(Map<String, Object> nodeData) {
        String type = (String) nodeData.get("type");
        WorkflowNode node = createNode(type);

        node.setName((String) nodeData.get("name"));
        node.setDescription((String) nodeData.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) nodeData.get("props");
        if (props != null) {
            node.setProperties(props);
        }

        return node;
    }
}
