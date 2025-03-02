package com.example.workflow.views;

import com.example.workflow.model.WorkflowJsonEntity;
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

import java.util.List;

@Route("workflow-viewer")
public class WorkflowViewerView extends VerticalLayout {

    private final WorkflowJsonRepository workflowJsonRepository;
    private final Grid<WorkflowJsonEntity> workflowGrid = new Grid<>(WorkflowJsonEntity.class);

    public WorkflowViewerView(WorkflowJsonRepository workflowJsonRepository) {
        this.workflowJsonRepository = workflowJsonRepository;

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

        // Actions column with multiple buttons
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

            // Create layout for action buttons
            HorizontalLayout actions = new HorizontalLayout(viewEditButton, deleteButton);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Actions")
          .setAutoWidth(true);

        // Use button column
        workflowGrid.addComponentColumn(entity -> {
            Button useButton = new Button("Use");
            useButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            useButton.addClickListener(e -> 
                getUI().ifPresent(ui -> ui.navigate(WorkflowUseView.class, entity.getId()))
            );
            return useButton;
        }).setHeader("")
          .setAutoWidth(true);

        // Apply modern grid themes
        workflowGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        workflowGrid.setHeightFull();

        // Enable navigation on row click (for view/edit)
        workflowGrid.addItemClickListener(event -> 
            getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class, event.getItem().getId()))
        );
    }


    private void loadWorkflows() {
        List<WorkflowJsonEntity> workflows = workflowJsonRepository.findAll();
        workflowGrid.setItems(workflows);
    }

    private void showDeleteConfirmationDialog(WorkflowJsonEntity entity) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Confirm Deletion");
        dialog.add("Are you sure you want to permanently delete this workflow?");

        Button confirmButton = new Button("Delete", e -> {
            workflowJsonRepository.delete(entity);
            loadWorkflows();
            dialog.close();
            Notification.show("Workflow deleted successfully.");
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelButton, confirmButton);

        dialog.open();
    }
}