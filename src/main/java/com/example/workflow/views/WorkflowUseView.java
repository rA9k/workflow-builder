package com.example.workflow.views;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionService;
import com.example.workflow.service.WorkflowOPAService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.StreamResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Route("workflow-use")
@RouteAlias("workflow-use/new")
@CssImport("./styles/responsive-workflow.css")
@JsModule("./js/workflow-responsive.js")
public class WorkflowUseView extends VerticalLayout implements HasUrlParameter<Long>, BeforeEnterObserver {

    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private List<Map<String, Object>> workflowNodes;
    private HorizontalLayout workflowProgress;
    private final Map<String, Object> workflowData = new HashMap<>();
    private int currentNodeIndex = 0;

    // Add this field
    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    private WorkflowExecutionEntity workflowExecution;

    // Track uploaded document and workflow state
    private byte[] uploadedDocument;
    private String uploadedFileName;
    private String mimeType;
    private String workflowStatus = "In Progress";
    private Map<String, String> nodeStatuses = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Inject the OPA service
    @Autowired
    private WorkflowOPAService workflowOPAService;

    public WorkflowUseView(WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowExecutionService workflowExecutionService) {
        this.workflowJsonRepository = workflowJsonRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowExecutionService = workflowExecutionService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);
        addClassName("responsive-workflow-use");

        H2 header = new H2("Workflow Execution");
        header.getStyle().set("margin-top", "0");
        header.addClassName("responsive-header");

        workflowProgress = new HorizontalLayout();
        workflowProgress.setWidthFull();
        workflowProgress.addClassName("responsive-workflow-progress");
        workflowProgress.getStyle()
                .set("background-color", "#f5f5f5")
                .set("padding", "1rem")
                .set("border-radius", "4px")
                .set("margin-bottom", "1rem")
                .set("overflow-x", "auto"); // Allow horizontal scrolling on small screens

