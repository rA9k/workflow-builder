package com.example.workflow.views;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.OrganizationService;
import com.example.workflow.service.WorkflowExecutionService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UIScope
@Component
@CssImport("styles/wave-styles.css")
@Route(value = "workflows-in-use", layout = MainView.class)
@PageTitle("Workflows In Use")
public class WorkflowInUseListView extends VerticalLayout {

    private final WorkflowExecutionService workflowExecutionService;
    private Grid<WorkflowExecutionEntity> grid;
    private TextField filter;

    @Autowired
    private OrganizationService organizationService;

    public WorkflowInUseListView(WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionService workflowExecutionService) {

        this.workflowExecutionService = workflowExecutionService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureGrid();
        configureFilter();

        H2 header = new H2("Workflows In Use");

        HorizontalLayout actionBar = new HorizontalLayout(filter);
        actionBar.setWidthFull();
        actionBar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        Button backButton = new Button("Back to Workflows");
        backButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));

        Button refreshButton = new Button("Refresh");
        refreshButton.addClickListener(e -> refreshGrid());

        HorizontalLayout buttonLayout = new HorizontalLayout(backButton, refreshButton);

        add(header, actionBar, grid, buttonLayout);

        // refreshGrid();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Now it's safe to call refreshGrid
        refreshGrid();
    }

    private void configureGrid() {
        grid = new Grid<>(WorkflowExecutionEntity.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(entity -> entity.getWorkflow().getName())
                .setHeader("Workflow")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(WorkflowExecutionEntity::getDocumentType)
                .setHeader("Document Type")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(entity -> {
            String stageName = "Unknown Stage";
            String badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #E0E0E0; color: black;"; // Default
            // ADD THE STATUS CHECK HERE, BEFORE THE TRY BLOCK
            if ("Completed".equals(entity.getStatus())) {
                stageName = "Completed";
                badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #4CAF50; color: white;";
                return new Span(new com.vaadin.flow.component.Html(
                        "<span style='" + badgeStyle + "'>" + stageName + "</span>"));
            } else if ("Rejected".equals(entity.getStatus())) {
                stageName = "Rejected";
                badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #F44336; color: white;";
                return new Span(new com.vaadin.flow.component.Html(
                        "<span style='" + badgeStyle + "'>" + stageName + "</span>"));
            }

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> workflowData = mapper.readValue(
                        entity.getWorkflow().getData(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                        });
                // Then extract the nodes list from the map
                java.util.List<java.util.Map<String, Object>> workflowNodes = (java.util.List<java.util.Map<String, Object>>) workflowData
                        .get("nodes");

                if (workflowNodes == null) {
                    // Handle the case where there's no nodes property
                    return new Span(new com.vaadin.flow.component.Html(
                            "<span style='padding: 0.25em 0.5em; border-radius: 4px; background-color: #E0E0E0; color: black;'>Unknown Stage</span>"));
                }

                int currentNodeIndex = entity.getCurrentNodeIndex();
                if (currentNodeIndex >= 0 && currentNodeIndex < workflowNodes.size()) {
                    java.util.Map<String, Object> currentNode = workflowNodes.get(currentNodeIndex);
                    String nodeType = (String) currentNode.get("type");

                    switch (nodeType) {
                        case "Upload":
                            stageName = "Upload Stage";
                            badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #2196F3; color: white;";
                            break;
                        case "Doc Review":
                            stageName = "Doc Review Stage";
                            badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #FFC107; color: black;";
                            break;
                        case "Approval":
                            stageName = "Approval Stage";
                            badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #9C27B0; color: white;";
                            break;
                        case "Completed":
                            stageName = "Completed";
                            badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #4CAF50; color: white;";
                            break;
                        case "Rejected":
                            stageName = "Rejected";
                            badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #F44336; color: white;";
                            break;
                        default:
                            stageName = nodeType + " Stage"; // Custom component name
                    }
                }
            } catch (Exception e) {
                // Add more detailed logging
                System.err.println("Error parsing workflow data: " + e.getMessage());
                e.printStackTrace();

                // Handle parsing errors
                return new Span(new com.vaadin.flow.component.Html(
                        "<span style='padding: 0.25em 0.5em; border-radius: 4px; background-color: #E0E0E0; color: black;'>Error: "
                                + e.getMessage() + "</span>"));
            }
            // Return a Span with inline styles
            return new Span(
                    new com.vaadin.flow.component.Html("<span style='" + badgeStyle + "'>" + stageName + "</span>"));
        })).setHeader("Status").setSortable(true).setWidth("100px");

        grid.addColumn(entity -> {
            if (entity.getUpdatedAt() != null) {
                return entity.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return "";
        }).setHeader("Last Updated").setSortable(true).setWidth("150px");

        grid.addColumn(WorkflowExecutionEntity::getCreatedBy)
                .setHeader("Created By")
                .setSortable(true)
                .setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(entity -> {
            HorizontalLayout actions = new HorizontalLayout();

            Button viewButton = new Button("View");
            viewButton.getStyle().set("font-size", "0.8em");
            viewButton.addClickListener(e -> {
                // Use direct URL navigation to ensure we don't hit the /new/ route alias
                UI.getCurrent().getPage().setLocation("workflow-use/" + entity.getId());
            });

            Button detailsButton = new Button("Details");
            detailsButton.getStyle().set("font-size", "0.8em");
            detailsButton.addClickListener(e -> showDetails(entity));

            // Only show delete button if current user is the creator of the workflow
            // instance
            String currentUsername = getCurrentUsername();
            if (currentUsername.equals(entity.getCreatedBy())) {
                Button deleteButton = new Button("Delete");
                deleteButton.getStyle().set("font-size", "0.8em");
                deleteButton.getStyle().set("color", "white");
                deleteButton.getStyle().set("background-color", "#F44336");
                deleteButton.addClickListener(e -> confirmDelete(entity));
                actions.add(viewButton, detailsButton, deleteButton);
            } else {
                actions.add(viewButton, detailsButton);
            }

            return actions;
        })).setHeader("Actions").setAutoWidth(true);

        // Enable click on row to open the workflow
        grid.addItemClickListener(event -> {
            if (event.getColumn() == null) {
                UI.getCurrent().navigate(WorkflowUseView.class, event.getItem().getId());
            }
        });
    }

    private void confirmDelete(WorkflowExecutionEntity entity) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setWidth("400px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 title = new H3("Confirm Delete");
        Paragraph confirmation = new Paragraph("Are you sure you want to delete the workflow execution for \""
                + entity.getUploadedFileName() + "\"?");
        Paragraph warning = new Paragraph("This action cannot be undone.");
        warning.getStyle().set("color", "red");

        HorizontalLayout buttons = new HorizontalLayout();
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(e -> confirmDialog.close());

        Button deleteButton = new Button("Delete");
        deleteButton.getStyle().set("background-color", "#F44336");
        deleteButton.getStyle().set("color", "white");
        deleteButton.addClickListener(e -> {
            deleteWorkflowExecution(entity);
            confirmDialog.close();
        });

        buttons.add(cancelButton, deleteButton);
        buttons.setJustifyContentMode(JustifyContentMode.END);

        layout.add(title, confirmation, warning, buttons);
        confirmDialog.add(layout);

        confirmDialog.open();
    }

    @Transactional
    private void deleteWorkflowExecution(WorkflowExecutionEntity entity) {
        try {
            workflowExecutionService.deleteWorkflowExecution(entity.getId());
            refreshGrid();
            showNotification("Workflow execution deleted successfully");
        } catch (Exception ex) {
            showNotification("Error deleting workflow execution: " + ex.getMessage());
        }
    }

    private void showNotification(String message) {
        com.vaadin.flow.component.notification.Notification
                .show(message, 3000,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
    }

    private void configureFilter() {
        filter = new TextField();
        filter.setPlaceholder("Filter by document name...");
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> applyFilter());
        filter.setWidthFull();
    }

    private void refreshGrid() {
        String currentUsername = getCurrentUsername();
        List<String> userRoles = getCurrentUserRoles();
        OrganizationEntity organization = organizationService.getCurrentOrganization();

        // Get executions filtered by organization and user permissions
        List<WorkflowExecutionEntity> executions = workflowExecutionService
                .getWorkflowExecutionsForUserAndOrganization(currentUsername, userRoles, organization);
        grid.setItems(executions);
    }

    private void applyFilter() {
        String filterText = filter.getValue().trim().toLowerCase();
        String currentUsername = getCurrentUsername();
        List<String> userRoles = getCurrentUserRoles();
        OrganizationEntity organization = organizationService.getCurrentOrganization();

        // Get executions filtered by organization and user permissions
        List<WorkflowExecutionEntity> executions = workflowExecutionService
                .getWorkflowExecutionsForUserAndOrganization(currentUsername, userRoles, organization);

        if (!filterText.isEmpty()) {
            executions = executions.stream().filter(exec -> (exec.getUploadedFileName() != null &&
                    exec.getUploadedFileName().toLowerCase().contains(filterText)) ||
                    (exec.getWorkflow() != null &&
                            exec.getWorkflow().getName().toLowerCase().contains(filterText))
                    ||
                    (exec.getStatus() != null &&
                            exec.getStatus().toLowerCase().contains(filterText)))
                    .collect(Collectors.toList());
        }

        grid.setItems(executions);
    }

    private void showDetails(WorkflowExecutionEntity entity) {
        Dialog dialog = new Dialog();
        dialog.setWidth("600px");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        H3 title = new H3("Workflow Execution Details");

        Paragraph workflowName = new Paragraph("Workflow: " + entity.getWorkflow().getName());
        Paragraph document = new Paragraph("Document: " + entity.getUploadedFileName());
        Paragraph documentType = new Paragraph("Document Type: " + entity.getDocumentType());
        Paragraph status = new Paragraph("Status: " + entity.getStatus());
        Paragraph createdAt = new Paragraph("Created: " +
                entity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        Paragraph createdBy = new Paragraph("Created By: " + entity.getCreatedBy());

        VerticalLayout decisionInfo = new VerticalLayout();
        decisionInfo.setPadding(false);
        decisionInfo.setSpacing(true);

        if (entity.getReviewDecision() != null && !entity.getReviewDecision().isEmpty()) {
            decisionInfo.add(new Paragraph("Review Decision: " + entity.getReviewDecision()));

            if (entity.getReviewNotes() != null && !entity.getReviewNotes().isEmpty()) {
                TextField reviewNotes = new TextField("Review Notes");
                reviewNotes.setValue(entity.getReviewNotes());
                reviewNotes.setReadOnly(true);
                reviewNotes.setWidthFull();
                decisionInfo.add(reviewNotes);
            }
        }

        if (entity.getApprovalDecision() != null && !entity.getApprovalDecision().isEmpty()) {
            decisionInfo.add(new Paragraph("Approval Decision: " + entity.getApprovalDecision()));

            if (entity.getApprovalNotes() != null && !entity.getApprovalNotes().isEmpty()) {
                TextField approvalNotes = new TextField("Approval Notes");
                approvalNotes.setValue(entity.getApprovalNotes());
                approvalNotes.setReadOnly(true);
                approvalNotes.setWidthFull();
                decisionInfo.add(approvalNotes);
            }
        }

        Button closeButton = new Button("Close");
        closeButton.addClickListener(e -> dialog.close());

        layout.add(title, workflowName, document, documentType, status, createdAt, createdBy, decisionInfo,
                closeButton);
        dialog.add(layout);

        dialog.open();
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

        // Fallback to the default behavior
        return (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "anonymous";
    }
}