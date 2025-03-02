package com.example.workflow.views;

import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route("workflow-use")
public class WorkflowUseView extends VerticalLayout implements HasUrlParameter<Long> {
    
    private final WorkflowJsonRepository workflowJsonRepository;
    private List<Map<String, Object>> workflowNodes;
    private WorkflowJsonEntity workflowEntity;
    private HorizontalLayout workflowProgress;
    private final Map<String, Object> workflowData = new HashMap<>();
    private int currentNodeIndex = 0;
    
    // Track uploaded documents and workflow state
    private byte[] uploadedDocument;
    private String uploadedFileName;
    private String workflowStatus = "In Progress";
    private Map<String, String> nodeStatuses = new HashMap<>();
    
    public WorkflowUseView(WorkflowJsonRepository workflowJsonRepository) {
        this.workflowJsonRepository = workflowJsonRepository;
        
        // Set up basic layout
        setSizeFull();
        setSpacing(true);
        setPadding(true);
        
        // Header
        H2 header = new H2("Workflow Execution");
        header.getStyle().set("margin-top", "0");
        
        // Create the progress bar layout
        workflowProgress = new HorizontalLayout();
        workflowProgress.setWidthFull();
        workflowProgress.getStyle()
                .set("background-color", "#f5f5f5")
                .set("padding", "1rem")
                .set("border-radius", "4px")
                .set("margin-bottom", "1rem");
        
        // Back to workflows button
        Button backButton = new Button("Back to Workflows");
        backButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));
        
        add(header, backButton, workflowProgress);
    }
    
    @Override
    public void setParameter(BeforeEvent event, Long workflowId) {
        if (workflowId == null) {
            Notification.show("No workflow specified");
            UI.getCurrent().navigate(WorkflowViewerView.class);
            return;
        }
        
        workflowJsonRepository.findById(workflowId).ifPresentOrElse(
                this::loadWorkflow,
                () -> {
                    Notification.show("Workflow not found");
                    UI.getCurrent().navigate(WorkflowViewerView.class);
                }
        );
    }
    
    private void loadWorkflow(WorkflowJsonEntity entity) {
        this.workflowEntity = entity;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            workflowNodes = mapper.readValue(
                    entity.getData(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            
            // Initialize node statuses
            workflowNodes.forEach(node -> {
                String nodeName = (String) node.get("name");
                nodeStatuses.put(nodeName, "Pending");
            });
            
            // Set up workflow steps in the UI
            renderWorkflowSteps();
            
            // Display current node
            displayCurrentNode();
            
        } catch (Exception e) {
            Notification.show("Error loading workflow: " + e.getMessage());
        }
    }
    
    private void renderWorkflowSteps() {
        workflowProgress.removeAll();
        
        for (int i = 0; i < workflowNodes.size(); i++) {
            Map<String, Object> node = workflowNodes.get(i);
            String nodeName = (String) node.get("name");
            String nodeType = (String) node.get("type");
            String status = nodeStatuses.getOrDefault(nodeName, "Pending");
            
            Div stepDiv = new Div();
            stepDiv.setText(nodeName);
            stepDiv.addClassName("workflow-step");
            
            // Style based on status and current step
            stepDiv.getStyle()
                    .set("padding", "0.5rem 1rem")
                    .set("border-radius", "4px")
                    .set("margin-right", "0.5rem")
                    .set("cursor", "pointer");
            
            switch (status) {
                case "Completed":
                    stepDiv.getStyle().set("background-color", "#4CAF50").set("color", "white");
                    break;
                case "In Progress":
                    stepDiv.getStyle().set("background-color", "#2196F3").set("color", "white");
                    break;
                case "Rejected":
                    stepDiv.getStyle().set("background-color", "#F44336").set("color", "white");
                    break;
                default:
                    stepDiv.getStyle().set("background-color", "#E0E0E0").set("color", "black");
            }
            
            // Set current step style
            if (i == currentNodeIndex) {
                stepDiv.getStyle().set("border", "2px solid #000");
            }
            
            // Add chevron separator if not the last item
            if (i < workflowNodes.size() - 1) {
                Div chevron = new Div();
                chevron.setText("›");
                chevron.getStyle()
                        .set("margin", "0 0.5rem")
                        .set("font-size", "1.5rem")
                        .set("color", "#666");
                
                workflowProgress.add(stepDiv, chevron);
            } else {
                workflowProgress.add(stepDiv);
            }
        }
    }
    
    private void displayCurrentNode() {
        // Remove any existing node content
        getChildren()
                .filter(component -> component.getClass() != H2.class 
                        && component.getClass() != Button.class
                        && component != workflowProgress)
                .forEach(this::remove);
        
        if (currentNodeIndex >= workflowNodes.size()) {
            // Workflow is complete
            Paragraph complete = new Paragraph("Workflow is complete");
            complete.getStyle().set("font-size", "1.2rem");
            add(complete);
            return;
        }
        
        Map<String, Object> currentNode = workflowNodes.get(currentNodeIndex);
        String nodeType = (String) currentNode.get("type");
        String nodeName = (String) currentNode.get("name");
        
        // Update status
        nodeStatuses.put(nodeName, "In Progress");
        renderWorkflowSteps();
        
        // Create a card-like container for the node UI
        Div nodeCard = new Div();
        nodeCard.getStyle()
                .set("background-color", "white")
                .set("padding", "1.5rem")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
                .set("width", "100%")
                .set("max-width", "800px")
                .set("margin", "0 auto");
        
        H3 nodeTitle = new H3(nodeName);
        nodeCard.add(nodeTitle);
        
        // Add description if available
        if (currentNode.get("description") != null) {
            Paragraph descPara = new Paragraph((String) currentNode.get("description"));
            nodeCard.add(descPara);
        }
        
        // Create node-specific interface
        Component nodeInterface = null;
        
        switch (nodeType) {
            case "Upload":
                nodeInterface = createUploadInterface(currentNode);
                break;
            case "Document Review":
                nodeInterface = createReviewInterface(currentNode);
                break;
            case "Approve/Reject":
                nodeInterface = createApprovalInterface(currentNode);
                break;
            case "Custom Field":
                nodeInterface = createCustomFieldInterface(currentNode);
                break;
            default:
                nodeInterface = new Paragraph("Unknown node type: " + nodeType);
        }
        
        nodeCard.add(nodeInterface);
        add(nodeCard);
    }
    
    private Component createUploadInterface(Map<String, Object> node) {
    VerticalLayout layout = new VerticalLayout();
    layout.setSpacing(true);
    layout.setPadding(false);
    
    MemoryBuffer buffer = new MemoryBuffer();
    Upload upload = new Upload(buffer);
    upload.setAcceptedFileTypes("application/pdf", "image/*", "application/msword", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    
    // Create the next button early so we can reference it
    Button nextButton = new Button("Next");
    nextButton.setEnabled(false); // Disabled until file is uploaded
    
    upload.addSucceededListener(event -> {
        try {
            // Save uploaded file to workflow data
            InputStream inputStream = buffer.getInputStream();
            byte[] bytes = inputStream.readAllBytes();
            uploadedDocument = bytes;
            uploadedFileName = event.getFileName();
            
            // Save additional document metadata
            Map<String, Object> docData = new HashMap<>();
            docData.put("fileName", uploadedFileName);
            docData.put("mimeType", event.getMIMEType());
            docData.put("size", event.getContentLength());
            
            workflowData.put("uploadedDocument", docData);
            
            Notification.show("Document uploaded: " + uploadedFileName);
            
            // Enable next button - fixed line
            nextButton.setEnabled(true);
        } catch (Exception e) {
            Notification.show("Error processing file: " + e.getMessage());
        }
    });
    
    // Add specific properties from node if they exist
    @SuppressWarnings("unchecked")
    Map<String, String> props = (Map<String, String>) node.get("props");
    if (props != null) {
        String docType = props.getOrDefault("documentType", "");
        if (!docType.isEmpty()) {
            Paragraph typeInfo = new Paragraph("Document Type: " + docType);
            layout.add(typeInfo);
        }
    }
    
    nextButton.addClickListener(e -> {
        // Mark current node as completed
        nodeStatuses.put((String) node.get("name"), "Completed");
        moveToNextNode();
    });
    
    layout.add(new Paragraph("Please upload a document to continue:"), upload, nextButton);
    return layout;
}
    
    private Component createReviewInterface(Map<String, Object> node) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);
        
        if (uploadedDocument == null) {
            Paragraph error = new Paragraph("No document has been uploaded");
            error.getStyle().set("color", "red");
            layout.add(error);
            
            Button skipButton = new Button("Skip Review");
            skipButton.addClickListener(e -> {
                nodeStatuses.put((String) node.get("name"), "Skipped");
                moveToNextNode();
            });
            
            layout.add(skipButton);
            return layout;
        }
        
        // Document viewer
        Button viewDocButton = new Button("View Document");
        viewDocButton.addClickListener(e -> {
            showDocumentViewer(uploadedFileName, uploadedDocument);
        });
        
        // Review notes field
        TextArea notes = new TextArea("Review Notes");
        notes.setWidthFull();
        notes.setMinHeight("150px");
        
        // Review decisions
        Button returnButton = new Button("Return for Changes");
        returnButton.getStyle().set("background-color", "#FFC107");
        returnButton.addClickListener(e -> {
            // Save notes to workflow data
            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Return");
            
            // Update status and move back to upload
            nodeStatuses.put((String) node.get("name"), "Returned");
            for (int i = 0; i < workflowNodes.size(); i++) {
                if ("Upload".equals(workflowNodes.get(i).get("type"))) {
                    currentNodeIndex = i;
                    break;
                }
            }
            displayCurrentNode();
        });
        
        Button completeReviewButton = new Button("Complete Review");
        completeReviewButton.getStyle().set("background-color", "#4CAF50").set("color", "white");
        completeReviewButton.addClickListener(e -> {
            // Save notes to workflow data
            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Complete");
            
            // Mark node as completed and move to next
            nodeStatuses.put((String) node.get("name"), "Completed");
            moveToNextNode();
        });
        
        // Show review-specific properties
        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) node.get("props");
        if (props != null) {
            Paragraph docInfo = new Paragraph("Document: " + uploadedFileName);
            Paragraph reviewerInfo = new Paragraph("Reviewer Role: " + 
                    props.getOrDefault("reviewerRole", "Not specified"));
            
            layout.add(docInfo, reviewerInfo);
        }
        
        HorizontalLayout buttons = new HorizontalLayout(returnButton, completeReviewButton);
        layout.add(viewDocButton, notes, buttons);
        return layout;
    }
    
    private Component createApprovalInterface(Map<String, Object> node) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);
        
        if (uploadedDocument == null) {
            Paragraph error = new Paragraph("No document has been uploaded for approval");
            error.getStyle().set("color", "red");
            layout.add(error);
            
            Button skipButton = new Button("Skip Approval");
            skipButton.addClickListener(e -> {
                nodeStatuses.put((String) node.get("name"), "Skipped");
                moveToNextNode();
            });
            
            layout.add(skipButton);
            return layout;
        }
        
        // Document viewer
        Button viewDocButton = new Button("View Document");
        viewDocButton.addClickListener(e -> {
            showDocumentViewer(uploadedFileName, uploadedDocument);
        });
        
        // Review notes from previous step
        String reviewNotes = (String) workflowData.getOrDefault("reviewNotes", "");
        if (!reviewNotes.isEmpty()) {
            TextArea notesArea = new TextArea("Review Notes");
            notesArea.setValue(reviewNotes);
            notesArea.setReadOnly(true);
            notesArea.setWidthFull();
            layout.add(notesArea);
        }
        
        TextArea approvalNotes = new TextArea("Approval Notes");
        approvalNotes.setWidthFull();
        
        // Approval buttons
        Button rejectButton = new Button("Reject");
        rejectButton.getStyle().set("background-color", "#F44336").set("color", "white");
        rejectButton.addClickListener(e -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setCloseOnEsc(false);
            confirmDialog.setCloseOnOutsideClick(false);
            
            VerticalLayout confirmLayout = new VerticalLayout();
            confirmLayout.add(new H3("Confirm Rejection"));
            confirmLayout.add(new Paragraph("Are you sure you want to reject this document?"));
            
            Button cancelButton = new Button("Cancel", event -> confirmDialog.close());
            Button confirmButton = new Button("Confirm Reject", event -> {
                workflowData.put("approvalNotes", approvalNotes.getValue());
                workflowData.put("approvalDecision", "Rejected");
                
                // Update workflow status
                workflowStatus = "Rejected";
                nodeStatuses.put((String) node.get("name"), "Rejected");
                
                confirmDialog.close();
                
                // Show rejection notification
                Notification.show("Document rejected");
                
                // End workflow or redirect to completed view
                showWorkflowSummary();
            });
            confirmButton.getStyle().set("background-color", "#F44336").set("color", "white");
            
            HorizontalLayout buttonLayout = new HorizontalLayout(cancelButton, confirmButton);
            confirmLayout.add(buttonLayout);
            
            confirmDialog.add(confirmLayout);
            confirmDialog.open();
        });
        
        Button approveButton = new Button("Approve");
        approveButton.getStyle().set("background-color", "#4CAF50").set("color", "white");
        approveButton.addClickListener(e -> {
            workflowData.put("approvalNotes", approvalNotes.getValue());
            workflowData.put("approvalDecision", "Approved");
            
            // Mark node as completed and finish workflow
            nodeStatuses.put((String) node.get("name"), "Completed");
            workflowStatus = "Completed";
            
            Notification.show("Document approved!");
            moveToNextNode();
        });
        
        // Show approval-specific properties
        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) node.get("props");
        if (props != null) {
            Paragraph docInfo = new Paragraph("Document: " + uploadedFileName);
            Paragraph approverInfo = new Paragraph("Approver Role: " + 
                    props.getOrDefault("Approver Role", "Not specified"));
            
            layout.add(docInfo, approverInfo);
        }
        
        HorizontalLayout buttonLayout = new HorizontalLayout(rejectButton, approveButton);
        layout.add(viewDocButton, approvalNotes, buttonLayout);
        return layout;
    }
    
    private Component createCustomFieldInterface(Map<String, Object> node) {
        VerticalLayout layout = new VerticalLayout();
        
        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) node.get("props");
        if (props != null) {
            String label = props.getOrDefault("label", "Custom Field");
            String value = props.getOrDefault("value", "");
            
            TextArea customField = new TextArea(label);
            customField.setValue(value);
            customField.setWidthFull();
            
            Button saveButton = new Button("Save & Continue");
            saveButton.addClickListener(e -> {
                // Save custom field data
                workflowData.put("customField_" + label, customField.getValue());
                
                // Mark node as completed and move to next
                nodeStatuses.put((String) node.get("name"), "Completed");
                moveToNextNode();
            });
            
            layout.add(customField, saveButton);
        } else {
            layout.add(new Paragraph("No field properties defined"));
            Button skipButton = new Button("Skip");
            skipButton.addClickListener(e -> {
                nodeStatuses.put((String) node.get("name"), "Skipped");
                moveToNextNode();
            });
            layout.add(skipButton);
        }
        
        return layout;
    }
    
    private void moveToNextNode() {
        currentNodeIndex++;
        
        if (currentNodeIndex >= workflowNodes.size()) {
            // Workflow is complete
            showWorkflowSummary();
        } else {
            displayCurrentNode();
        }
    }
    
    private void showWorkflowSummary() {
        // Clear existing content
        getChildren()
                .filter(component -> component.getClass() != H2.class 
                        && component.getClass() != Button.class
                        && component != workflowProgress)
                .forEach(this::remove);
        
        // Update all nodes as completed except any that are rejected
        for (Map<String, Object> node : workflowNodes) {
            String nodeName = (String) node.get("name");
            if (!"Rejected".equals(nodeStatuses.get(nodeName))) {
                nodeStatuses.put(nodeName, "Completed");
            }
        }
        
        // Refresh the progress bar
        renderWorkflowSteps();
        
        // Create summary card
        Div summaryCard = new Div();
        summaryCard.getStyle()
                .set("background-color", "white")
                .set("padding", "1.5rem")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
                .set("width", "100%")
                .set("max-width", "800px")
                .set("margin", "0 auto");
        
        H3 summaryTitle = new H3("Workflow Summary");
        
        // Status indicator
        Div statusIndicator = new Div();
        statusIndicator.setText("Status: " + workflowStatus);
        statusIndicator.getStyle()
                .set("padding", "0.5rem 1rem")
                .set("border-radius", "4px")
                .set("display", "inline-block")
                .set("margin-bottom", "1rem");
        
        if ("Completed".equals(workflowStatus)) {
            statusIndicator.getStyle()
                    .set("background-color", "#4CAF50")
                    .set("color", "white");
        } else if ("Rejected".equals(workflowStatus)) {
            statusIndicator.getStyle()
                    .set("background-color", "#F44336")
                    .set("color", "white");
        } else {
            statusIndicator.getStyle()
                    .set("background-color", "#FFC107");
        }
        
        // Document information
        Paragraph docInfo = new Paragraph("Document: " + uploadedFileName);
        
        // Decision information
        String reviewDecision = (String) workflowData.getOrDefault("reviewDecision", "N/A");
        String approvalDecision = (String) workflowData.getOrDefault("approvalDecision", "N/A");
        
        Paragraph reviewInfo = new Paragraph("Review Decision: " + reviewDecision);
        Paragraph approvalInfo = new Paragraph("Approval Decision: " + approvalDecision);
        
        // Notes
        String reviewNotes = (String) workflowData.getOrDefault("reviewNotes", "");
        String approvalNotes = (String) workflowData.getOrDefault("approvalNotes", "");
        
        VerticalLayout notesLayout = new VerticalLayout();
        notesLayout.setPadding(false);
        notesLayout.setSpacing(true);
        
        if (!reviewNotes.isEmpty()) {
            TextArea reviewNotesArea = new TextArea("Review Notes");
            reviewNotesArea.setValue(reviewNotes);
            reviewNotesArea.setReadOnly(true);
            reviewNotesArea.setWidthFull();
            notesLayout.add(reviewNotesArea);
        }
        
        if (!approvalNotes.isEmpty()) {
            TextArea approvalNotesArea = new TextArea("Approval Notes");
            approvalNotesArea.setValue(approvalNotes);
            approvalNotesArea.setReadOnly(true);
            approvalNotesArea.setWidthFull();
            notesLayout.add(approvalNotesArea);
        }
        
        // Actions
        Button backToWorkflowsButton = new Button("Back to Workflows");
        backToWorkflowsButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));
        
        Button startNewButton = new Button("Start New");
        startNewButton.getStyle().set("background-color", "#2196F3").set("color", "white");
        startNewButton.addClickListener(e -> {
            // Reset workflow state and start over
            currentNodeIndex = 0;
            uploadedDocument = null;
            uploadedFileName = null;
            workflowData.clear();
            workflowStatus = "In Progress";
            
            // Reset node statuses
            for (Map<String, Object> node : workflowNodes) {
                nodeStatuses.put((String) node.get("name"), "Pending");
            }
            
            displayCurrentNode();
        });
        
        // Add everything to summary card
        summaryCard.add(
                summaryTitle, 
                statusIndicator, 
                docInfo, 
                reviewInfo, 
                approvalInfo, 
                notesLayout,
                new HorizontalLayout(backToWorkflowsButton, startNewButton)
        );
        
        add(summaryCard);
    }
    
    private void showDocumentViewer(String fileName, byte[] content) {
    Dialog viewerDialog = new Dialog();
    viewerDialog.setWidth("80%");
    viewerDialog.setHeight("80%");
    
    VerticalLayout dialogLayout = new VerticalLayout();
    dialogLayout.setSizeFull();
    dialogLayout.setPadding(false);
    
    H3 docTitle = new H3("Document: " + fileName);
    
    // Simple document viewer
    Div docViewer = new Div();
    docViewer.setSizeFull();
    docViewer.getStyle()
            .set("overflow", "auto")
            .set("background-color", "#f5f5f5")
            .set("padding", "1rem")
            .set("border", "1px solid #ddd");
    
    // Handle different document types
    if (fileName.toLowerCase().endsWith(".pdf")) {
        // For PDF, we use an anchor with download attribute and target="_blank"
        StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
        com.vaadin.flow.component.html.Anchor pdfLink = new com.vaadin.flow.component.html.Anchor(resource, "View PDF");
        pdfLink.setTarget("_blank");
        
        Paragraph pdfInfo = new Paragraph("PDF document. Click below to view:");
        docViewer.add(pdfInfo, pdfLink);
    } else if (fileName.toLowerCase().endsWith(".jpg") || 
            fileName.toLowerCase().endsWith(".jpeg") || 
            fileName.toLowerCase().endsWith(".png") || 
            fileName.toLowerCase().endsWith(".gif")) {
        // For images
        StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
        com.vaadin.flow.component.html.Image image = new com.vaadin.flow.component.html.Image(resource, "Document");
        image.setMaxWidth("100%");
        docViewer.add(image);
    } else {
        // For other file types, show a download link
        Paragraph unsupportedMsg = new Paragraph("Preview not available for this file type.");
        StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
        com.vaadin.flow.component.html.Anchor downloadLink = 
                new com.vaadin.flow.component.html.Anchor(resource, "Download");
        downloadLink.getElement().setAttribute("download", true);
        docViewer.add(unsupportedMsg, downloadLink);
    }
    
    Button closeButton = new Button("Close");
    closeButton.addClickListener(e -> viewerDialog.close());
    
    dialogLayout.add(docTitle, docViewer, closeButton);
    dialogLayout.expand(docViewer);
    
    viewerDialog.add(dialogLayout);
    viewerDialog.open();
}
}