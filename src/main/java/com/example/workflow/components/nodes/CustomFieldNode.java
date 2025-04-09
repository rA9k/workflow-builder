package com.example.workflow.components.nodes;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.Map;

public class CustomFieldNode extends WorkflowNode {

    @Override
    public String getType() {
        return "Custom Field";
    }

    @Override
    public Component createDesignerComponent() {
        Button btn = new Button(getName());
        btn.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("border-left", "4px solid #9C27B0");
        return btn;
    }

    @Override
    public Component createExecutionComponent(Map<String, Object> executionContext) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);

        // Get field label from properties
        String label = properties.getOrDefault("label", "Custom Field");

        // Get workflow data
        Map<String, Object> workflowData = (Map<String, Object>) executionContext.get("workflowData");

        // Create field with existing value if available
        String fieldKey = "customField_" + label;
        String existingValue = "";
        if (workflowData != null && workflowData.containsKey(fieldKey)) {
            existingValue = (String) workflowData.get(fieldKey);
        } else {
            // Use default value from properties if no existing value
            existingValue = properties.getOrDefault("value", "");
        }

        TextArea customField = new TextArea(label);
        customField.setValue(existingValue);
        customField.setWidthFull();

        Button saveButton = new Button("Save & Continue");
        saveButton.getStyle().set("background-color", "#4CAF50").set("color", "white");
        saveButton.addClickListener(e -> {
            // Save the field value to workflow data
            workflowData.put(fieldKey, customField.getValue());
            this.status = "Completed";
            executionContext.put("advanceWorkflow", true);
        });

        Button skipButton = new Button("Skip");
        skipButton.addClickListener(e -> {
            this.status = "Skipped";
            executionContext.put("advanceWorkflow", true);
        });

        layout.add(customField, new HorizontalLayout(saveButton, skipButton));
        return layout;
    }
}
