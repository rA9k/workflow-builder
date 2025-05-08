package com.example.workflow.views;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.OrganizationService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Route(value = "workflow-viewer", layout = MainView.class)
public class WorkflowViewerView extends VerticalLayout {

    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final Grid<WorkflowJsonEntity> workflowGrid = new Grid<>(WorkflowJsonEntity.class);

    @Autowired
    private OrganizationService organizationService;

    public WorkflowViewerView(WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            OrganizationService organizationService) {
        this.workflowJsonRepository = workflowJsonRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.organizationService = organizationService;

        // Overall layout styling for a modern feel
        addClassName("workflow-viewer");
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("background-color", "#f4f6f8");

        // Page title
        H1 title = new H1("Workflow Viewer");
        title.getStyle().set("color", "#333");

        // Create Workflow button styled as primary action
        Button createWorkflowButton = new Button("Create Workflow");
        createWorkflowButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createWorkflowButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class)));

        // Toolbar layout for top actions
        HorizontalLayout toolbar = new HorizontalLayout(createWorkflowButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);
        toolbar.getStyle().set("margin-bottom", "20px");

        // Setup grid with modern theme variants and styling
        setupGrid();
        // loadWorkflows();

        // Add components to the view
        add(title, toolbar, workflowGrid);
        expand(workflowGrid);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Now it's safe to call loadWorkflows
        loadWorkflows();
    }

    private void setupGrid() {
        // Remove default columns to customize the grid
        workflowGrid.removeAllColumns();

        // Name column
        workflowGrid.addColumn(WorkflowJsonEntity::getName)
                .setHeader("Name")
                .setAutoWidth(true);

        // Actions column with multiple buttons (View/Edit and Delete)
        workflowGrid.addComponentColumn(entity -> {
            // View/Edit button
            Button viewEditButton = new Button("View/Edit");
            viewEditButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            viewEditButton.addClickListener(
                    e -> getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class, entity.getId())));

            // Delete button
            Button deleteButton = new Button("Delete");
            deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
            deleteButton.addClickListener(e -> showDeleteConfirmationDialog(entity));

            // Layout for action buttons
            HorizontalLayout actions = new HorizontalLayout(viewEditButton, deleteButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions")
                .setAutoWidth(true);

        // Use button column – creates a new execution instance every time.
        // Notice the navigation now uses the route alias "workflow-use/new"
        workflowGrid.addComponentColumn(entity -> {
            Button useButton = new Button("Use");
            useButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            useButton.addClickListener(e -> {
                // Create the execution first, then navigate to it
                getUI().ifPresent(ui -> {
                    // Navigate to the new execution route with the workflow definition ID
                    ui.navigate("workflow-use/new/" + entity.getId());

                    // After navigation, the WorkflowUseView will create the execution
                    // and should update the URL to include the execution ID
                });
            });

            return useButton;
        }).setHeader("")
                .setAutoWidth(true);

        // Apply modern grid themes
        workflowGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        workflowGrid.setHeightFull();
    }

    private void loadWorkflows() {
        OrganizationEntity organization = organizationService.getCurrentOrganization();
        List<WorkflowJsonEntity> workflows = workflowJsonRepository.findByOrganization(organization);
        workflowGrid.setItems(workflows);
    }

    @Transactional
    private void showDeleteConfirmationDialog(WorkflowJsonEntity entity) {
        // Check if workflow belongs to current organization
        OrganizationEntity organization = organizationService.getCurrentOrganization();
        if (entity.getOrganization() == null ||
                !entity.getOrganization().getId().equals(organization.getId())) {
            Notification.show("You don't have permission to delete this workflow",
                    3000, Notification.Position.MIDDLE);
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Confirm Deletion");
        dialog.add("Are you sure you want to permanently delete this workflow?");

        Button confirmButton = new Button("Delete", e -> {
            // Delete associated workflow executions first
            try {
                List<WorkflowExecutionEntity> executions = workflowExecutionRepository.findByWorkflow(entity);
                workflowExecutionRepository.deleteAll(executions);

                // Then delete the workflow
                workflowJsonRepository.delete(entity);
                loadWorkflows();
                dialog.close();
                Notification.show("Workflow deleted successfully.");
            } catch (Exception ex) {
                dialog.close();
                Notification.show("Error deleting workflow: " + ex.getMessage(),
                        5000, Notification.Position.BOTTOM_CENTER);
            }
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelButton, confirmButton);

        dialog.open();
    }
}
