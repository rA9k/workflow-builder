package com.example.workflow.components.nodes;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.service.WorkflowExecutionEngine;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UploadNode extends WorkflowNode {

    @Override
    public String getType() {
        return "Upload";
    }

    @Override
    public Component createDesignerComponent() {
        Button btn = new Button(getName());
        btn.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("border-left", "4px solid #4CAF50");
        return btn;
    }

    @Override
    public Component createExecutionComponent(Map<String, Object> executionContext) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.addClassName("workflow-node");
        layout.addClassName("upload-node");

        // Create node header with title and status
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.addClassName("node-header");

        H4 nodeTitle = new H4(getName().isEmpty() ? "Document Upload" : getName());
        nodeTitle.addClassName("node-title");

        Div statusBadge = new Div();
        statusBadge.setText(this.status != null && !this.status.isEmpty() ? this.status : "Pending");
        statusBadge.addClassName("node-status");
        if ("Completed".equals(this.status)) {
            statusBadge.addClassName("completed");
        } else if ("Pending".equals(this.status) || this.status == null) {
            statusBadge.addClassName("pending");
        }

        headerLayout.add(nodeTitle, statusBadge);
        layout.add(headerLayout);

        // Get workflow OPA service
        Object opaServiceObj = executionContext.get("opaService");
        if (!(opaServiceObj instanceof com.example.workflow.service.WorkflowOPAService)) {
            layout.add(new Paragraph("OPA service not available"));
            return layout;
        }

        com.example.workflow.service.WorkflowOPAService opaService = (com.example.workflow.service.WorkflowOPAService) opaServiceObj;

        // Get execution context
        Long workflowId = (Long) executionContext.get("workflowId");
        Long executionId = (Long) executionContext.get("executionId");
        String currentUsername = getCurrentUsername();

        // Check upload permissions using execution-specific policy
        String uploadPolicyPackage = "workflow_" + workflowId + "_" + executionId;
        boolean canUpload = opaService.isActionAllowed(uploadPolicyPackage, "upload", currentUsername, "username");

        if (!canUpload) {
            Div notAllowedDiv = new Div();
            notAllowedDiv.addClassName("notification-panel");
            notAllowedDiv.addClassName("danger");

            H3 notAllowedHeader = new H3("Access Denied");
            notAllowedHeader.getStyle().set("margin-top", "0");

            Paragraph notAllowedText = new Paragraph("You are not authorized to upload documents for this execution.");

            notAllowedDiv.add(notAllowedHeader, notAllowedText);
            layout.add(notAllowedDiv);
            return layout;
        }

        // Check if this is a re-upload after being returned for changes
        Map<String, Object> workflowData = (Map<String, Object>) executionContext.get("workflowData");
        String reviewDecision = (String) workflowData.getOrDefault("reviewDecision", "");
        String reviewNotes = (String) workflowData.getOrDefault("reviewNotes", "");

        // Check if this is a re-upload after being rejected in approval
        String approvalDecision = (String) workflowData.getOrDefault("approvalDecision", "");
        String approvalNotes = (String) workflowData.getOrDefault("approvalNotes", "");

        // Show notification if document was returned for changes
        // Show notification if document was returned for changes
        if ("Return".equals(reviewDecision) && !reviewNotes.isEmpty()) {
            Div returnNotification = new Div();
            returnNotification.addClassName("notification-panel");
            returnNotification.addClassName("warning");

            H3 returnHeader = new H3("Document Returned for Changes");
            returnHeader.getStyle().set("margin-top", "0");

            Paragraph notesLabel = new Paragraph("Reviewer Notes:");
            notesLabel.getStyle().set("font-weight", "bold");

            Paragraph notesContent = new Paragraph(reviewNotes);

            returnNotification.add(returnHeader, notesLabel, notesContent);
            layout.add(returnNotification);
        }
        // Show notification if document was rejected
        else if ("Rejected".equals(approvalDecision) && !approvalNotes.isEmpty()) {
            Div rejectionNotification = new Div();
            rejectionNotification.addClassName("notification-panel");
            rejectionNotification.addClassName("danger");

            H3 rejectionHeader = new H3("Document Rejected");
            rejectionHeader.getStyle().set("margin-top", "0");

            Paragraph notesLabel = new Paragraph("Approver Notes:");
            notesLabel.getStyle().set("font-weight", "bold");

            Paragraph notesContent = new Paragraph(approvalNotes);

            rejectionNotification.add(rejectionHeader, notesLabel, notesContent);
            layout.add(rejectionNotification);
        }

        // Create upload component
        Div uploadContainer = new Div();
        uploadContainer.addClassName("upload-container");
        uploadContainer.getStyle()
                .set("background-color", "var(--light-gray)")
                .set("border-radius", "8px")
                .set("padding", "1.5rem")
                .set("margin-top", "1rem");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "image/*", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        // Style the upload component
        upload.addClassName("upload-component");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Paragraph("Drag and drop your document here"));
        upload.setUploadButton(new Button("Browse Files", new Icon(VaadinIcon.UPLOAD)));

        Button nextButton = new Button("Submit Document", new Icon(VaadinIcon.CHECK));
        nextButton.addClassName("submit-button");
        nextButton.getStyle()
                .set("margin-top", "1rem")
                .set("background-color", "var(--primary-color)")
                .set("color", "white");
        nextButton.setEnabled(false);
        nextButton.setDisableOnClick(true);

        upload.addSucceededListener(event -> {
            try {
                java.io.InputStream inputStream = buffer.getInputStream();
                byte[] bytes = inputStream.readAllBytes();

                // Update execution context with uploaded document
                executionContext.put("uploadedDocument", bytes);
                executionContext.put("uploadedFileName", event.getFileName());
                executionContext.put("mimeType", event.getMIMEType());

                // Update workflow data
                Map<String, Object> docData = Map.of(
                        "fileName", event.getFileName(),
                        "mimeType", event.getMIMEType(),
                        "size", event.getContentLength());
                workflowData.put("uploadedDocument", docData);

                // Clear the return status if this is a re-upload
                if ("Return".equals(reviewDecision)) {
                    workflowData.put("reviewDecision", "");
                }

                // Clear the rejection status if this is a re-upload after rejection
                if ("Rejected".equals(approvalDecision)) {
                    workflowData.put("approvalDecision", "");
                    workflowData.put("approvalNotes", "");
                    executionContext.put("workflowStatus", "In Progress");
                }

                // Create success notification
                Div successDiv = new Div();
                successDiv.addClassName("notification-panel");
                successDiv.addClassName("info");
                successDiv.setText("Document uploaded: " + event.getFileName());
                layout.add(successDiv);

                Notification.show("Document uploaded: " + event.getFileName(),
                        3000, Notification.Position.BOTTOM_END);
                nextButton.setEnabled(true);
            } catch (Exception e) {
                Notification.show("Error processing file: " + e.getMessage());
            }
        });

        nextButton.addClickListener(e -> {
            // Disable button immediately to prevent double-clicks
            nextButton.setEnabled(false);

            // For Upload component, we need to use a different approach
            upload.getElement().setProperty("nodrop", true);
            upload.getElement().setAttribute("disabled", true);

            this.status = "Completed";
            executionContext.put("advanceWorkflow", true);

            // Provide visual feedback
            nextButton.setText("Submitted");
            nextButton.setIcon(new Icon(VaadinIcon.CHECK_CIRCLE));
            nextButton.getStyle()
                    .set("background-color", "var(--secondary-color)")
                    .set("color", "white");

            // Update status badge
            statusBadge.setText("Completed");
            statusBadge.getClassNames().remove("pending");
            statusBadge.addClassName("completed");

            // Call the onComplete handler to advance the workflow
            if (executionContext.containsKey("onComplete")) {
                ((Runnable) executionContext.get("onComplete")).run();
            } else {
                Notification.show("Cannot advance workflow: Missing completion handler",
                        3000, Notification.Position.MIDDLE);
            }
        });

        // Add instructions
        Paragraph instructions = new Paragraph("Please upload a document to continue with the workflow process.");
        instructions.getStyle()
                .set("margin-bottom", "1rem")
                .set("color", "var(--text-color)");

        uploadContainer.add(instructions, upload);
        layout.add(uploadContainer, nextButton);
        return layout;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication
                .getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser) {
            org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser = (org.springframework.security.oauth2.core.oidc.user.OidcUser) authentication
                    .getPrincipal();

            // Try to get preferred_username from the claims
            String preferredUsername = oidcUser.getAttribute("preferred_username");
            if (preferredUsername != null && !preferredUsername.isEmpty()) {
                return preferredUsername;
            }
        }

        // Fallback to the default behavior if we can't extract from OidcUser
        return (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "anonymous";
    }
}
