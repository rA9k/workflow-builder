package com.example.workflow.views;

import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.opa.WorkflowOPAService;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.page.Viewport;
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
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.button.ButtonVariant;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Route(value = "workflow-creator")
@RouteAlias(value = "workflow-creator/:workflowId?")
@CssImport("./styles/wave-styles.css")
@CssImport("./styles/responsive-workflow.css") // New responsive styles
@JsModule("./js/workflow-responsive.js") // New responsive JS
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

    // Inject the OPA service
    @Autowired
    private WorkflowOPAService workflowOPAService;

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long workflowId) {
        if (workflowId != null) {
            workflowJsonRepository.findById(workflowId).ifPresentOrElse(
                    entity -> loadWorkflowFromEntity(entity),
                    () -> Notification.show("Workflow not found with ID: " + workflowId));
        }
    }

    private void loadWorkflowFromEntity(WorkflowJsonEntity entity) {
        try {
            workflowCanvas.removeAll();
            workflowCanvas.add(connectorLayer);
            nodeProperties.clear();

            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> nodesData = mapper.readValue(
                    entity.getData(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
            System.out.println("Loading workflow data from DB: " + entity.getData());

            nodesData.sort(Comparator.comparingInt(n -> (int) n.getOrDefault("order", 0)));

            List<Component> nodes = new ArrayList<>();
            for (Map<String, Object> nodeMap : nodesData) {
                String name = (String) nodeMap.get("name");
                String type = (String) nodeMap.get("type");
                String desc = (String) nodeMap.get("description");
                Map<String, String> additional = (Map<String, String>) nodeMap.get("props");

                Button nodeBtn = createWorkflowButton(type);
                nodeBtn.setText(name);

                WorkflowNodeProperties props = new WorkflowNodeProperties();
                props.name = name;
                props.type = type;
                props.description = desc;
                if (additional != null) {
                    props.additionalProperties.putAll(additional);
                }
                nodeProperties.put(nodeBtn, props);
                workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, nodeBtn);
                nodes.add(nodeBtn);
            }

            UI.getCurrent().getPage().executeJs(
                    "setTimeout(() => { if (window.updateConnectors) window.updateConnectors($0); }, 100);",
                    workflowCanvas.getElement());

            Notification.show("Workflow loaded from DB: " + entity.getId());
        } catch (Exception e) {
            Notification.show("Error loading workflow: " + e.getMessage());
        }
    }

    // Inner class to hold node properties
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

        HorizontalLayout mainContainer = new HorizontalLayout();
        mainContainer.setSizeFull();
        mainContainer.setPadding(false);
        mainContainer.setSpacing(false);
        mainContainer.setClassName("responsive-main-container");

        componentsPanel = new VerticalLayout();
        componentsPanel.addClassName("sidebar-panel");
        componentsPanel.addClassName("responsive-sidebar");
        componentsPanel.setWidth("200px");
        componentsPanel.setHeight("100%");
        componentsPanel.getStyle()
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "1000")
                .set("flex-shrink", "0");

        H3 componentsTitle = new H3("Components");
        componentsTitle.getStyle().set("margin", "0 0 1rem 0");

        // Add a toggle button for mobile view
        Button toggleSidebarBtn = new Button(new Icon(VaadinIcon.MENU));
        toggleSidebarBtn.addClassName("mobile-toggle-btn");
        toggleSidebarBtn.addClickListener(e -> {
            componentsPanel.getElement().executeJs(
                    "this.classList.toggle('mobile-open')");
        });

        // Add the toggle button only for mobile view using CSS
        Div mobileHeader = new Div(toggleSidebarBtn);
        mobileHeader.addClassName("mobile-header");

        componentsPanel.addClickListener(event -> {
            if (componentsPanel.getClassNames().contains("collapsed")) {
                componentsPanel.removeClassName("collapsed");
            } else {
                componentsPanel.addClassName("collapsed");
            }
        });
        componentsPanel.add(componentsTitle);
        componentsPanel.add(createDraggableButton("Upload", true));
        componentsPanel.add(createDraggableButton("Document Review", true));
        componentsPanel.add(createDraggableButton("Approve/Reject", true));
        componentsPanel.add(createDraggableButton("Custom Field", true));

        Div canvasContainer = new Div();
        canvasContainer.addClassName("canvas-container");
        canvasContainer.setSizeFull();
        canvasContainer.getStyle()
                .set("position", "relative")
                .set("overflow", "hidden");

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
                .set("z-index", "2");

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

        propertiesPanel = new VerticalLayout();
        propertiesPanel.addClassName("properties-panel");
        propertiesPanel.setWidth("300px");
        propertiesPanel.getStyle()
                .set("position", "fixed")
                .set("top", "0")
                .set("right", "-300px")
                .set("height", "100vh")
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "-2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "1000")
                .set("transition", "right 0.3s ease-in-out");

        H3 propertiesTitle = new H3("Properties");
        propertiesTitle.getStyle().set("margin", "0 0 1rem 0");
        propertiesPanel.add(propertiesTitle);

        workflowCanvas.add(connectorLayer);
        canvasContainer.add(workflowCanvas);
        canvasContainer.add(propertiesPanel);
        mainContainer.add(componentsPanel, canvasContainer);
        mainContainer.setFlexGrow(0, componentsPanel);
        mainContainer.setFlexGrow(1, canvasContainer);

        HorizontalLayout btnLayout = createActionButtons();

        editModeToggle = new Checkbox("Edit Mode");
        editModeToggle.setValue(true);
        editModeToggle.addValueChangeListener(e -> setEditMode(e.getValue()));
        btnLayout.add(editModeToggle);
        addComponentAsFirst(mobileHeader);
        add(mainContainer, btnLayout);
        setFlexGrow(1, mainContainer);

        setupCanvasDropTarget();

    }

    // -- Canvas & Drag/Drop methods (createDraggableButton, createWorkflowButton,
    // etc.) remain unchanged --
    // (Include the same implementations as before)

    // -- For brevity, only the parts related to OPA integration are shown in full
    // --

    /**
     * Modified saveWorkflow method to generate and deploy OPA policy.
     */
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
                            "  const kids = Array.from(canvas.children).filter(c => !c.classList.contains('connector-layer'));"
                            +
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
                            "       window.addEventListener('resize', () => { if (window.updateConnectors) window.updateConnectors(canvas); });"
                            +
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
            if (editMode) {
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
        if (!editMode)
            return;
        propertiesPanel.removeAll();

        // Create a header layout with title and close button
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);

        H3 propertiesTitle = new H3("Properties");
        propertiesTitle.getStyle().set("margin", "0");

        // Create close button with icon
        Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
        closeButton.addClassName("properties-close-btn");
        closeButton.getStyle()
                .set("background", "transparent")
                .set("color", "#666")
                .set("padding", "0.5rem")
                .set("min-width", "auto")
                .set("border-radius", "50%")
                .set("cursor", "pointer");

        closeButton.addClickListener(e -> {
            deselectComponent();
        });

        headerLayout.add(propertiesTitle, closeButton);
        propertiesPanel.add(headerLayout);

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
        com.vaadin.flow.component.select.Select<String> typeSelectLocal = new com.vaadin.flow.component.select.Select<>();
        typeSelectLocal.setLabel("Document Type");
        typeSelectLocal.setItems("Invoice", "Onboarding", "Report", "Other");
        typeSelectLocal.setWidthFull();

        com.vaadin.flow.component.select.Select<String> deptSelectLocal = new com.vaadin.flow.component.select.Select<>();
        deptSelectLocal.setLabel("Department");
        deptSelectLocal.setItems("HR", "Finance", "Legal", "Operations", "IT");
        deptSelectLocal.setWidthFull();

        // Add/Remove Selects based on type
        // In the showPropertiesPanel method, modify the code for the Select components:

        if ("Upload".equals(props.type)) {
            String defaultDocType = props.additionalProperties.getOrDefault("documentType", "Invoice");
            props.additionalProperties.put("documentType", defaultDocType); // Add this line
            typeSelectLocal.setValue(defaultDocType);
            typeSelectLocal.addValueChangeListener(e -> {
                props.additionalProperties.put("documentType", e.getValue());
                // Rest of the code remains the same
            });
            propertiesPanel.add(typeSelectLocal);
        } else if ("Document Review".equals(props.type)) {
            String defaultDept = props.additionalProperties.getOrDefault("department", "HR");
            props.additionalProperties.put("department", defaultDept); // Add this line
            deptSelectLocal.setValue(defaultDept);
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
                com.vaadin.flow.component.select.Select<String> reviewerRoleSelect = new com.vaadin.flow.component.select.Select<>();
                reviewerRoleSelect.setLabel("Reviewer Role");
                reviewerRoleSelect.setItems("Manager", "Team Lead", "Analyst", "Senior Analyst");
                reviewerRoleSelect.setWidthFull();

                String defaultReviewerRole = props.additionalProperties.getOrDefault("reviewerRole", "Manager");
                props.additionalProperties.put("reviewerRole", defaultReviewerRole); // Add this line
                reviewerRoleSelect.setValue(defaultReviewerRole);

                reviewerRoleSelect
                        .addValueChangeListener(e -> props.additionalProperties.put("reviewerRole", e.getValue()));
                container.add(reviewerRoleSelect);
                break;
            case "Approve/Reject":
                com.vaadin.flow.component.select.Select<String> approverRoleSelect = new com.vaadin.flow.component.select.Select<>();
                approverRoleSelect.setLabel("Approver Role");
                approverRoleSelect.setItems("HR Head", "Senior Manager", "Senior Accountant");
                approverRoleSelect.setWidthFull();

                String defaultApproverRole = props.additionalProperties.getOrDefault("Approver Role", "Senior Manager");
                props.additionalProperties.put("Approver Role", defaultApproverRole); // Add this line
                approverRoleSelect.setValue(defaultApproverRole);

                approverRoleSelect
                        .addValueChangeListener(e -> props.additionalProperties.put("Approver Role", e.getValue()));
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

    // In the WorkflowCreatorView class, modify the clearAllBtn action in the
    // createActionButtons method:

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
        btnLayout.addClassName("responsive-button-layout");
        Button saveAsButton = new Button("Save As", e -> {
            // Create a dummy WorkflowJsonEntity for now. The data will be populated from
            // the current workflow.
            WorkflowJsonEntity entity = new WorkflowJsonEntity();
            // Need to get the current workflow data here

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
            String jsonData = null;
            try {
                jsonData = mapper.writeValueAsString(nodesData);
            } catch (Exception ex) {
                Notification.show("Error saving workflow: " + ex.getMessage());
                return;
            }

            entity.setData(jsonData);
            showSaveAsDialog(entity);
        });
        styleActionButton(saveAsButton, "#28a745");

        btnLayout.add(saveAsButton);

        btnLayout.setWidthFull();
        btnLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        btnLayout.setSpacing(true);
        btnLayout.getStyle()
                .set("padding", "1rem")
                .set("flex-wrap", "wrap"); // Use style property instead of setFlexWrap

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
                    workflowCanvas.getElement());
        } else {
            Notification.show("No component selected to delete!");
        }
    }

    private void deselectComponent() {
        if (editMode && selectedComponent != null) {
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

                    // Generate and deploy the OPA policy for this new workflow
                    String policy = generateWorkflowPolicy(entity.getId());
                    workflowOPAService.deployWorkflowPolicy(entity.getId(), policy);

                    dialog.close();
                } catch (Exception e) {
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

            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(nodesData);

            String path = UI.getCurrent().getInternals().getActiveViewLocation().getPath();
            if (path.matches("workflow-creator/\\d+")) {
                Long workflowId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                workflowJsonRepository.findById(workflowId).ifPresent(entity -> {
                    entity.setData(jsonData);
                    System.out.println("Saving workflow with data: " + jsonData);
                    workflowJsonRepository.save(entity);
                    Notification.show("Workflow updated successfully");
                    System.out.println("Calling saveWorkflow()");
                    // Generate and deploy the OPA policy for this workflow
                    String policy = generateWorkflowPolicy(entity.getId());
                    workflowOPAService.deployWorkflowPolicy(entity.getId(), policy);
                });
            } else {
                showSaveDialog(jsonData);
            }
        } catch (Exception e) {
            Notification.show("Error saving workflow: " + e.getMessage());
        }
    }

    /**
     * Generate a Rego policy for the workflow based on its node properties.
     * Each node type generates a rule for a specific action.
     */
    private String generateWorkflowPolicy(Long workflowId) {
        StringBuilder policy = new StringBuilder();
        policy.append("package workflow_").append(workflowId).append("\n\n");
        policy.append("default allow = false\n\n");

        // Iterate over each node's properties
        for (WorkflowNodeProperties props : nodeProperties.values()) {
            switch (props.type) {
                case "Upload":
                    // Skip creating upload policies - will be handled at the execution level
                    break;
                case "Document Review":
                    // Use reviewer role from the node properties
                    String reviewerRole = props.additionalProperties.getOrDefault("reviewerRole", "manager");
                    policy.append("allow if{\n")
                            .append("    input.action == \"review\"\n")
                            .append("    input.role == \"").append(reviewerRole).append("\"\n")
                            .append("}\n\n");
                    break;
                case "Approve/Reject":
                    // Use approver role from the node properties
                    String approverRole = props.additionalProperties.getOrDefault("Approver Role", "senior_manager");
                    policy.append("allow if{\n")
                            .append("    input.action == \"approve\"\n")
                            .append("    input.role == \"").append(approverRole).append("\"\n")
                            .append("}\n\n");
                    break;
                default:
                    // Handle additional node types here
                    break;
            }
        }

        // Add a general "allow" for uploads at the workflow level
        policy.append("allow if{\n")
                .append("    input.action == \"upload\"\n")
                .append("}\n\n");

        return policy.toString();
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

    private void showSaveAsDialog(WorkflowJsonEntity entity) {
        Dialog dialog = new Dialog();
        dialog.setWidth("400px");

        TextField workflowNameField = new TextField("New Workflow Name");
        workflowNameField.setWidthFull();
        workflowNameField.setPlaceholder("Enter a name for the new workflow");

        Button saveButton = new Button("Save", saveEvent -> {
            String newWorkflowName = workflowNameField.getValue();
            if (newWorkflowName != null && !newWorkflowName.isEmpty()) {
                // Save the workflow as a new entity with the given name
                WorkflowJsonEntity newWorkflow = new WorkflowJsonEntity();
                newWorkflow.setName(newWorkflowName);
                newWorkflow.setData(entity.getData()); // Copy the JSON from the existing workflow
                workflowJsonRepository.save(newWorkflow);
                Notification.show("Workflow saved as '" + newWorkflowName + "'");
                dialog.close();
            } else {
                Notification.show("Workflow name cannot be empty");
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancel", cancelEvent -> dialog.close());

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setWidthFull();
        buttonLayout.setJustifyContentMode(JustifyContentMode.END);
        buttonLayout.setSpacing(true);

        VerticalLayout dialogLayout = new VerticalLayout(workflowNameField, buttonLayout);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);

        dialog.add(dialogLayout);
        dialog.open();
    }

    // ========== Utility ==========

    private String getTypeColor(String type) {
        return switch (type) {
            case "Upload" -> "#4CAF50"; // Green
            case "Document Review" -> "#2196F3"; // Blue
            case "Approve/Reject" -> "#FF9800"; // Orange
            case "Custom" -> "#9C27B0"; // Purple
            default -> "#666666";
        };
    }

    private void updateConnectors() {
        getUI().ifPresent(ui -> ui.getPage().executeJs("if (window.updateConnectors) window.updateConnectors($0);",
                workflowCanvas.getElement()));
    }

    // -- Other methods (e.g. createActionButtons, deleteSelected, setEditMode,
    // etc.) remain unchanged --
}
