package com.example.workflow.components.nodes;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
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

public class ApprovalNode extends WorkflowNode {

    @Override
    public String getType() {
        return "Approve/Reject";
    }

    @Override
    public Component createDesignerComponent() {
        Button btn = new Button(getName());
        btn.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("border-left", "4px solid #FF9800");
        return btn;
    }

    @Override
    public Component createExecutionComponent(Map<String, Object> executionContext) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);
        layout.addClassName("workflow-node");
        layout.addClassName("approval-node");

        // Create node header with title and status
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.addClassName("node-header");

        H4 nodeTitle = new H4(getName().isEmpty() ? "Document Approval" : getName());
        nodeTitle.addClassName("node-title");

        Div statusBadge = new Div();
        statusBadge.setText(this.status != null && !this.status.isEmpty() ? this.status : "Pending");
        statusBadge.addClassName("node-status");
        if ("Completed".equals(this.status)) {
            statusBadge.addClassName("completed");
        } else if ("Rejected".equals(this.status)) {
            statusBadge.addClassName("rejected");
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

        // Check if user has approval permissions
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities();
        boolean canApprove = false;
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (opaService.isActionAllowed(workflowId, "approve", role)) {
                canApprove = true;
                break;
            }
        }

        if (!canApprove) {
            Div notAllowedDiv = new Div();
            notAllowedDiv.addClassName("notification-panel");
            notAllowedDiv.addClassName("danger");

            H3 notAllowedHeader = new H3("Access Denied");
            notAllowedHeader.getStyle().set("margin-top", "0");

            Paragraph notAllowedText = new Paragraph("You are not authorized to approve this document.");

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

            Paragraph noDocText = new Paragraph("No document has been uploaded for approval");

            Button skipButton = new Button("Skip Approval", new Icon(VaadinIcon.FAST_FORWARD));
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

        // Add approver role information if available
        String approverRole = properties.getOrDefault("Approver Role", "Not specified");
        Div roleInfoDiv = new Div();
        roleInfoDiv.addClassName("notification-panel");
        roleInfoDiv.addClassName("info");
        roleInfoDiv.setText("Required Approver Role: " + approverRole);
        layout.add(roleInfoDiv);

        // Create approval interface
        Div approvalContainer = new Div();
        approvalContainer.getStyle()
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

        TextArea approvalNotes = new TextArea("Approval Notes");
        approvalNotes.setWidthFull();
        approvalNotes.setMinHeight("150px");
        approvalNotes.setPlaceholder("Enter your approval or rejection notes here...");
        approvalNotes.addClassName("form-field");

        // Get previous approval notes if this is a re-approval
        Map<String, Object> workflowData = (Map<String, Object>) executionContext.get("workflowData");
        if (workflowData != null && workflowData.containsKey("approvalNotes")) {
            approvalNotes.setValue((String) workflowData.get("approvalNotes"));
        }

        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setWidthFull();
        buttonsLayout.setJustifyContentMode(JustifyContentMode.END);
        buttonsLayout.setSpacing(true);

        Button rejectButton = new Button("Reject", new Icon(VaadinIcon.CLOSE_CIRCLE));

        Button approveButton = new Button("Approve", new Icon(VaadinIcon.CHECK_CIRCLE));
        approveButton.getStyle()
                .set("background-color", "var(--secondary-color)")
                .set("color", "white");
        approveButton.addClickListener(e -> {
            // Disable both buttons immediately to prevent double-clicks
            rejectButton.setEnabled(false);
            approveButton.setEnabled(false);

            workflowData.put("approvalNotes", approvalNotes.getValue());
            workflowData.put("approvalDecision", "Approved");
            this.status = "Completed";

            // Update status badge
            statusBadge.setText("Completed");
            statusBadge.getClassNames().remove("pending");
            statusBadge.addClassName("completed");

            executionContext.put("workflowStatus", "Completed");
            executionContext.put("advanceWorkflow", true);

            Notification.show("Document approved successfully",
                    3000, Notification.Position.BOTTOM_END);

            // Call the onComplete handler to advance the workflow
            if (executionContext.containsKey("onComplete")) {
                ((Runnable) executionContext.get("onComplete")).run();
            }
        });

        rejectButton.getStyle()
                .set("background-color", "var(--danger-color)")
                .set("color", "white");
        rejectButton.addClickListener(e -> {
            // Don't disable buttons yet since we need to validate first
            if (approvalNotes.getValue().trim().isEmpty()) {
                Notification.show("Please provide rejection notes",
                        3000, Notification.Position.BOTTOM_CENTER);
                return;
            }

            Dialog confirmDialog = new Dialog();
            confirmDialog.setCloseOnEsc(false);
            confirmDialog.setCloseOnOutsideClick(false);
            confirmDialog.addClassName("confirmation-dialog");
            confirmDialog.getElement().getThemeList().add("dialog");

            VerticalLayout confirmLayout = new VerticalLayout();
            confirmLayout.setPadding(true);
            confirmLayout.setSpacing(true);

            H3 confirmHeader = new H3("Confirm Rejection");
            confirmHeader.getStyle().set("margin-top", "0");

            Paragraph confirmText = new Paragraph(
                    "Are you sure you want to reject this document? This will return the workflow to the upload stage.");
            confirmText.getStyle().set("margin-bottom", "1rem");

            HorizontalLayout confirmButtonsLayout = new HorizontalLayout();
            confirmButtonsLayout.setWidthFull();
            confirmButtonsLayout.setJustifyContentMode(JustifyContentMode.END);
            confirmButtonsLayout.setSpacing(true);

            Button cancelButton = new Button("Cancel", new Icon(VaadinIcon.CLOSE));
            cancelButton.getStyle()
                    .set("margin-right", "1rem")
                    .set("background-color", "var(--light-gray)");
            cancelButton.addClickListener(event -> confirmDialog.close());

            // Modify the Confirm Reject button click handler
            Button confirmButton = new Button("Confirm Reject", new Icon(VaadinIcon.CHECK));
            confirmButton.getStyle()
                    .set("background-color", "var(--danger-color)")
                    .set("color", "white");
            confirmButton.addClickListener(event -> {
                // Disable both main buttons immediately to prevent double-clicks
                rejectButton.setEnabled(false);
                approveButton.setEnabled(false);

                // Also disable the dialog buttons
                cancelButton.setEnabled(false);
                confirmButton.setEnabled(false);

                workflowData.put("approvalNotes", approvalNotes.getValue());
                workflowData.put("approvalDecision", "Rejected");
                executionContext.put("workflowStatus", "Rejected");
                this.status = "Rejected";

                // Update status badge
                statusBadge.setText("Rejected");
                statusBadge.getClassNames().remove("pending");
                statusBadge.addClassName("rejected");

                // Set the node status in the execution context
                executionContext.put("nodeStatus", "Rejected");

                // Set flag to return to upload node
                executionContext.put("returnToUpload", true);
                executionContext.put("advanceWorkflow", true);

                confirmDialog.close();
                Notification.show("Document rejected",
                        3000, Notification.Position.BOTTOM_END);

                // Call the onComplete handler to advance the workflow
                if (executionContext.containsKey("onComplete")) {
                    ((Runnable) executionContext.get("onComplete")).run();
                }
            });

            confirmButtonsLayout.add(cancelButton, confirmButton);
            confirmLayout.add(confirmHeader, confirmText, confirmButtonsLayout);
            confirmDialog.add(confirmLayout);
            confirmDialog.open();
        });

        buttonsLayout.add(rejectButton, approveButton);
        approvalContainer.add(viewDocButton, approvalNotes, buttonsLayout);
        layout.add(approvalContainer);

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
