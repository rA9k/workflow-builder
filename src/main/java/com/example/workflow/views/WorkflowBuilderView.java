package com.example.workflow.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("workflow-builder")
@PageTitle("Workflow Builder")
public class WorkflowBuilderView extends VerticalLayout {

    private VerticalLayout workflowCanvas; // The drop area (canvas)
    private TextField workflowName; // To give the workflow a name

    public WorkflowBuilderView() {
        add(new H1("Workflow Builder"));

        // Workflow Name input
        workflowName = new TextField("Workflow Name");
        add(workflowName);

        // Workflow canvas (drop area)
        workflowCanvas = new VerticalLayout();
        workflowCanvas.setWidth("100%");
        workflowCanvas.setHeight("400px");
        workflowCanvas.getStyle().set("border", "2px solid #ccc");
        add(workflowCanvas);

        // Predefined workflow steps (draggable items)
        Button step1 = createDraggableStep("Approve");
        Button step2 = createDraggableStep("Upload");
        Button step3 = createDraggableStep("Notify");

        // Add the steps to the layout
        add(step1, step2, step3);

        // Handle the drop event on the workflow canvas
        DropTarget<VerticalLayout> dropTarget = DropTarget.create(workflowCanvas);
        dropTarget.addDropListener(event -> {
            // Handle the drop by extracting the dragged component
            event.getDragSourceComponent().ifPresent(draggedComponent -> {
                if (draggedComponent instanceof Button) {
                    Button draggedButton = (Button) draggedComponent;
                    // Add the dropped step to the canvas
                    workflowCanvas.add(new Button(draggedButton.getText()));
                }
            });
        });
    }

    private Button createDraggableStep(String stepName) {
        Button stepButton = new Button(stepName);
        stepButton.setWidth("150px");

        // Make the button draggable
        DragSource<Button> dragSource = DragSource.create(stepButton);
        
        dragSource.addDragStartListener(event -> {
            event.getSource().getElement().getStyle().set("opacity", "0.5");
        });
        
        dragSource.addDragEndListener(event -> {
            event.getSource().getElement().getStyle().set("opacity", "1");
        });

        return stepButton;
    }
}
