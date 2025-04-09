package com.example.workflow.components.nodes;

import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.component.textfield.TextArea;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Map;

public class ReviewNode extends WorkflowNode {

    @Override
    public String getType() {
        return "Document Review";
    }

    @Override
    public Component createDesignerComponent() {
        Button btn = new Button(getName());
        btn.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("border-left", "4px solid #2196F3");
        return btn;
    }

    @Override
    public Component createExecutionComponent(Map<String, Object> executionContext) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.addClassName("workflow-node");
        layout.addClassName("review-node");

        // Create node header with title and status
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.addClassName("node-header");

        H4 nodeTitle = new H4(getName().isEmpty() ? "Document Review" : getName());
        nodeTitle.addClassName("node-title");

        Div statusBadge = new Div();
        statusBadge.setText(this.status != null && !this.status.isEmpty() ? this.status : "Pending");
        statusBadge.addClassName("node-status");
        if ("Completed".equals(this.status)) {
            statusBadge.addClassName("completed");
        } else if ("Returned".equals(this.status)) {
            statusBadge.addClassName("returned");
        } else {
            statusBadge.addClassName("pending");
        }

        headerLayout.add(nodeTitle, statusBadge);
        layout.add(headerLayout);

        // Get necessary context data
        Long workflowId = (Long) executionContext.get("workflowId");
        byte[] uploadedDocument = (byte[]) executionContext.get("uploadedDocument");
        String uploadedFileName = (String) executionContext.get("uploadedFileName");

        // Get OPA service from context
        Object opaServiceObj = executionContext.get("opaService");
        if (!(opaServiceObj instanceof com.example.workflow.service.WorkflowOPAService)) {
            layout.add(new Paragraph("OPA service not available"));
            return layout;
        }

        com.example.workflow.service.WorkflowOPAService opaService = (com.example.workflow.service.WorkflowOPAService) opaServiceObj;

