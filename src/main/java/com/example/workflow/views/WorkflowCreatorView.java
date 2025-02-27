package com.example.workflow.views;

import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import java.util.*;

@Route(value = "workflow-creator")
@RouteAlias(value = "workflow-creator/:workflowId?")
@CssImport("./styles/wave-styles.css")
public class WorkflowCreatorView extends VerticalLayout implements HasUrlParameter<Long> {

    private final HorizontalLayout workflowCanvas;
    private final VerticalLayout componentsPanel;
    private final VerticalLayout propertiesPanel;
    private final Div connectorLayer;
    private Component selectedComponent;
    private Map<Component, WorkflowNodeProperties> nodeProperties = new HashMap<>();
    private final WorkflowJsonRepository workflowJsonRepository;
    private boolean editMode = true; // Initially in edit mode
    private Checkbox editModeToggle;
    private Button clearAllBtn;

    // private final ComboBox<String> typeCombo = new ComboBox<>("Document Type");
    // private final ComboBox<String> deptCombo = new ComboBox<>("Department");


   @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long workflowId) {
        if (workflowId != null) {
            workflowJsonRepository.findById(workflowId).ifPresentOrElse(
                entity -> loadWorkflowFromEntity(entity),
                () -> Notification.show("Workflow not found with ID: " + workflowId)
            );
        }
    }

    private void loadWorkflowFromEntity(WorkflowJsonEntity entity) {
     try {
         // 1) Clear existing UI
         workflowCanvas.removeAll();
         workflowCanvas.add(connectorLayer);
         nodeProperties.clear();

        // 2) Parse JSON
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> nodesData = mapper.readValue(
            entity.getData(),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
        );
        // After parsing nodesData
        System.out.println("Loading workflow data from DB: " + entity.getData());


        // 3) Sort by 'order'
        nodesData.sort(Comparator.comparingInt(n -> (int) n.getOrDefault("order", 0)));

        // 4) Recreate each node
        List<Component> nodes = new ArrayList<>();
        for (Map<String, Object> nodeMap : nodesData) {
            String name = (String) nodeMap.get("name");
            String type = (String) nodeMap.get("type");
            String desc = (String) nodeMap.get("description");
            Map<String, String> additional = (Map<String, String>) nodeMap.get("props");

             // Create a new button
             Button nodeBtn = createWorkflowButton(type);
             nodeBtn.setText(name);

             // Rebuild nodeProperties
             WorkflowNodeProperties props = new WorkflowNodeProperties();
             props.name = name;
             props.type = type;
             props.description = desc;
             if (additional != null) {
                 props.additionalProperties.putAll(additional);
             }
             nodeProperties.put(nodeBtn, props);

             // Add to canvas
             workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, nodeBtn);
             nodes.add(nodeBtn);
         }

        // 5) Add a small delay before updating connectors to ensure DOM is ready
        UI.getCurrent().getPage().executeJs(
            "setTimeout(() => { if (window.updateConnectors) window.updateConnectors($0); }, 100);",
            workflowCanvas.getElement()
        );
        
        Notification.show("Workflow loaded from DB: " + entity.getId());
    } catch (Exception e) {
        Notification.show("Error loading workflow: " + e.getMessage());
    }
}

    private static class WorkflowNodeProperties {
        String name;
        String type;
        String description;
        Map<String, String> additionalProperties = new HashMap<>();
    }

    public WorkflowCreatorView(WorkflowJsonRepository workflowJsonRepository) {
        this.workflowJsonRepository = workflowJsonRepository;

        setSizeFull();
        addClassName("workflow-layout");

        // Create the main container
        HorizontalLayout mainContainer = new HorizontalLayout();
        mainContainer.setSizeFull();
        mainContainer.setPadding(false);
        mainContainer.setSpacing(false);

        // Setup components panel (left sidebar)
        componentsPanel = new VerticalLayout();
        componentsPanel.addClassName("sidebar-panel");
        componentsPanel.setWidth("200px");
        componentsPanel.setHeight("100%");
        componentsPanel.getStyle()
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "2")
                .set("flex-shrink", "0");

        H3 componentsTitle = new H3("Components");
        componentsTitle.getStyle().set("margin", "0 0 1rem 0");

        componentsPanel.addClickListener(event -> {
            if (componentsPanel.getClassNames().contains("collapsed")) {
                componentsPanel.removeClassName("collapsed");
            } else {
                componentsPanel.addClassName("collapsed");
            }
        });
        componentsPanel.add(componentsTitle);

        // Add draggable buttons to sidebar
        componentsPanel.add(createDraggableButton("Upload", true));
        componentsPanel.add(createDraggableButton("Document Review", true));
        componentsPanel.add(createDraggableButton("Approve/Reject", true));
        componentsPanel.add(createDraggableButton("Custom Field", true));


        // Setup canvas container
        Div canvasContainer = new Div();
        canvasContainer.addClassName("canvas-container");
        canvasContainer.setSizeFull();
        canvasContainer.getStyle()
                .set("position", "relative")
                .set("overflow", "hidden");

        // Setup workflow canvas
        workflowCanvas = new HorizontalLayout();
        workflowCanvas.addClassName("workflow-canvas");
        workflowCanvas.setSizeFull();
        workflowCanvas.setSpacing(true);
        workflowCanvas.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("padding", "2rem")
                .set("min-height", "300px")
                .set("overflow", "auto")
                .set("z-index", "2"); // Ensure canvas is above connector layer


        // Setup the connector layer
        connectorLayer = new Div();
        connectorLayer.addClassName("connector-layer");
        connectorLayer.getStyle()
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("width", "100%")
                .set("height", "100%")
                .set("pointer-events", "none")
                .set("z-index", "1");

        // Setup properties panel (right overlay)
        propertiesPanel = new VerticalLayout();
        propertiesPanel.addClassName("properties-panel");
        propertiesPanel.setWidth("300px");
        propertiesPanel.getStyle()
                .set("position", "fixed")
                .set("top", "0")
                .set("right", "-300px") // Start off-screen
                .set("height", "100vh") // Use viewport height
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "-2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "1000")
                .set("transition", "right 0.3s ease-in-out");

        H3 propertiesTitle = new H3("Properties");
        propertiesTitle.getStyle().set("margin", "0 0 1rem 0");
        propertiesPanel.add(propertiesTitle);

        // Initialize ComboBoxes
        // typeCombo.setItems("Invoice", "Onboarding", "Report", "Other");
        // typeCombo.setWidthFull();

        // deptCombo.setItems("HR", "Finance", "Legal", "Operations", "IT");
        // deptCombo.setWidthFull();


        // Add components to their containers
        workflowCanvas.add(connectorLayer);
        canvasContainer.add(workflowCanvas);
        canvasContainer.add(propertiesPanel);

        mainContainer.add(componentsPanel, canvasContainer);
        mainContainer.setFlexGrow(0, componentsPanel);
        mainContainer.setFlexGrow(1, canvasContainer);

        // Create action buttons
        HorizontalLayout btnLayout = createActionButtons();
        
        editModeToggle = new Checkbox("Edit Mode");
        editModeToggle.setValue(true); // Initially checked
        editModeToggle.addValueChangeListener(e -> setEditMode(e.getValue()));
        btnLayout.add(editModeToggle);

        // Add everything to the main layout
        add(mainContainer, btnLayout);
        setFlexGrow(1, mainContainer);

       // Setup canvas drop target
       setupCanvasDropTarget();

   }
   
    @Override
       protected void onAttach(AttachEvent attachEvent) {
           super.onAttach(attachEvent);
           getUI().ifPresent(ui -> {
               ui.getPage().executeJs(
                   "window.updateConnectors = function(canvas) {" +
                           "  const layer = canvas.querySelector('.connector-layer');" +
                           "  if (!layer) {" +
                           "    console.error('No connector layer');" +
                           "    return;" +
                           "  }" +
                           "  let svg = layer.querySelector('svg');" +
                           "  if (!svg) {" +
                           "    svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');" +
                           "    svg.style.width = '100%';" +
                           "    svg.style.height = '100%';" +
                           "    svg.style.position = 'absolute';" +
                           "    svg.style.top = '0';" +
                           "    svg.style.left = '0';" +
                           "    svg.style.pointerEvents = 'none';" +
                           "    layer.appendChild(svg);" +
                           "    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');" +
                           "    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');" +
                           "    marker.setAttribute('id', 'arrowEnd');" +
                           "    marker.setAttribute('markerWidth', '10');" +
                           "    marker.setAttribute('markerHeight', '10');" +
                           "    marker.setAttribute('refX', '5');" +
                           "    marker.setAttribute('refY', '3');" +
                           "    marker.setAttribute('orient', 'auto');" +
                           "    const pathEl = document.createElementNS('http://www.w3.org/2000/svg', 'path');" +
                           "    pathEl.setAttribute('d', 'M0,0 L0,6 L6,3 z');" +
                           "    pathEl.setAttribute('fill', '#333');" +
                           "    marker.appendChild(pathEl);" +
                           "    defs.appendChild(marker);" +
                           "    svg.appendChild(defs);" +
                           "  }" +
                           "  svg.innerHTML = '';" +
                           "  const defsNode = svg.querySelector('defs');" +
                           "  if (defsNode) svg.appendChild(defsNode);" +
                           "  const kids = Array.from(canvas.children).filter(c => !c.classList.contains('connector-layer'));" +
                           "  for (let i = 0; i < kids.length - 1; i++) {" +
                           "    const c1 = kids[i].getBoundingClientRect();" +
                           "    const c2 = kids[i+1].getBoundingClientRect();" +
                           "    const can = canvas.getBoundingClientRect();" +
                           "    const x1 = c1.left + c1.width - can.left - 5;" +
                           "    const y1 = c1.top + (c1.height / 2) - can.top;" +
                           "    const x2 = c2.left - can.left + 5;" +
                           "    const y2 = c2.top + (c2.height / 2) - can.top;" +
                           "    const dx = x2 - x1;" +
                           "    const off = 0.3 * dx;" +
                           "    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');" +
                           "    const d = `M ${x1} ${y1} C ${x1 + off} ${y1} ${x2 - off} ${y2} ${x2} ${y2}`;" +
                           "    path.setAttribute('d', d);" +
                           "    path.setAttribute('stroke', '#333');" +
                           "    path.setAttribute('stroke-width', '2');" +
                           "    path.setAttribute('fill', 'none');" +
                           "    path.setAttribute('marker-end', 'url(#arrowEnd)');" +
                           "    svg.appendChild(path);" +
                           "  }" +
                           "};" +
                   "   window.addResizeListener = function(canvas) {" +
                   "       window.addEventListener('resize', () => { if (window.updateConnectors) window.updateConnectors(canvas); });" +
                   "   };");
                   ui.getPage().executeJs("window.addResizeListener($0);", workflowCanvas.getElement());
           });
       }


    // ========== Canvas & Drag/Drop Setup ==========update

    // Update the setupCanvasDropTarget method:

