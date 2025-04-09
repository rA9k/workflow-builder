package com.example.workflow.components;

import com.example.workflow.components.nodes.WorkflowNode;
import com.example.workflow.model.WorkflowDefinition;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.service.WorkflowExecutionEngine;
import com.example.workflow.views.WorkflowUseView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.Map;
import java.util.Optional;

/**
 * Reusable component for displaying and interacting with a workflow execution
 */
public class WorkflowExecutionComponent extends VerticalLayout {

    private final WorkflowExecutionEntity execution;
    private final WorkflowDefinition definition;
    private final WorkflowExecutionEngine executionEngine;
    private Map<String, Object> executionContext;

    private Div contentArea;

    public WorkflowExecutionComponent(
            WorkflowExecutionEntity execution,
            WorkflowDefinition definition,
            WorkflowExecutionEngine executionEngine,
            Runnable refreshProgressCallback) {

        this.execution = execution;
        this.definition = definition;
        this.executionEngine = executionEngine;

        setSizeFull();
        addClassName("workflow-execution-component");

        // Create workflow header
        H3 header = new H3("Workflow: " + definition.getName());
        add(header);

        // Create content area
        contentArea = new Div();
        contentArea.setSizeFull();
        contentArea.getStyle()
                .set("background-color", "white")
                .set("padding", "1.5rem")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)");
        add(contentArea);

        // Initialize execution context
        executionContext = executionEngine.createExecutionContext(execution);

        // Create a direct reference to the WorkflowUseView for complete refresh
        executionContext.put("forceCompleteRefresh", (Runnable) () -> {
            UI.getCurrent().access(() -> {
                // Find the WorkflowUseView instance
                Optional<WorkflowUseView> viewOpt = UI.getCurrent().getChildren()
                        .filter(component -> component instanceof WorkflowUseView)
                        .map(component -> (WorkflowUseView) component)
                        .findFirst();

                if (viewOpt.isPresent()) {
                    viewOpt.get().forceCompleteRefresh(execution.getId());
                } else {
                    // Fallback to the provided callback
                    refreshProgressCallback.run();
                }
            });
        });

        // Add completion handler
        executionContext.put("onComplete", (Runnable) this::moveToNextNode);

