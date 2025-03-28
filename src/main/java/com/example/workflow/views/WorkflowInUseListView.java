package com.example.workflow.views;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionService;
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
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@CssImport("styles/wave-styles.css")
@Route(value = "workflows-in-use", layout = MainView.class)
@PageTitle("Workflows In Use")
public class WorkflowInUseListView extends VerticalLayout {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionService workflowExecutionService;
    private Grid<WorkflowExecutionEntity> grid;
    private TextField filter;

    public WorkflowInUseListView(WorkflowExecutionRepository workflowExecutionRepository,
                                 WorkflowJsonRepository workflowJsonRepository,
                                 WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowJsonRepository = workflowJsonRepository;
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
            String badgeStyle = "padding: 0.25em 0.5em; border-radius: 4px; background-color: #E0E0E0; color: black;"; // Default style

            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> workflowNodes = mapper.readValue(
                    entity.getWorkflow().getData(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {}
                );

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
                // Handle parsing errors
                stageName = "Error: " + e.getMessage();
            }

            // Return a Span with inline styles
            return new Span(new com.vaadin.flow.component.Html("<span style='" + badgeStyle + "'>" + stageName + "</span>"));
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

            Button deleteButton = new Button("Delete");
            deleteButton.getStyle().set("font-size", "0.8em");
            deleteButton.getStyle().set("color", "white");
            deleteButton.getStyle().set("background-color", "#F44336");
            deleteButton.addClickListener(e -> confirmDelete(entity));

            actions.add(viewButton, detailsButton, deleteButton);
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
    com.vaadin.flow.component.notification.Notification notification = 
        com.vaadin.flow.component.notification.Notification.show(message, 3000, 
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
        List<WorkflowExecutionEntity> executions = workflowExecutionService.getWorkflowExecutions();
        grid.setItems(executions);
    }

    private void applyFilter() {
        String filterText = filter.getValue().trim().toLowerCase();

        if (filterText.isEmpty()) {
            refreshGrid();
        } else {
            List<WorkflowExecutionEntity> executions = workflowExecutionService.getWorkflowExecutions();
            grid.setItems((DataProvider<WorkflowExecutionEntity, Void>) executions.stream().filter(exec ->
                (exec.getUploadedFileName() != null &&
                 exec.getUploadedFileName().toLowerCase().contains(filterText)) ||
                (exec.getWorkflow() != null &&
                 exec.getWorkflow().getName().toLowerCase().contains(filterText)) ||
                (exec.getStatus() != null &&
                 exec.getStatus().toLowerCase().contains(filterText))
            ));
        }
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

        layout.add(title, workflowName, document, documentType, status, createdAt, createdBy, decisionInfo, closeButton);
        dialog.add(layout);

        dialog.open();
    }
}