        Button backButton = new Button("Back to Workflows");
        backButton.addClassName("responsive-button");
        backButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));

        add(header, backButton, workflowProgress);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Long workflowId = event.getRouteParameters().getLong("id").orElse(null);

        if (workflowId != null) {
            Optional<WorkflowExecutionEntity> executionOpt = workflowExecutionRepository.findById(workflowId);

            if (executionOpt.isPresent()) {
                WorkflowExecutionEntity execution = executionOpt.get();
                String currentUsername = getCurrentUsername();
                List<String> userRoles = getCurrentUserRoles();

                // Check if user has permission to view this workflow
                boolean hasPermission = execution.getCreatedBy().equals(currentUsername);

                if (!hasPermission) {
                    // Check if the user has the required role for the current node
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<Map<String, Object>> workflowNodes = mapper.readValue(
                                execution.getWorkflow().getData(),
                                new TypeReference<List<Map<String, Object>>>() {
                                });

                        int currentNodeIndex = execution.getCurrentNodeIndex();
                        if (currentNodeIndex >= 0 && currentNodeIndex < workflowNodes.size()) {
                            Map<String, Object> currentNode = workflowNodes.get(currentNodeIndex);
                            String nodeType = (String) currentNode.get("type");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> props = (Map<String, Object>) currentNode.get("props");

                            if (props != null) {
                                // For Document Review nodes
                                if ("Document Review".equals(nodeType) || "Doc Review".equals(nodeType)) {
                                    String reviewerRole = (String) props.get("reviewerRole");
                                    if (reviewerRole != null && userRoles.contains(reviewerRole)) {
                                        hasPermission = true;
                                    }
                                }
                                // For Approval nodes
                                else if ("Approve/Reject".equals(nodeType) || "Approval".equals(nodeType)) {
                                    String approverRole = (String) props.get("Approver Role");
                                    if (approverRole != null && userRoles.contains(approverRole)) {
                                        hasPermission = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log error but don't fail the check
                        System.err.println("Error checking workflow roles: " + e.getMessage());
                    }
                }

                if (!hasPermission) {
                    // Redirect to workflows list with error message
                    UI.getCurrent().navigate(WorkflowInUseListView.class);
                    Notification.show("You don't have permission to view this workflow",
                            3000, Notification.Position.MIDDLE);
                    return;
                }
            }
        }
    }

    private List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(role -> role.replace("ROLE_", "")) // Remove ROLE_ prefix if present
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long parameter) {
        if (parameter == null) {
            Notification.show("No workflow specified");
            UI.getCurrent().navigate(WorkflowViewerView.class);
            return;
        }

        String path = event.getLocation().getPath();
        boolean isNew = path.contains("/new");

        if (isNew) {
            // For a new execution, treat the parameter as a workflow definition ID
            workflowJsonRepository.findById(parameter).ifPresentOrElse(
                    this::loadWorkflow, // This method creates a new execution
                    () -> {
                        Notification.show("Workflow not found");
                        UI.getCurrent().navigate(WorkflowViewerView.class);
                    });
        } else {
            // Otherwise, treat the parameter as a workflow execution ID
            workflowExecution = workflowExecutionRepository.findById(parameter).orElse(null);
            if (workflowExecution != null) {
                loadExistingExecution(workflowExecution);
            } else {
                Notification.show("Workflow execution not found");
                UI.getCurrent().navigate(WorkflowViewerView.class);
            }
        }
    }

    private void loadExistingExecution(WorkflowExecutionEntity execution) {
        try {
            workflowNodes = objectMapper.readValue(
                    execution.getWorkflow().getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            currentNodeIndex = execution.getCurrentNodeIndex();
            uploadedDocument = execution.getUploadedDocument();
            uploadedFileName = execution.getUploadedFileName();
            workflowStatus = execution.getStatus();

            if (execution.getNodeStatuses() != null && !execution.getNodeStatuses().isEmpty()) {
                nodeStatuses = objectMapper.readValue(
                        execution.getNodeStatuses(),
                        new TypeReference<Map<String, String>>() {
                        });
            } else {
                workflowNodes.forEach(node -> {
                    String nodeName = (String) node.get("name");
                    nodeStatuses.put(nodeName, "Pending");
                });
            }

            if (execution.getWorkflowData() != null && !execution.getWorkflowData().isEmpty()) {
                workflowData.putAll(objectMapper.readValue(
                        execution.getWorkflowData(),
                        new TypeReference<Map<String, Object>>() {
                        }));
            }

            renderWorkflowSteps();
            displayCurrentNode();
        } catch (Exception e) {
            Notification.show("Error loading execution: " + e.getMessage());
        }
    }

    // New execution creation from a workflow definition.
    private void loadWorkflow(WorkflowJsonEntity entity) {
        try {
            workflowNodes = objectMapper.readValue(
                    entity.getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            workflowNodes.forEach(node -> {
                String nodeName = (String) node.get("name");
                nodeStatuses.put(nodeName, "Pending");
            });

            workflowExecution = new WorkflowExecutionEntity();
            workflowExecution.setWorkflow(entity);
            workflowExecution.setStatus("In Progress");
            workflowExecution.setCurrentNodeIndex(0);
            workflowExecution.setCreatedBy(getCurrentUsername());
            workflowExecution = workflowExecutionRepository.save(workflowExecution);

            // Deploy the execution-specific upload-stage policy for this new instance.
            String currentUsername = getCurrentUsername();
            workflowOPAService.deployWorkflowExecutionPolicy(
                    entity.getId(), workflowExecution.getId(), currentUsername);

            renderWorkflowSteps();
            displayCurrentNode();
        } catch (Exception e) {
            Notification.show("Error loading workflow: " + e.getMessage());
        }
    }

    private void renderWorkflowSteps() {
        workflowProgress.removeAll();
        workflowProgress.addClassName("responsive-steps-container");

        for (int i = 0; i < workflowNodes.size(); i++) {
            Map<String, Object> node = workflowNodes.get(i);
            String nodeName = (String) node.get("name");
            String status = nodeStatuses.getOrDefault(nodeName, "Pending");

            Div stepDiv = new Div();
            stepDiv.setText(nodeName);
            stepDiv.addClassName("workflow-step");
            stepDiv.addClassName("responsive-step");
            stepDiv.getStyle()
                    .set("padding", "0.5rem 1rem")
                    .set("border-radius", "4px")
                    .set("margin-right", "0.5rem")
                    .set("cursor", "pointer");
            if ("Completed".equals(status)) {
                stepDiv.getStyle().set("background-color", "#4CAF50").set("color", "white");
            } else if ("In Progress".equals(status)) {
                stepDiv.getStyle().set("background-color", "#2196F3").set("color", "white");
            } else if ("Rejected".equals(status)) {
                stepDiv.getStyle().set("background-color", "#F44336").set("color", "white");
            } else {
                stepDiv.getStyle().set("background-color", "#E0E0E0").set("color", "black");
            }
            if (i == currentNodeIndex) {
                stepDiv.getStyle().set("border", "2px solid #000");
            }
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
        getChildren()
                .filter(component -> !(component instanceof H2 || component instanceof Button
                        || component == workflowProgress))
                .forEach(this::remove);

        if (currentNodeIndex >= workflowNodes.size()) {
            Paragraph complete = new Paragraph("Workflow is complete");
            complete.getStyle().set("font-size", "1.2rem");
            if (uploadedDocument != null) {
                Button downloadButton = new Button("Download Final Document");
                downloadButton.getStyle().set("background-color", "#2196F3").set("color", "white");

                StreamResource resource = new StreamResource(uploadedFileName,
                        () -> new ByteArrayInputStream(uploadedDocument));
                Anchor downloadLink = new Anchor(resource, "");
                downloadLink.getElement().setAttribute("download", true);
                downloadLink.add(downloadButton);

                add(downloadLink);
            }
            add(complete);
            return;
        }

        Map<String, Object> currentNode = workflowNodes.get(currentNodeIndex);
        String nodeType = (String) currentNode.get("type");
        String nodeName = (String) currentNode.get("name");
        nodeStatuses.put(nodeName, "In Progress");
        renderWorkflowSteps();

        Div nodeCard = new Div();
        nodeCard.addClassName("responsive-node-card");
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

        if (currentNode.get("description") != null) {
            Paragraph descPara = new Paragraph((String) currentNode.get("description"));
            nodeCard.add(descPara);
        }

        Component nodeInterface;
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

    // ------------------- Node Interface Methods -------------------

    private Component createUploadInterface(Map<String, Object> node) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);

        // Enforce using the execution-specific upload-stage policy.
        Long workflowId = workflowExecution.getWorkflow().getId();
        Long executionId = workflowExecution.getId();
        String currentUsername = getCurrentUsername();
        String uploadPolicyPackage = "workflow_" + workflowId + "_" + executionId;

        boolean canUpload = workflowOPAService.isActionAllowed(uploadPolicyPackage, "upload", currentUsername,
                "username");
        if (!canUpload) {
            Paragraph notAllowed = new Paragraph("You are not authorized to upload documents for this execution.");
            notAllowed.getStyle().set("color", "red");
            layout.add(notAllowed);
            return layout;
        }

        // Check if this is a re-upload after being returned for changes
        String reviewDecision = (String) workflowData.getOrDefault("reviewDecision", "");
        String reviewNotes = (String) workflowData.getOrDefault("reviewNotes", "");

        // Check if this is a re-upload after being rejected in approval
        String approvalDecision = (String) workflowData.getOrDefault("approvalDecision", "");
        String approvalNotes = (String) workflowData.getOrDefault("approvalNotes", "");

        if ("Return".equals(reviewDecision) && !reviewNotes.isEmpty()) {
            // Create a notification panel for the return reason
            Div returnNotification = new Div();
            returnNotification.getStyle()
                    .set("background-color", "#FFF3CD")
                    .set("color", "#856404")
                    .set("padding", "1rem")
                    .set("border-radius", "4px")
                    .set("border-left", "4px solid #FFC107")
                    .set("margin-bottom", "1rem");

            H3 returnHeader = new H3("Document Returned for Changes");
            returnHeader.getStyle().set("margin-top", "0");

            Paragraph notesLabel = new Paragraph("Reviewer Notes:");
            notesLabel.getStyle().set("font-weight", "bold");

            Paragraph notesContent = new Paragraph(reviewNotes);

            returnNotification.add(returnHeader, notesLabel, notesContent);
            layout.add(returnNotification);
        } else if ("Rejected".equals(approvalDecision) && !approvalNotes.isEmpty()) {
            // Create a notification panel for the rejection reason
            Div rejectionNotification = new Div();
            rejectionNotification.getStyle()
                    .set("background-color", "#F8D7DA")
                    .set("color", "#721C24")
                    .set("padding", "1rem")
                    .set("border-radius", "4px")
                    .set("border-left", "4px solid #F44336")
                    .set("margin-bottom", "1rem");

            H3 rejectionHeader = new H3("Document Rejected");
            rejectionHeader.getStyle().set("margin-top", "0");

            Paragraph notesLabel = new Paragraph("Approver Notes:");
            notesLabel.getStyle().set("font-weight", "bold");

            Paragraph notesContent = new Paragraph(approvalNotes);

            rejectionNotification.add(rejectionHeader, notesLabel, notesContent);
            layout.add(rejectionNotification);
        }

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "image/*", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        Button nextButton = new Button("Submit");
        nextButton.setEnabled(false);

        upload.addSucceededListener(event -> {
            try {
                InputStream inputStream = buffer.getInputStream();
                byte[] bytes = inputStream.readAllBytes();
                uploadedDocument = bytes;
                uploadedFileName = event.getFileName();

                Map<String, Object> docData = new HashMap<>();
                docData.put("fileName", uploadedFileName);
                docData.put("mimeType", event.getMIMEType());
                docData.put("size", event.getContentLength());
                workflowData.put("uploadedDocument", docData);

                // Clear the return status if this is a re-upload
                if ("Return".equals(reviewDecision)) {
                    workflowData.put("reviewDecision", "");
                }

                // Clear the rejection status if this is a re-upload after rejection
                if ("Rejected".equals(approvalDecision)) {
                    workflowData.put("approvalDecision", "");
                    workflowData.put("approvalNotes", "");
                    workflowStatus = "In Progress";
                }

                Notification.show("Document uploaded: " + uploadedFileName);
                nextButton.setEnabled(true);
            } catch (Exception e) {
                Notification.show("Error processing file: " + e.getMessage());
            }
        });

        nextButton.addClickListener(e -> {
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
        // For review stage, use the execution policy package.
        Long workflowId = workflowExecution.getWorkflow().getId();
        Long executionId = workflowExecution.getId();
        String reviewPolicyPackage = "workflow_" + workflowId + "_" + executionId;
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities();
        boolean canReview = false;
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (workflowOPAService.isActionAllowed(workflowId, "review", role)) {
                canReview = true;
                break;
            }
        }
        if (!canReview) {
            Paragraph notAllowed = new Paragraph("You are not authorized to review this document.");
            notAllowed.getStyle().set("color", "red");
            layout.add(notAllowed);
            return layout;
        }

        if (uploadedDocument == null) {
            Paragraph error = new Paragraph("No document has been uploaded");
            error.getStyle().set("color", "red");
            layout.add(error);
            Button skipButton = new Button("Skip Review");
            skipButton.addClickListener(e -> {
                nodeStatuses.put((String) node.get("name"), "Skipped");
                updateWorkflowExecution();
                moveToNextNode();
            });
            layout.add(skipButton);
            return layout;
        }

        Button viewDocButton = new Button("View Document");
        viewDocButton.addClickListener(e -> showDocumentViewer(uploadedFileName, uploadedDocument));

        TextArea notes = new TextArea("Review Notes");
        notes.setWidthFull();
        notes.setMinHeight("150px");

        Button returnButton = new Button("Return for Changes");
        returnButton.getStyle().set("background-color", "#FFC107");
        returnButton.addClickListener(e -> {
            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Return");
            nodeStatuses.put((String) node.get("name"), "Returned");
            updateWorkflowExecution();
            for (int i = 0; i < workflowNodes.size(); i++) {
                if ("Upload".equals(workflowNodes.get(i).get("type"))) {
                    currentNodeIndex = i;
                    break;
                }
            }
            updateWorkflowExecution();
            displayCurrentNode();
        });

        Button completeReviewButton = new Button("Complete Review");
        completeReviewButton.getStyle().set("background-color", "#4CAF50").set("color", "white");
        completeReviewButton.addClickListener(e -> {
            workflowData.put("reviewNotes", notes.getValue());
            workflowData.put("reviewDecision", "Complete");
            nodeStatuses.put((String) node.get("name"), "Completed");
            updateWorkflowExecution();
            moveToNextNode();
        });

        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) node.get("props");
        if (props != null) {
            Paragraph reviewerInfo = new Paragraph(
                    "Reviewer Role: " + props.getOrDefault("reviewerRole", "Not specified"));
            layout.add(reviewerInfo);
        }

        HorizontalLayout buttons = new HorizontalLayout(returnButton, completeReviewButton);
        layout.add(viewDocButton, notes, buttons);
        return layout;
    }

    private Component createApprovalInterface(Map<String, Object> node) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(false);

        Long workflowId = workflowExecution.getWorkflow().getId();
        Long executionId = workflowExecution.getId();
        String approvalPolicyPackage = "workflow_" + workflowId + "_" + executionId;
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities();
        boolean canApprove = false;
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (workflowOPAService.isActionAllowed(workflowId, "approve", role)) {
                canApprove = true;
                break;
            }
        }
        if (!canApprove) {
            Paragraph notAllowed = new Paragraph("You are not authorized to approve this document.");
            notAllowed.getStyle().set("color", "red");
            layout.add(notAllowed);
            return layout;
        }

        if (uploadedDocument == null) {
            Paragraph error = new Paragraph("No document has been uploaded for approval");
            error.getStyle().set("color", "red");
            layout.add(error);
            Button skipButton = new Button("Skip Approval");
            skipButton.addClickListener(e -> {
                nodeStatuses.put((String) node.get("name"), "Skipped");
                updateWorkflowExecution();
                moveToNextNode();
            });
            layout.add(skipButton);
            return layout;
        }

        Button viewDocButton = new Button("View Document");
        viewDocButton.addClickListener(e -> showDocumentViewer(uploadedFileName, uploadedDocument));

        TextArea approvalNotes = new TextArea("Approval Notes");
        approvalNotes.setWidthFull();

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
                workflowStatus = "Rejected";
                nodeStatuses.put((String) node.get("name"), "Rejected");
                updateWorkflowExecution();
                confirmDialog.close();
                Notification.show("Document rejected");
                currentNodeIndex = 0; // Go back to Upload node
                updateWorkflowExecution();
                displayCurrentNode();
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
            nodeStatuses.put((String) node.get("name"), "Completed");
            workflowStatus = "Completed";
            updateWorkflowExecution();
            Notification.show("Document approved!");
            moveToNextNode();
        });

        @SuppressWarnings("unchecked")
        Map<String, String> props = (Map<String, String>) node.get("props");
        if (props != null) {
            Paragraph approverInfo = new Paragraph(
                    "Approver Role: " + props.getOrDefault("Approver Role", "Not specified"));
            layout.add(approverInfo);
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
                workflowData.put("customField_" + label, customField.getValue());
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

    // -------------------- Workflow Navigation and Update Methods
    // --------------------

    private void updateWorkflowExecution() {
        try {
            workflowExecution.setCurrentNodeIndex(currentNodeIndex);
            workflowExecution.setStatus(workflowStatus);
            workflowExecution.setUploadedFileName(uploadedFileName);
            workflowExecution.setUploadedDocument(uploadedDocument);
            workflowExecution.setReviewDecision((String) workflowData.getOrDefault("reviewDecision", ""));
            workflowExecution.setReviewNotes((String) workflowData.getOrDefault("reviewNotes", ""));
            workflowExecution.setApprovalDecision((String) workflowData.getOrDefault("approvalDecision", ""));
            workflowExecution.setApprovalNotes((String) workflowData.getOrDefault("approvalNotes", ""));
            @SuppressWarnings("unchecked")
            Map<String, Object> docData = (Map<String, Object>) workflowData.get("uploadedDocument");
            if (docData != null) {
                workflowExecution.setMimeType((String) docData.get("mimeType"));
            }
            workflowExecution.setNodeStatuses(objectMapper.writeValueAsString(nodeStatuses));
            workflowExecution.setWorkflowData(objectMapper.writeValueAsString(workflowData));

            // Use the service instead of directly accessing the repository
            workflowExecution = workflowExecutionService.updateWorkflowExecution(workflowExecution);
        } catch (Exception e) {
            Notification.show("Error saving workflow state: " + e.getMessage());
        }
    }

    private void moveToNextNode() {
        currentNodeIndex++;
        updateWorkflowExecution();
        if (currentNodeIndex >= workflowNodes.size()) {
            if ("Rejected".equals(workflowStatus)) {
                currentNodeIndex = 0;
                updateWorkflowExecution();
                displayCurrentNode();
            } else {
                showWorkflowSummary();
            }
        } else {
            displayCurrentNode();
        }
    }

    private void showWorkflowSummary() {
        getChildren()
                .filter(component -> !(component instanceof H2 || component instanceof Button
                        || component == workflowProgress))
                .forEach(this::remove);
        for (Map<String, Object> node : workflowNodes) {
            String nodeName = (String) node.get("name");
            if (!"Rejected".equals(nodeStatuses.get(nodeName))) {
                nodeStatuses.put(nodeName, "Completed");
            }
        }
        renderWorkflowSteps();
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
        Div statusIndicator = new Div();
        statusIndicator.setText("Status: " + workflowStatus);
        statusIndicator.getStyle()
                .set("padding", "0.5rem 1rem")
                .set("border-radius", "4px")
                .set("display", "inline-block")
                .set("margin-bottom", "1rem");
        if ("Completed".equals(workflowStatus)) {
            statusIndicator.getStyle().set("background-color", "#4CAF50").set("color", "white");
        } else if ("Rejected".equals(workflowStatus)) {
            statusIndicator.getStyle().set("background-color", "#F44336").set("color", "white");
        } else {
            statusIndicator.getStyle().set("background-color", "#FFC107");
        }
        Paragraph docInfo = new Paragraph("Document: " + uploadedFileName);
        String reviewDecision = (String) workflowData.getOrDefault("reviewDecision", "N/A");
        String approvalDecision = (String) workflowData.getOrDefault("approvalDecision", "N/A");
        Paragraph reviewInfo = new Paragraph("Review Decision: " + reviewDecision);
        Paragraph approvalInfo = new Paragraph("Approval Decision: " + approvalDecision);
        VerticalLayout notesLayout = new VerticalLayout();
        notesLayout.setPadding(false);
        notesLayout.setSpacing(true);
        String reviewNotes = (String) workflowData.getOrDefault("reviewNotes", "");
        String approvalNotes = (String) workflowData.getOrDefault("approvalNotes", "");
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
        Button backToWorkflowsButton = new Button("Back to Workflows");
        backToWorkflowsButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));
        Button startNewButton = new Button("Start New");
        startNewButton.getStyle().set("background-color", "#2196F3").set("color", "white");
        startNewButton.addClickListener(e -> {
            currentNodeIndex = 0;
            uploadedDocument = null;
            uploadedFileName = null;
            workflowData.clear();
            workflowStatus = "In Progress";
            for (Map<String, Object> node : workflowNodes) {
                nodeStatuses.put((String) node.get("name"), "Pending");
            }
            displayCurrentNode();
        });
        summaryCard.add(summaryTitle, statusIndicator, docInfo,
                new HorizontalLayout(backToWorkflowsButton, startNewButton));
        add(summaryCard);
    }

    private void showDocumentViewer(String fileName, byte[] content) {
        Dialog viewerDialog = new Dialog();
        viewerDialog.setWidth("80%");
        viewerDialog.setHeight("80%");
        if (fileName.toLowerCase().endsWith(".pdf")) {
            StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
            com.vaadin.componentfactory.pdfviewer.PdfViewer pdfViewer = new com.vaadin.componentfactory.pdfviewer.PdfViewer();
            pdfViewer.setSrc(resource);
            pdfViewer.setSizeFull();
            viewerDialog.add(pdfViewer);
        } else if (fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$")) {
            StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
            Image image = new Image(resource, "Document");
            image.setMaxWidth("100%");
            viewerDialog.add(image);
        } else {
            Paragraph unsupportedMsg = new Paragraph("Preview not available for this file type.");
            StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(content));
            Anchor downloadLink = new Anchor(resource, "Download");
            downloadLink.getElement().setAttribute("download", true);
            viewerDialog.add(unsupportedMsg, downloadLink);
        }
        Button closeButton = new Button("Close");
        closeButton.addClickListener(e -> viewerDialog.close());
        viewerDialog.add(closeButton);
        viewerDialog.open();
    }

    // -------------------- Helper Method --------------------

    // Retrieve the current username from the security context.
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

            // // Fallback to name if preferred_username is not available
            // String name = oidcUser.getAttribute("given_name");
            // if (name != null && !name.isEmpty()) {
            // return name;
            // }

            // // You can also access the subject (userId) directly
            // String userId = oidcUser.getSubject();
            // if (userId != null && !userId.isEmpty()) {
            // return userId;
            // }
        }

        // Fallback to the default behavior if we can't extract from OidcUser
        return (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "anonymous";
    }

}