private void setupCanvasDropTarget() {
    DropTarget<HorizontalLayout> canvasDropTarget = DropTarget.create(workflowCanvas);
    canvasDropTarget.addDropListener(event -> {
        if (!editMode) {
            return; // Ignore drops when not in edit mode
        }
        
        Optional<Component> dragged = event.getDragSourceComponent();
        dragged.ifPresent(comp -> {
            // Removed auto-deselection of previous component
            
            if (comp.getParent().orElse(null) == componentsPanel) {
                if (comp instanceof Button) {
                    Button original = (Button) comp;
                    Button newBtn = createWorkflowButton(original.getText());
                    workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, newBtn);
                    initializeNodeProperties(newBtn);                   
                    updateConnectors();
                }
            } else {
                if (comp.getParent().isPresent()) {
                    ((HasComponents) comp.getParent().get()).remove(comp);
                }
                workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, comp);
                updateConnectors();
            }
        });
    });

    workflowCanvas.getElement().addEventListener("click", e -> {
        if (e.getEventData().getBoolean("event.target === event.currentTarget")) {
            deselectComponent();
        }
    }).addEventData("event.target === event.currentTarget");
}


    private void initializeNodeProperties(Component component) {
        WorkflowNodeProperties props = new WorkflowNodeProperties();
        if (component instanceof Button btn) {
            props.name = "New " + btn.getText();
            props.type = btn.getText();
        } else {
            props.name = "New Node";
            props.type = "Custom";
        }
        props.description = "";
        nodeProperties.put(component, props);
    }

    private Button createDraggableButton(String label, boolean isToolbarButton) {
    Button btn = new Button(label);
    btn.addClassName("workflow-button");
    if (isToolbarButton) {
        btn.addClassName("toolbar-button"); // only add for toolbar buttons
    }
    btn.getStyle()
            .set("background-color", "#ffffff")
            .set("border", "1px solid #dee2e6")
            .set("border-radius", "4px")
            .set("cursor", "move")
            .set("border-left", "4px solid " + getTypeColor(label))
            .set("z-index", "2");

    DragSource<Button> dragSource = DragSource.create(btn);
    dragSource.setDraggable(true);
    dragSource.addDragStartListener(e -> {
        if (editMode) {
            selectedComponent = btn;
        } else {
            dragSource.setDraggable(false); // Disable dragging if not in edit mode
        }
    });

    btn.addClickListener(evt -> {
        if(editMode){
            if (selectedComponent != null && selectedComponent != btn) {
                deselectComponent();
            }
            selectedComponent = btn;
            btn.getStyle()
                    .set("border-top", "2px solid #1a73e8")
                    .set("border-right", "2px solid #1a73e8")
                    .set("border-bottom", "2px solid #1a73e8");
            showPropertiesPanel(btn);
        }
    });

    return btn;
}


    private Button createWorkflowButton(String label) {
        Button btn = createDraggableButton(label, false);
    
        btn.addClickListener(event -> {
            if (!editMode) {
                return;
            }
        
            if (selectedComponent != null && selectedComponent != btn) {
                deselectComponent();
            }
            selectedComponent = btn;
            btn.getStyle()
                .set("border-top", "2px solid #1a73e8")
                .set("border-right", "2px solid #1a73e8")
                .set("border-bottom", "2px solid #1a73e8");
            showPropertiesPanel(btn);
        });

        DropTarget<Button> drop = DropTarget.create(btn);
        drop.addDropListener(e -> {
            if (!editMode) {
                return; // Ignore drops when not in edit mode
            }
        
            Optional<Component> dragged = e.getDragSourceComponent();
            dragged.ifPresent(dc -> {
                if (dc != btn && workflowCanvas.getChildren().anyMatch(ch -> ch.equals(btn))) {
                    int index = workflowCanvas.indexOf(btn);
                    workflowCanvas.remove(dc);
                    workflowCanvas.addComponentAtIndex(index, dc);
                    updateConnectors();
                }
            });
        });

        return btn;
    }
    // ========== Properties Panel ==========

    private void showPropertiesPanel(Component component) {
    if(!editMode) return;
    propertiesPanel.removeAll();
    propertiesPanel.add(new H3("Properties"));

    final WorkflowNodeProperties props = nodeProperties.computeIfAbsent(component, k -> {
        WorkflowNodeProperties newProps = new WorkflowNodeProperties();
        newProps.name = component instanceof Button ? ((Button) component).getText() : "New Component";
        newProps.type = component instanceof Button ? ((Button) component).getText() : "Custom";
        newProps.description = "";
        return newProps;
    });

    // Name field
    TextField nameField = new TextField("Name");
    nameField.setValue(props.name);
    nameField.setWidthFull();
    nameField.addValueChangeListener(e -> {
        props.name = e.getValue();
        if (component instanceof Button) {
            ((Button) component).setText(e.getValue());
        }
        updateConnectors();
    });
    
    // Use Select components instead of ComboBox
    com.vaadin.flow.component.select.Select<String> typeSelectLocal = 
        new com.vaadin.flow.component.select.Select<>();
    typeSelectLocal.setLabel("Document Type");
    typeSelectLocal.setItems("Invoice", "Onboarding", "Report", "Other");
    typeSelectLocal.setWidthFull();
    
    com.vaadin.flow.component.select.Select<String> deptSelectLocal = 
        new com.vaadin.flow.component.select.Select<>();
    deptSelectLocal.setLabel("Department");
    deptSelectLocal.setItems("HR", "Finance", "Legal", "Operations", "IT");
    deptSelectLocal.setWidthFull();

    // Add/Remove Selects based on type
    if ("Upload".equals(props.type)) {
        typeSelectLocal.setValue(props.additionalProperties.getOrDefault("documentType", "Invoice"));
        typeSelectLocal.addValueChangeListener(e -> {
            props.additionalProperties.put("documentType", e.getValue());
            // Refresh the type-specific fields whenever the document type changes
            VerticalLayout typeSpecificFields = (VerticalLayout) propertiesPanel.getChildren()
                .filter(c -> c instanceof VerticalLayout && c != typeSelectLocal.getParent().get())
                .findFirst().orElse(new VerticalLayout());
            typeSpecificFields.removeAll();
            addTypeSpecificFields(typeSpecificFields, props);

        });
        propertiesPanel.add(typeSelectLocal);
    } else if ("Document Review".equals(props.type)) {
        deptSelectLocal.setValue(props.additionalProperties.getOrDefault("department", "HR"));
        deptSelectLocal.addValueChangeListener(e -> {
            props.additionalProperties.put("department", e.getValue());
        });
        propertiesPanel.add(deptSelectLocal);
    } else if ("Custom Field".equals(props.type)) {
        TextField labelField = new TextField("Label");
        labelField.setValue(props.additionalProperties.getOrDefault("label", ""));
        labelField.setWidthFull();
        labelField.addValueChangeListener(e -> props.additionalProperties.put("label", e.getValue()));

        TextField valueField = new TextField("Value");
        valueField.setValue(props.additionalProperties.getOrDefault("value", ""));
        valueField.setWidthFull();
        valueField.addValueChangeListener(e -> props.additionalProperties.put("value", e.getValue()));

        propertiesPanel.add(labelField, valueField);
    }

    TextField descriptionField = new TextField("Description");
    descriptionField.setValue(props.description);
    descriptionField.setWidthFull();
    descriptionField.addValueChangeListener(e -> props.description = e.getValue());

    VerticalLayout typeSpecificFields = new VerticalLayout();
    typeSpecificFields.setPadding(false);
    addTypeSpecificFields(typeSpecificFields, props); // Call initially

    propertiesPanel.add(nameField, descriptionField, typeSpecificFields);
    propertiesPanel.getClassNames().add("open");
    propertiesPanel.getStyle().set("right", "0");
    propertiesPanel.setVisible(true);
    workflowCanvas.getStyle().set("margin-right", "300px");
}

