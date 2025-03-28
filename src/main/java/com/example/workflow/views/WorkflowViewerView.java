package com.example.workflow.views;

import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Route(value = "workflow-viewer", layout = MainView.class)
public class WorkflowViewerView extends VerticalLayout {

    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final Grid<WorkflowJsonEntity> workflowGrid = new Grid<>(WorkflowJsonEntity.class);

    public WorkflowViewerView(WorkflowJsonRepository workflowJsonRepository,
                              WorkflowExecutionRepository workflowExecutionRepository) {
        this.workflowJsonRepository = workflowJsonRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;

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
        createWorkflowButton.addClickListener(e ->
                getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class))
        );

        // Toolbar layout for top actions
        HorizontalLayout toolbar = new HorizontalLayout(createWorkflowButton);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.END);
        toolbar.getStyle().set("margin-bottom", "20px");

        // Setup grid with modern theme variants and styling
        setupGrid();
        loadWorkflows();

        // Add components to the view
        add(title, toolbar, workflowGrid);
        expand(workflowGrid);
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
            viewEditButton.addClickListener(e ->
                    getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class, entity.getId()))
            );

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
    // Navigate to the new execution route. 
    // Make sure that 'entity' here is a WorkflowJsonEntity.
    getUI().ifPresent(ui -> ui.getPage().setLocation("workflow-use/new/" + entity.getId()));
    Notification.show("New workflow instance created. " +
        "If previously uploaded files have expired, please re-upload them.");
});

            return useButton;
        }).setHeader("")
          .setAutoWidth(true);

        // Apply modern grid themes
        workflowGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        workflowGrid.setHeightFull();
    }

    /**
     * Creates a new workflow execution instance for the selected workflow.
     * This method is now used solely when deleting or for other operations if needed.
     * The "Use" button now navigates via the new route alias.
     */
    private WorkflowExecutionEntity createNewWorkflowExecution(WorkflowJsonEntity workflowEntity) {
        WorkflowExecutionEntity executionEntity = new WorkflowExecutionEntity();
        executionEntity.setWorkflow(workflowEntity);
        executionEntity.setStatus("NEW");
        // Set any other required properties for a new execution
        return workflowExecutionRepository.save(executionEntity);
    }

    private void loadWorkflows() {
        List<WorkflowJsonEntity> workflows = workflowJsonRepository.findAll();
        workflowGrid.setItems(workflows);
    }

    @Transactional
    private void showDeleteConfirmationDialog(WorkflowJsonEntity entity) {
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