        // Check if user has review permissions
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities();
        boolean canReview = false;
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (opaService.isActionAllowed(workflowId, "review", role)) {
                canReview = true;
                break;
            }
        }

        if (!canReview) {
            Div notAllowedDiv = new Div();
            notAllowedDiv.addClassName("notification-panel");
            notAllowedDiv.addClassName("danger");

            H3 notAllowedHeader = new H3("Access Denied");
            notAllowedHeader.getStyle().set("margin-top", "0");

            Paragraph notAllowedText = new Paragraph("You are not authorized to review this document.");

            notAllowedDiv.add(notAllowedHeader, notAllowedText);
            layout.add(notAllowedDiv);
            return layout;
        }

        // Check if document exists
        if (uploadedDocument == null) {
            Div noDocDiv = new Div();
            noDocDiv.addClassName("notification-panel");
            noDocDiv.addClassName("warning");

            H3 noDocHeader = new H3("No Document Available");
            noDocHeader.getStyle().set("margin-top", "0");

            Paragraph noDocText = new Paragraph("No document has been uploaded for review.");

            Button skipButton = new Button("Skip Review", new Icon(VaadinIcon.FAST_FORWARD));
            skipButton.getStyle()
                    .set("margin-top", "1rem")
                    .set("background-color", "var(--warning-color)")
                    .set("color", "white");
            skipButton.addClickListener(e -> {
                this.status = "Skipped";
                statusBadge.setText("Skipped");
                executionContext.put("advanceWorkflow", true);

                if (executionContext.containsKey("onComplete")) {
                    ((Runnable) executionContext.get("onComplete")).run();
                }
            });

            noDocDiv.add(noDocHeader, noDocText, skipButton);
            layout.add(noDocDiv);
            return layout;
        }

        // Add reviewer role information if available
        String reviewerRole = properties.getOrDefault("reviewerRole", "Not specified");
        Div roleInfoDiv = new Div();
        roleInfoDiv.addClassName("notification-panel");
        roleInfoDiv.addClassName("info");
        roleInfoDiv.setText("Required Reviewer Role: " + reviewerRole);
        layout.add(roleInfoDiv);

        // Create review interface
        Div reviewContainer = new Div();
        reviewContainer.getStyle()
                .set("background-color", "var(--light-gray)")
                .set("border-radius", "8px")
                .set("padding", "1.5rem")
                .set("margin-top", "1rem");

        Button viewDocButton = new Button("View Document", new Icon(VaadinIcon.FILE_TEXT_O));
        viewDocButton.getStyle()
                .set("margin-bottom", "1rem")
                .set("background-color", "var(--primary-color)")
                .set("color", "white");
        viewDocButton.addClickListener(e -> {
            showDocumentViewer(uploadedFileName, uploadedDocument, layout);
        });

        TextArea notes = new TextArea("Review Notes");
        notes.setWidthFull();
        notes.setMinHeight("150px");
        notes.setPlaceholder("Enter your review comments here...");
        notes.addClassName("form-field");

        // Get previous review notes if this is a re-review
        Map<String, Object> workflowData = (Map<String, Object>) executionContext.get("workflowData");
        if (workflowData != null && workflowData.containsKey("reviewNotes")) {
            notes.setValue((String) workflowData.get("reviewNotes"));
        }

        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setWidthFull();
        buttonsLayout.setJustifyContentMode(JustifyContentMode.END);
        buttonsLayout.setSpacing(true);

        Button completeReviewButton = new Button("Complete Review", new Icon(VaadinIcon.CHECK_CIRCLE));

        Button returnButton = new Button("Return for Changes", new Icon(VaadinIcon.ARROW_BACKWARD));
        returnButton.getStyle()
                .set("background-color", "var(--warning-color)")
                .set("color", "white");
        // Return for Changes Button
        // Return for Changes Button
        returnButton.addClickListener(e -> {
            // Disable both buttons immediately to prevent double-clicks
            returnButton.setEnabled(false);
            completeReviewButton.setEnabled(false);

            if (notes.getValue().trim().isEmpty()) {
                // Re-enable buttons if validation fails
                returnButton.setEnabled(true);
                completeReviewButton.setEnabled(true);

                Notification.show("Please provide review notes before returning the document",
                        3000, Notification.Position.BOTTOM_CENTER);
                return;
            }

            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Return");
            this.status = "Returned";

            // Update status badge
            statusBadge.setText("Returned");
            statusBadge.getClassNames().remove("pending");
            statusBadge.addClassName("returned");

            // Set the node status in the execution context
            executionContext.put("nodeStatus", "Returned");

            // Set flag to return to upload node
            executionContext.put("returnToUpload", true);
            executionContext.put("advanceWorkflow", true);

            // Show notification
            Notification.show("Document returned for changes",
                    3000, Notification.Position.BOTTOM_END);

            // Call the onComplete handler to advance the workflow
            if (executionContext.containsKey("onComplete")) {
                ((Runnable) executionContext.get("onComplete")).run();
            }
        });

        completeReviewButton.getStyle()
                .set("background-color", "var(--secondary-color)")
                .set("color", "white");
        completeReviewButton.addClickListener(e -> {
            // Disable both buttons immediately to prevent double-clicks
            returnButton.setEnabled(false);
            completeReviewButton.setEnabled(false);

            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Complete");
            this.status = "Completed";

            // Update status badge
            statusBadge.setText("Completed");
            statusBadge.getClassNames().remove("pending");
            statusBadge.addClassName("completed");

            executionContext.put("advanceWorkflow", true);

            // Show notification
            Notification.show("Review completed successfully",
                    3000, Notification.Position.BOTTOM_END);

            // Call the onComplete handler to advance the workflow
            if (executionContext.containsKey("onComplete")) {
                ((Runnable) executionContext.get("onComplete")).run();
            }
        });

        buttonsLayout.add(returnButton, completeReviewButton);
        reviewContainer.add(viewDocButton, notes, buttonsLayout);
        layout.add(reviewContainer);

        return layout;
    }

    private void showDocumentViewer(String fileName, byte[] content, VerticalLayout parentLayout) {
        com.vaadin.flow.component.dialog.Dialog viewerDialog = new com.vaadin.flow.component.dialog.Dialog();
        viewerDialog.setWidth("80%");
        viewerDialog.setHeight("80%");
        viewerDialog.addClassName("document-viewer-dialog");

        // Add dialog header
        HorizontalLayout dialogHeader = new HorizontalLayout();
        dialogHeader.setWidthFull();
        dialogHeader.setJustifyContentMode(JustifyContentMode.BETWEEN);
        dialogHeader.setAlignItems(Alignment.CENTER);
        dialogHeader.setPadding(true);
        dialogHeader.getStyle()
                .set("border-bottom", "1px solid var(--border-color)")
                .set("background-color", "var(--light-gray)");

        H3 dialogTitle = new H3("Document: " + fileName);
        dialogTitle.getStyle().set("margin", "0");

        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClickListener(e -> viewerDialog.close());
        closeButton.getStyle()
                .set("background-color", "transparent")
                .set("color", "var(--text-color)");

        dialogHeader.add(dialogTitle, closeButton);
        viewerDialog.add(dialogHeader);

        // Add document content
        Div contentContainer = new Div();
        contentContainer.setWidthFull();
        contentContainer.getStyle().set("flex-grow", "1").set("overflow", "auto");

        if (fileName.toLowerCase().endsWith(".pdf")) {
            com.vaadin.flow.server.StreamResource resource = new com.vaadin.flow.server.StreamResource(
                    fileName, () -> new java.io.ByteArrayInputStream(content));
            com.vaadin.componentfactory.pdfviewer.PdfViewer pdfViewer = new com.vaadin.componentfactory.pdfviewer.PdfViewer();
            pdfViewer.setSrc(resource);
            pdfViewer.setSizeFull();
            contentContainer.add(pdfViewer);
        } else if (fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$")) {
            com.vaadin.flow.server.StreamResource resource = new com.vaadin.flow.server.StreamResource(
                    fileName, () -> new java.io.ByteArrayInputStream(content));
            com.vaadin.flow.component.html.Image image = new com.vaadin.flow.component.html.Image(resource, "Document");
            image.setMaxWidth("100%");
            image.getStyle().set("display", "block").set("margin", "0 auto");
            contentContainer.add(image);
        } else {
            Div unsupportedDiv = new Div();
            unsupportedDiv.addClassName("notification-panel");
            unsupportedDiv.addClassName("warning");
            unsupportedDiv.setText("Preview not available for this file type.");

            com.vaadin.flow.server.StreamResource resource = new com.vaadin.flow.server.StreamResource(
                    fileName, () -> new java.io.ByteArrayInputStream(content));
            com.vaadin.flow.component.html.Anchor downloadLink = new com.vaadin.flow.component.html.Anchor(resource,
                    "Download");
            downloadLink.getElement().setAttribute("download", true);
            downloadLink.getStyle()
                    .set("display", "inline-block")
                    .set("margin-top", "1rem")
                    .set("padding", "0.5rem 1rem")
                    .set("background-color", "var(--primary-color)")
                    .set("color", "white")
                    .set("text-decoration", "none")
                    .set("border-radius", "4px");

            unsupportedDiv.add(downloadLink);
            contentContainer.add(unsupportedDiv);
        }

        viewerDialog.add(contentContainer);
        viewerDialog.open();
    }
}