private void addTypeSpecificFields(VerticalLayout container, WorkflowNodeProperties props) {
        container.removeAll();
        switch (props.type) {
            case "Upload":
                if ("Invoice".equals(props.additionalProperties.getOrDefault("documentType", "Invoice"))) {
                    addField(container, props, "Amount Threshold", "1000");
                }
                break;
            case "Document Review":
                addField(container, props, "Review Duration (days)", "3");
                com.vaadin.flow.component.select.Select<String> reviewerRoleSelect =
                        new com.vaadin.flow.component.select.Select<>();
                reviewerRoleSelect.setLabel("Reviewer Role");
                reviewerRoleSelect.setItems("Manager", "Team Lead", "Analyst", "Senior Analyst");
                reviewerRoleSelect.setWidthFull();
                reviewerRoleSelect.setValue(props.additionalProperties.getOrDefault("reviewerRole", "Manager"));
                reviewerRoleSelect.addValueChangeListener(e -> props.additionalProperties.put("reviewerRole", e.getValue()));
                container.add(reviewerRoleSelect);
                break;
            case "Approve/Reject":
                com.vaadin.flow.component.select.Select<String> approverRoleSelect =
                        new com.vaadin.flow.component.select.Select<>();
                approverRoleSelect.setLabel("Approver Role");
                approverRoleSelect.setItems("HR Head", "Senior Manager", "Senior Accountant");
                approverRoleSelect.setWidthFull();
                approverRoleSelect.setValue(props.additionalProperties.getOrDefault("Approver Role", "Senior Manager"));
                approverRoleSelect.addValueChangeListener(e -> props.additionalProperties.put("Approver Role", e.getValue()));
                container.add(approverRoleSelect);

                addField(container, props, "Timeout (hours)", "48");
                break;
            default:
                break;
        }
    }

    private void addField(VerticalLayout container, WorkflowNodeProperties props, String label, String defaultValue) {
        TextField field = new TextField(label);
        field.setWidthFull();
        field.setValue(props.additionalProperties.getOrDefault(label, defaultValue));
        field.addValueChangeListener(e -> props.additionalProperties.put(label, e.getValue()));
        container.add(field);
    }

    // ========== Actions & Buttons ==========

    // In the WorkflowCreatorView class, modify the clearAllBtn action in the createActionButtons method:

