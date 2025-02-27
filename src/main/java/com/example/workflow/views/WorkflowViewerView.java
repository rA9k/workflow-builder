package com.example.workflow.views;

import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H1;
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

        // View/Edit button column
        workflowGrid.addComponentColumn(entity -> {
            Button viewEditButton = new Button("View/Edit");
            viewEditButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            viewEditButton.addClickListener(e ->
                    getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class, entity.getId()))
            );

            // Save As button column
            Button saveAsButton = new Button("Save As");
            saveAsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            saveAsButton.addClickListener(e -> {
                // Implement Save As functionality here
                // 1. Open a dialog to ask for the new workflow name
                // 2. Save the workflow as a new entity with the given name
                com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
                com.vaadin.flow.component.textfield.TextField workflowNameField = new com.vaadin.flow.component.textfield.TextField("New Workflow Name");
                Button saveButton = new Button("Save", saveEvent -> {
                    String newWorkflowName = workflowNameField.getValue();
                    if (newWorkflowName != null && !newWorkflowName.isEmpty()) {
                        // Save the workflow as a new entity with the given name
                        WorkflowJsonEntity newWorkflow = new WorkflowJsonEntity();
                        newWorkflow.setName(newWorkflowName);
                        newWorkflow.setData(entity.getData()); // Copy the JSON from the existing workflow
                        workflowJsonRepository.save(newWorkflow);
                        loadWorkflows(); // Refresh the grid
                        dialog.close();
                    } else {
                        com.vaadin.flow.component.notification.Notification.show("Workflow name cannot be empty");
                    }
                });
                Button cancelButton = new Button("Cancel", cancelEvent -> dialog.close());

                com.vaadin.flow.component.orderedlayout.HorizontalLayout buttonLayout = new com.vaadin.flow.component.orderedlayout.HorizontalLayout(saveButton, cancelButton);
                dialog.add(workflowNameField, buttonLayout);
                dialog.open();
            });
            return viewEditButton;
        }).setHeader("Actions")
          .setAutoWidth(true);

        // Use button column
        workflowGrid.addComponentColumn(entity -> {
            Button useButton = new Button("Use");
            useButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
            // Placeholder: Implement use functionality as needed
            return useButton;
        }).setHeader("")
          .setAutoWidth(true);

        // Apply modern grid themes
        workflowGrid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        workflowGrid.setHeightFull();

        // Enable navigation on row click
        workflowGrid.addItemClickListener(event -> 
            getUI().ifPresent(ui -> ui.navigate(WorkflowCreatorView.class, event.getItem().getId()))
        );
    }

    private void loadWorkflows() {
        List<WorkflowJsonEntity> workflows = workflowJsonRepository.findAll();
        workflowGrid.setItems(workflows);
    }
}