        // Display the current node
        displayCurrentNode();
    }

    /**
     * Display the current node's execution component
     */
    private void displayCurrentNode() {
        contentArea.removeAll();

        int currentNodeIndex = execution.getCurrentNodeIndex();

        // Check if workflow is completed
        if (currentNodeIndex >= definition.getNodeCount()) {
            showWorkflowSummary();
            return;
        }

        // Get current node and create its execution component
        WorkflowNode currentNode = definition.getNodeAt(currentNodeIndex);
        if (currentNode == null) {
            contentArea.add(new Paragraph("Error: Invalid node index"));
            return;
        }

        // Create node component
        Component nodeComponent = currentNode.createExecutionComponent(executionContext);
        contentArea.add(nodeComponent);
    }

    /**
     * Move to the next node in the workflow
     */
    private void moveToNextNode() {
        try {
            // Disable UI interactions during processing
            contentArea.setEnabled(false);

            // Update the execution with current context
            executionEngine.advanceWorkflow(execution, executionContext);

            // Force a complete refresh of the UI
            if (executionContext.containsKey("forceCompleteRefresh")) {
                ((Runnable) executionContext.get("forceCompleteRefresh")).run();
            }

        } catch (Exception e) {
            Notification.show("Error advancing workflow: " + e.getMessage());
            contentArea.setEnabled(true);
        }
    }

    /**
     * Display workflow summary when completed
     */
    private void showWorkflowSummary() {
        contentArea.removeAll();

        VerticalLayout summaryLayout = new VerticalLayout();
        summaryLayout.setPadding(false);
        summaryLayout.setSpacing(true);

        H3 summaryTitle = new H3("Workflow Summary");

        Div statusIndicator = new Div();
        statusIndicator.setText("Status: " + execution.getStatus());
        statusIndicator.getStyle()
                .set("padding", "0.5rem 1rem")
                .set("border-radius", "4px")
                .set("display", "inline-block")
                .set("margin-bottom", "1rem");

        if ("Completed".equals(execution.getStatus())) {
            statusIndicator.getStyle().set("background-color", "#4CAF50").set("color", "white");
        } else if ("Rejected".equals(execution.getStatus())) {
            statusIndicator.getStyle().set("background-color", "#F44336").set("color", "white");
        } else {
            statusIndicator.getStyle().set("background-color", "#FFC107");
        }

        // Add document info if available
        if (execution.getUploadedFileName() != null) {
            Paragraph docInfo = new Paragraph("Document: " + execution.getUploadedFileName());
            summaryLayout.add(docInfo);

            // Add document download button
            if (execution.getUploadedDocument() != null) {
                Button downloadButton = new Button("Download Document");
                downloadButton.getStyle().set("background-color", "#2196F3").set("color", "white");

                com.vaadin.flow.server.StreamResource resource = new com.vaadin.flow.server.StreamResource(
                        execution.getUploadedFileName(),
                        () -> new java.io.ByteArrayInputStream(execution.getUploadedDocument()));

                com.vaadin.flow.component.html.Anchor downloadLink = new com.vaadin.flow.component.html.Anchor(resource,
                        "");
                downloadLink.getElement().setAttribute("download", true);
                downloadLink.add(downloadButton);

                summaryLayout.add(downloadLink);
            }
        }

        // Add workflow data summary
        try {
            Map<String, Object> workflowData = execution.getWorkflowDataAsMap();

            if (workflowData.containsKey("reviewDecision")) {
                Paragraph reviewInfo = new Paragraph("Review Decision: " + workflowData.get("reviewDecision"));
                summaryLayout.add(reviewInfo);
            }

            if (workflowData.containsKey("approvalDecision")) {
                Paragraph approvalInfo = new Paragraph("Approval Decision: " + workflowData.get("approvalDecision"));
                summaryLayout.add(approvalInfo);
            }

            // Add notes if available
            if (workflowData.containsKey("reviewNotes") && !((String) workflowData.get("reviewNotes")).isEmpty()) {
                TextArea reviewNotesArea = new TextArea("Review Notes");
                reviewNotesArea.setValue((String) workflowData.get("reviewNotes"));
                reviewNotesArea.setReadOnly(true);
                reviewNotesArea.setWidthFull();
                summaryLayout.add(reviewNotesArea);
            }

            if (workflowData.containsKey("approvalNotes") && !((String) workflowData.get("approvalNotes")).isEmpty()) {
                TextArea approvalNotesArea = new TextArea("Approval Notes");
                approvalNotesArea.setValue((String) workflowData.get("approvalNotes"));
                approvalNotesArea.setReadOnly(true);
                approvalNotesArea.setWidthFull();
                summaryLayout.add(approvalNotesArea);
            }

            // Add custom fields
            workflowData.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("customField_"))
                    .forEach(entry -> {
                        String fieldName = entry.getKey().substring("customField_".length());
                        TextArea fieldArea = new TextArea(fieldName);
                        fieldArea.setValue(entry.getValue().toString());
                        fieldArea.setReadOnly(true);
                        fieldArea.setWidthFull();
                        summaryLayout.add(fieldArea);
                    });
        } catch (Exception e) {
            summaryLayout.add(new Paragraph("Error loading workflow data: " + e.getMessage()));
        }

        // Add action buttons
        Button backButton = new Button("Back to Workflows");
        backButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate("workflow-viewer"));
        });

        Button startNewButton = new Button("Start New");
        startNewButton.getStyle().set("background-color", "#2196F3").set("color", "white");
        startNewButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate("workflow-use/new/" + definition.getId()));
        });

        HorizontalLayout buttonLayout = new HorizontalLayout(backButton, startNewButton);
        buttonLayout.setSpacing(true);

        summaryLayout.add(summaryTitle, statusIndicator, buttonLayout);
        contentArea.add(summaryLayout);
    }
}