// Fix for the createActionButtons method
private HorizontalLayout createActionButtons() {
    Button saveBtn = new Button("Save Workflow", e -> saveWorkflow());
    Button deleteBtn = new Button("Delete", e -> {
        deleteSelected();
        propertiesPanel.getStyle().set("display", "none");
    });
    
    // Fix the Clear All button to properly preserve the connector layer
    Button clearAllBtn = new Button("Clear All", e -> {
        // First deselect any selected component
        deselectComponent();
        // Remove all components except connectorLayer
        List<Component> componentsToRemove = new ArrayList<>();
        workflowCanvas.getChildren().forEach(component -> {
            if (component != connectorLayer) {
                componentsToRemove.add(component);
            }
        });

        // Now remove them
        componentsToRemove.forEach(workflowCanvas::remove);

        // Clear node properties
        nodeProperties.clear();

        // Reset the properties panel
        propertiesPanel.getStyle().set("right", "-300px");
        workflowCanvas.getStyle().remove("margin-right");

        // Update connectors
        updateConnectors();
    });

    this.clearAllBtn = clearAllBtn; // Store a reference to the button.

    // Fix for duplicate button definition
    Button viewWorkflowsBtn = new Button("View Workflows", e -> {
        UI.getCurrent().navigate("workflow-viewer");
    });
    
    styleActionButton(saveBtn, "#28a745");
    styleActionButton(deleteBtn, "#dc3545"); 
    styleActionButton(clearAllBtn, "#ffc107");
    styleActionButton(viewWorkflowsBtn, "#17a2b8");

    HorizontalLayout btnLayout = new HorizontalLayout(saveBtn, deleteBtn, clearAllBtn, viewWorkflowsBtn);
    btnLayout.setWidthFull();
    btnLayout.setJustifyContentMode(JustifyContentMode.CENTER);
    btnLayout.setSpacing(true);
    btnLayout.getStyle().set("padding", "1rem");

    return btnLayout;
}

    private void styleActionButton(Button btn, String color) {
        btn.getStyle()
                .set("background-color", color)
                .set("color", "#ffffff")
                .set("border", "none")
                .set("border-radius", "4px")
                .set("padding", "0.75rem 1.5rem")
                .set("font-weight", "500")
                .set("cursor", "pointer")
                .set("transition", "opacity 0.2s");
    }

    // Update the deleteSelected method:

private void deleteSelected() {
    if (!editMode) {
        return; // Don't allow deletion when not in edit mode
    }
    
    if (selectedComponent != null &&
            workflowCanvas.getChildren().anyMatch(ch -> ch.equals(selectedComponent))) {
        workflowCanvas.remove(selectedComponent);
        nodeProperties.remove(selectedComponent);
        selectedComponent = null;
        
        // Use the standard way of hiding the panel
        propertiesPanel.getStyle().set("right", "-300px");
        workflowCanvas.getStyle().remove("margin-right");
        
        // Update connectors after deletion
        UI.getCurrent().getPage().executeJs(
            "setTimeout(() => { if (window.updateConnectors) window.updateConnectors($0); }, 50);",
            workflowCanvas.getElement()
        );
    } else {
        Notification.show("No component selected to delete!");
    }
}


    private void deselectComponent() {
    if(editMode && selectedComponent != null) {
        selectedComponent.getStyle()
            .remove("border-top")
            .remove("border-right")
            .remove("border-bottom");
            
        // Hide the panel by sliding it out
        propertiesPanel.getStyle().set("right", "-300px");
        workflowCanvas.getStyle().remove("margin-right");
        
        selectedComponent = null;
    }
}


    private void showSaveDialog(String jsonData) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        TextField nameField = new TextField("Workflow Name");
        nameField.setWidthFull();
        Button saveButton = new Button("Save", event -> {
            String workflowName = nameField.getValue();
            if (workflowName != null && !workflowName.isEmpty()) {
                try {
                    WorkflowJsonEntity entity = new WorkflowJsonEntity();
                    entity.setName(workflowName);
                    // Add this debug logging right before saving
                    System.out.println("About to save workflow with data: " + jsonData);
                    entity.setData(jsonData);
                    workflowJsonRepository.save(entity);
                    Notification.show("Workflow saved with ID: " + entity.getId());
                    dialog.close();
                }  catch (Exception e) {
                    Notification.show("Error saving workflow: " + e.getMessage());
                }
            } else {
                Notification.show("Please enter a workflow name.");
            }
        });

        Button cancelButton = new Button("Cancel", event -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        dialog.add(nameField, buttonLayout);
        dialog.open();
    }
    private void saveWorkflow() {
        try {
            // 1) Build a list of node data
            List<Map<String, Object>> nodesData = new ArrayList<>();

            workflowCanvas.getChildren()
                    .filter(c -> c != connectorLayer)
                    .forEach(component -> {
                        WorkflowNodeProperties props = nodeProperties.get(component);
                        if (props != null) {
                            Map<String, Object> nodeMap = new HashMap<>();
                            nodeMap.put("name", props.name);
                            nodeMap.put("type", props.type);
                            nodeMap.put("description", props.description);
                            nodeMap.put("props", props.additionalProperties);
                            nodeMap.put("order", workflowCanvas.indexOf(component));
                            nodesData.add(nodeMap);
                        }
                    });
            // 2) Serialize to JSON
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(nodesData);

            
            // Check if we're editing an existing workflow
            String path = UI.getCurrent().getInternals().getActiveViewLocation().getPath();
            if (path.matches("workflow-creator/\\d+")) {
                Long workflowId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                workflowJsonRepository.findById(workflowId).ifPresent(entity -> {
                    entity.setData(jsonData);
                    // Add this debug logging right before saving
                    System.out.println("About to save workflow with data: " + jsonData);

                    workflowJsonRepository.save(entity);
                    Notification.show("Workflow updated successfully");
                });
            } else {
                showSaveDialog(jsonData);
            }
        } catch (Exception e) {
            Notification.show("Error saving workflow: " + e.getMessage());
        }
    }

    
    // Improve the setEditMode method:

private void setEditMode(boolean editMode) {
    this.editMode = editMode;

    // Hide properties panel when edit mode is disabled
    if (!editMode) {
        deselectComponent();

        // Ensure the properties panel is completely hidden
        propertiesPanel.getStyle()
            .set("right", "-300px")
            .set("visibility", "hidden");
        workflowCanvas.getStyle().remove("margin-right");
        
        // Apply subtle visual indication for read-only components
        workflowCanvas.getChildren().forEach(component -> {
            if (component instanceof Button && component != connectorLayer) {
                component.getStyle()
                    .set("opacity", "0.7")
                    .set("cursor", "default")
                    .set("filter", "grayscale(1)");
            }
        });
    } else {
        // When switching back to edit mode, restore normal appearance
        propertiesPanel.getStyle().set("visibility", "visible");
        
        // Restore normal styling to workflow buttons
        workflowCanvas.getChildren().forEach(component -> {
            if (component instanceof Button && component != connectorLayer) {
                component.getStyle()
                    .set("opacity", "1")
                    .set("cursor", "move")
                    .remove("filter");
            }
        });
    }

    // Disable the Clear All button when not in edit mode
    if (clearAllBtn != null) {
        clearAllBtn.setEnabled(editMode);
    }

    // Update all buttons in the workflow canvas
    workflowCanvas.getChildren().forEach(component -> {
        if (component instanceof Button && component != connectorLayer) {
            Button btn = (Button) component;
            DragSource<Button> dragSource = DragSource.create(btn);
            dragSource.setDraggable(editMode);
        }
    });

    // Update drag sources in the components panel too
    componentsPanel.getChildren().forEach(component -> {
        if (component instanceof Button) {
            Button btn = (Button) component;
            DragSource<Button> dragSource = DragSource.create(btn);
            dragSource.setDraggable(editMode);
            
            // In read-only mode, make component panel buttons appear disabled
            if (!editMode) {
                btn.getStyle()
                    .set("opacity", "0.6")
                    .set("cursor", "not-allowed");
            } else {
                btn.getStyle()
                    .set("opacity", "1")
                    .set("cursor", "move");
            }
        }
    });
}

    // ========== Utility ==========

    private String getTypeColor(String type) {
        return switch (type) {
            case "Upload" -> "#4CAF50";          // Green
            case "Document Review" -> "#2196F3"; // Blue
            case "Approve/Reject" -> "#FF9800";  // Orange
            case "Custom" -> "#9C27B0";          // Purple
            default -> "#666666";
        };
    }
     
     
    private void updateConnectors() {
        getUI().ifPresent(ui ->
                ui.getPage().executeJs("if (window.updateConnectors) window.updateConnectors($0);", workflowCanvas.getElement())
        );
    }
}