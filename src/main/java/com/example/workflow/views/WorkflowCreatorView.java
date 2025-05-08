package com.example.workflow.views;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.OrganizationService;
import com.example.workflow.service.WorkflowOPAService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.impl.JreJsonFactory;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.button.ButtonVariant;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Route(value = "workflow-creator")
@RouteAlias(value = "workflow-creator/:workflowId?")
@CssImport("./styles/wave-styles.css")
@CssImport("./styles/responsive-workflow.css")
@CssImport("./styles/workflow-connections.css")
@JavaScript("./js/jsplumb.min.js")

@JavaScript("./js/workflow-connections.js")
@JsModule("./js/workflow-responsive.js")
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

    private Map<String, Component> nodeIdMap = new HashMap<>();
    private List<Map<String, String>> connections = new ArrayList<>();

    // Inject the OPA service
    @Autowired
    private WorkflowOPAService workflowOPAService;

    @Autowired
    private OrganizationService organizationService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowCreatorView.class);

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long workflowId) {
        log.info("setParameter called with workflowId: {}", workflowId);
        if (workflowId != null) {
            // Get current organization
            OrganizationEntity organization = organizationService.getCurrentOrganization();

            // Find workflow by ID and organization
            Optional<WorkflowJsonEntity> workflowOpt = workflowJsonRepository.findById(workflowId);

            if (workflowOpt.isPresent()) {
                WorkflowJsonEntity entity = workflowOpt.get();

                // Check if workflow belongs to current organization or has no organization yet
                if (entity.getOrganization() == null ||
                        entity.getOrganization().getId().equals(organization.getId())) {
                    loadWorkflowFromEntity(entity);
                } else {
                    Notification.show("You don't have access to this workflow",
                            3000, Notification.Position.MIDDLE);
                }
            } else {
                Notification.show("Workflow not found with ID: " + workflowId);
            }
        }
    }

    private void loadWorkflowFromEntity(WorkflowJsonEntity entity) {
        try {
            log.info("Loading workflow entity: {}", entity.getId());

            OrganizationEntity currentOrg = organizationService.getCurrentOrganization();
            if (entity.getOrganization() != null &&
                    !entity.getOrganization().getId().equals(currentOrg.getId())) {
                Notification.show("You don't have access to this workflow",
                        3000, Notification.Position.MIDDLE);
                return;
            }
            // Clear existing content without removing the connector layer
            workflowCanvas.getChildren()
                    .filter(component -> component != connectorLayer)
                    .collect(Collectors.toList()) // Create a copy to avoid ConcurrentModificationException
                    .forEach(workflowCanvas::remove);

            // Add the connector layer back if it was removed
            if (!workflowCanvas.getChildren().anyMatch(c -> c == connectorLayer)) {
                workflowCanvas.add(connectorLayer);
            }

            // Clear existing node properties and mappings
            nodeProperties.clear();
            nodeIdMap.clear();
            connections.clear();

            try {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> workflowData = mapper.readValue(
                        entity.getData(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });

                // Extract nodes data
                List<Map<String, Object>> nodesData = (List<Map<String, Object>>) workflowData.getOrDefault("nodes",
                        new ArrayList<>());

                log.info("Loaded {} nodes from workflow data", nodesData.size());

                // Sort nodes by order if available
                nodesData.sort(Comparator.comparingInt(n -> (int) n.getOrDefault("order", 0)));

                // Create and add nodes to the canvas
                for (Map<String, Object> nodeMap : nodesData) {
                    String name = (String) nodeMap.get("name");
                    String type = (String) nodeMap.get("type");
                    String desc = (String) nodeMap.getOrDefault("description", "");

                    @SuppressWarnings("unchecked")
                    Map<String, String> props = (Map<String, String>) nodeMap.getOrDefault("props",
                            new HashMap<String, String>());

                    // Extract or generate node ID
                    String nodeId = (String) nodeMap.get("id");
                    if (nodeId == null) {
                        nodeId = "node-" + UUID.randomUUID().toString();
                    }

                    // Create the workflow button with the node ID
                    Button nodeBtn = createWorkflowButton(type, nodeId);
                    nodeBtn.setText(name);

                    // Create and store node properties
                    WorkflowNodeProperties nodeProps = new WorkflowNodeProperties();
                    nodeProps.name = name;
                    nodeProps.type = type;
                    nodeProps.description = desc;
                    if (props != null) {
                        nodeProps.additionalProperties.putAll(props);
                    }

                    nodeProperties.put(nodeBtn, nodeProps);

                    // Add the node to the canvas before the connector layer
                    workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, nodeBtn);

                    arrangeNodesInGrid();

                    // Set the position of the node if available
                    @SuppressWarnings("unchecked")
                    Map<String, String> position = (Map<String, String>) nodeMap.get("position");
                    if (position != null) {
                        String left = position.get("left");
                        String top = position.get("top");
                        if (left != null && top != null) {
                            nodeBtn.getElement().getStyle().set("left", left);
                            nodeBtn.getElement().getStyle().set("top", top);
                        } else {
                            // Set default position with offset to avoid stacking
                            int index = workflowCanvas.indexOf(nodeBtn);
                            nodeBtn.getElement().getStyle().set("left", (50 + (index * 30)) + "px");
                            nodeBtn.getElement().getStyle().set("top", (50 + (index * 30)) + "px");
                        }
                    } else {
                        // Set default position with offset to avoid stacking
                        int index = workflowCanvas.indexOf(nodeBtn);
                        nodeBtn.getElement().getStyle().set("left", (50 + (index * 30)) + "px");
                        nodeBtn.getElement().getStyle().set("top", (50 + (index * 30)) + "px");
                    }

                    log.debug("Added node {} of type {} with ID {}", name, type, nodeId);
                }

                // Extract connections data
                if (workflowData.containsKey("connections")) {
                    connections = (List<Map<String, String>>) workflowData.get("connections");
                    log.info("Loaded {} connections from workflow data", connections.size());

                    // Check if connections use jsPlumb internal IDs and migrate if needed
                    boolean needsMigration = false;
                    for (Map<String, String> conn : connections) {
                        if (conn.containsKey("source") && conn.get("source").startsWith("jsPlumb_")) {
                            needsMigration = true;
                            break;
                        }
                    }

                    if (needsMigration) {
                        log.info("Migrating connections from jsPlumb internal IDs to node IDs");

                        // Create a mapping from jsPlumb IDs to node IDs
                        Map<String, String> idMapping = new HashMap<>();

                        // This is a simplified approach - in a real implementation,
                        // you would need to determine the mapping based on the DOM structure
                        // For now, we'll just use the node order to map IDs
                        List<String> nodeIds = nodesData.stream()
                                .map(node -> (String) node.get("id"))
                                .collect(Collectors.toList());

                        for (int i = 0; i < nodeIds.size(); i++) {
                            // Map jsPlumb_6_X to the actual node ID
                            idMapping.put("jsPlumb_6_" + (i + 1), nodeIds.get(i));
                        }

                        // Update the connections with the mapped IDs
                        for (Map<String, String> conn : connections) {
                            if (conn.containsKey("source") && idMapping.containsKey(conn.get("source"))) {
                                conn.put("source", idMapping.get(conn.get("source")));
                            }
                            if (conn.containsKey("target") && idMapping.containsKey(conn.get("target"))) {
                                conn.put("target", idMapping.get(conn.get("target")));
                            }
                        }
                    }
                }

                // First ensure jsPlumb is initialized
                UI.getCurrent().getPage().executeJs(
                        "if (!window.workflowConnections || !window.workflowConnections.jsPlumbInstance) {" +
                                "  console.log('Initializing jsPlumb...');" +
                                "  if (window.workflowConnections) {" +
                                "    window.workflowConnections.initialize();" +
                                "  }" +
                                "}");

                // Then load connections with a shorter delay to ensure DOM and jsPlumb are
                // ready
                UI.getCurrent().getPage().executeJs(
                        "setTimeout(function() {" +
                                "  console.log('Loading connections...');" +
                                "  if (window.workflowConnections) {" +
                                "    window.workflowConnections.loadConnections($0);" +
                                "  }" +
                                "}, 500);", // Reduced delay to 0.5 seconds
                        mapper.writeValueAsString(connections));

                log.info("Workflow loaded successfully: {}", entity.getId());
                Notification.show("Workflow loaded successfully", 3000, Notification.Position.BOTTOM_END);

            } catch (Exception e) {
                log.error("Error parsing workflow data", e);
                Notification.show("Error parsing workflow data: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error loading workflow", e);
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
        log.info("WorkflowCreatorView constructor started.");
        this.workflowJsonRepository = workflowJsonRepository;

        setSizeFull();
        addClassName("workflow-layout");

        HorizontalLayout mainContainer = new HorizontalLayout();
        mainContainer.setSizeFull();
        mainContainer.setPadding(false);
        mainContainer.setSpacing(false);
        mainContainer.setClassName("responsive-main-container");

        // In the constructor, update the componentsPanel configuration
        componentsPanel = new VerticalLayout();
        componentsPanel.addClassName("sidebar-panel");
        componentsPanel.addClassName("responsive-sidebar");
        componentsPanel.setWidth("200px");
        componentsPanel.setHeight("100%");
        componentsPanel.setPadding(true);
        componentsPanel.setSpacing(true); // Ensure spacing between components
        componentsPanel.getStyle()
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "1000")
                .set("flex-shrink", "0")
                .set("overflow-y", "auto"); // Add scrolling for many components

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
        workflowCanvas.setSpacing(false);
        workflowCanvas.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("padding", "2rem")
                .set("min-height", "300px")
                .set("overflow", "auto")
                .set("position", "relative") // Add this
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
        // Update the properties panel configuration
        propertiesPanel.getStyle()
                .set("position", "fixed")
                .set("top", "0")
                .set("right", "-300px")
                .set("height", "100vh")
                .set("background-color", "#f8f9fa")
                .set("padding", "1rem")
                .set("box-shadow", "-2px 0 5px rgba(0,0,0,0.1)")
                .set("z-index", "3000") // Increase z-index to match overlay value
                .set("transition", "right 0.3s ease-in-out")
                .set("overflow-y", "auto");

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
        log.info("WorkflowCreatorView constructor finished.");
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
        log.info("onAttach started.");
        super.onAttach(attachEvent);

        // Initialize jsPlumb
        UI.getCurrent().getPage().executeJs(
                "if (window.workflowConnections) {" +
                        "  if (window.workflowConnections.jsPlumbInstance) {" +
                        "    window.workflowConnections.jsPlumbInstance.reset();" +
                        "  }" +
                        "  window.workflowConnections.initialize();" +
                        "  setTimeout(() => window.workflowConnections.refreshAllEndpoints(), 300);" +
                        "}");

        // Setup connection listener
        setupConnectionListener();

        // Set initial edit mode
        UI.getCurrent().getPage().executeJs(
                "setTimeout(function() {" +
                        "  if (window.workflowConnections) { window.workflowConnections.setEditMode($0); }" +
                        "}, 400);",
                editMode);

        log.info("onAttach finished.");
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
                        refreshWorkflowConnections();
                    }
                } else {
                    if (comp.getParent().isPresent()) {
                        ((HasComponents) comp.getParent().get()).remove(comp);
                    }
                    workflowCanvas.addComponentAtIndex(workflowCanvas.getComponentCount() - 1, comp);
                    updateConnectors();
                    refreshWorkflowConnections();
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
            btn.addClassName("toolbar-button");
            // Add margin to prevent stacking
            btn.getStyle().set("margin-bottom", "10px");
        }
        btn.getStyle()
                .set("background-color", "#ffffff")
                .set("border", "1px solid #dee2e6")
                .set("border-radius", "4px")
                .set("cursor", "move")
                .set("border-left", "4px solid " + getTypeColor(label))
                .set("z-index", "2");

        // Handle toolbar buttons differently from canvas buttons
        if (isToolbarButton) {
            // For toolbar buttons, we use Vaadin's drag system
            DragSource<Button> dragSource = DragSource.create(btn);
            dragSource.setDraggable(true);
            dragSource.addDragStartListener(e -> {
                if (editMode) {
                    selectedComponent = btn;
                } else {
                    dragSource.setDraggable(false);
                }
            });

            // Add drag end listener to create new button in canvas
            dragSource.addDragEndListener(e -> {
                if (editMode) {
                    // Create a new button in the canvas at the drop location
                    // This is handled by your drop target listener elsewhere
                }
            });
        } else {
            // For buttons already in the canvas, we'll let jsPlumb handle the dragging
            // Add a data attribute that jsPlumb will use for connections
            String nodeId = UUID.randomUUID().toString();
            btn.getElement().setAttribute("data-node-id", nodeId);

            // We'll initialize jsPlumb endpoints after the button is attached to DOM
            btn.addAttachListener(attachEvent -> {
                UI.getCurrent().getPage().executeJs(
                        "if (window.workflowConnections) { " +
                                "  window.workflowConnections.createEndpoints($0, $1); " +
                                "}",
                        btn.getElement(), nodeId);
            });
        }

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

    // Replace the existing createWorkflowButton method with this implementation
    private Button createWorkflowButton(String label) {
        return createWorkflowButton(label, null);
    }

    private Button createWorkflowButton(String label, String providedNodeId) {
        Button btn = createDraggableButton(label, false);

        // Generate a unique ID for this node or use the provided one
        String nodeId = providedNodeId != null ? providedNodeId : "node-" + UUID.randomUUID().toString();
        btn.getElement().setAttribute("data-node-id", nodeId);
        nodeIdMap.put(nodeId, btn);

        btn.getStyle()
                .set("position", "absolute")
                .set("z-index", "10");

        if (!btn.getStyle().has("left")) {
            // Calculate a position that avoids stacking
            // Get the count of existing nodes to create an offset
            int nodeCount = (int) workflowCanvas.getChildren()
                    .filter(c -> c != connectorLayer)
                    .count();

            // Use a grid-like positioning to avoid overlap
            int row = nodeCount / 3; // 3 nodes per row
            int col = nodeCount % 3;

            btn.getStyle().set("left", (50 + (col * 200)) + "px");
            btn.getStyle().set("top", (50 + (row * 120)) + "px");
        }

        // btn.getElement().executeJs(
        // "this.addEventListener('mousedown', function(e) {" +
        // " if (e.target.classList.contains('jtk-endpoint') ||
        // e.target.closest('.jtk-endpoint')) {" +
        // " e.stopPropagation();" +
        // " }" +
        // "}, true);");

        btn.addClickListener(event -> {

            if (!editMode) {
                return;
            }

            // First, visually deselect the previous component if there was one
            if (selectedComponent != null && selectedComponent != btn) {
                selectedComponent.getStyle()
                        .remove("border-top")
                        .remove("border-right")
                        .remove("border-bottom");
            }

            // Set the new selected component
            selectedComponent = btn;

            // Apply selection styling
            btn.getStyle()
                    .set("border-top", "2px solid #1a73e8")
                    .set("border-right", "2px solid #1a73e8")
                    .set("border-bottom", "2px solid #1a73e8");

            // Force the properties panel to be visible and populated
            propertiesPanel.removeAll();

            // Add the properties content (reuse the same code from showPropertiesPanel)
            HorizontalLayout headerLayout = new HorizontalLayout();
            headerLayout.addClassName("header-layout");
            headerLayout.setWidthFull();
            headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
            headerLayout.setAlignItems(Alignment.CENTER);
            headerLayout.setPadding(false);
            headerLayout.setSpacing(true);

            H3 propertiesTitle = new H3("Properties");
            propertiesTitle.getStyle().set("margin", "0");

            Button closeButton = new Button(new Icon(VaadinIcon.CLOSE));
            closeButton.addClassName("properties-close-btn");
            closeButton.getStyle()
                    .set("background", "transparent")
                    .set("color", "#666")
                    .set("padding", "0.5rem")
                    .set("min-width", "auto")
                    .set("border-radius", "50%")
                    .set("cursor", "pointer")
                    .set("z-index", "3001");

            // Update the closeButton click listener in the createWorkflowButton method:

            closeButton.addClickListener(e -> {
                // Use JavaScript to ensure the panel is properly hidden in all views
                UI.getCurrent().getPage().executeJs(
                        "const panel = document.querySelector('.properties-panel');" +
                                "if (panel) {" +
                                "  panel.style.right = '-300px';" +
                                "  panel.style.visibility = 'hidden';" +
                                "  document.querySelector('.workflow-canvas').style.removeProperty('margin-right');" +
                                "}");

                // Also update the Java-side styles
                propertiesPanel.getStyle().set("right", "-300px");
                propertiesPanel.getStyle().set("visibility", "hidden");
                workflowCanvas.getStyle().remove("margin-right");

                // Deselect the component
                if (selectedComponent != null) {
                    selectedComponent.getStyle()
                            .remove("border-top")
                            .remove("border-right")
                            .remove("border-bottom");
                    selectedComponent = null;
                }
            });

            headerLayout.add(propertiesTitle, closeButton);
            propertiesPanel.add(headerLayout);

            final WorkflowNodeProperties props = nodeProperties.computeIfAbsent(btn, k -> {
                WorkflowNodeProperties newProps = new WorkflowNodeProperties();
                newProps.name = btn.getText();
                newProps.type = btn.getText();
                newProps.description = "";
                return newProps;
            });

            // Add all the property fields
            TextField nameField = new TextField("Name");
            nameField.setValue(props.name);
            nameField.setWidthFull();
            nameField.addValueChangeListener(e -> {
                props.name = e.getValue();
                btn.setText(e.getValue());
                updateConnectors();
            });

            // Add the type-specific fields
            if ("Upload".equals(props.type)) {
                com.vaadin.flow.component.select.Select<String> typeSelectLocal = new com.vaadin.flow.component.select.Select<>();
                typeSelectLocal.setLabel("Document Type");
                typeSelectLocal.setItems("Invoice", "Onboarding", "Report", "Other");
                typeSelectLocal.setWidthFull();

                String defaultDocType = props.additionalProperties.getOrDefault("documentType", "Invoice");
                props.additionalProperties.put("documentType", defaultDocType);
                typeSelectLocal.setValue(defaultDocType);
                typeSelectLocal.addValueChangeListener(e -> {
                    props.additionalProperties.put("documentType", e.getValue());
                });
                propertiesPanel.add(typeSelectLocal);
            } else if ("Document Review".equals(props.type)) {
                com.vaadin.flow.component.select.Select<String> deptSelectLocal = new com.vaadin.flow.component.select.Select<>();
                deptSelectLocal.setLabel("Department");
                deptSelectLocal.setItems("HR", "Finance", "Legal", "Operations", "IT");
                deptSelectLocal.setWidthFull();

                String defaultDept = props.additionalProperties.getOrDefault("department", "HR");
                props.additionalProperties.put("department", defaultDept);
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
            addTypeSpecificFields(typeSpecificFields, props);

            propertiesPanel.add(nameField, descriptionField, typeSpecificFields);

            // Explicitly show the panel with direct style manipulation
            propertiesPanel.getStyle().set("right", "0");
            propertiesPanel.getStyle().set("visibility", "visible");
            propertiesPanel.getStyle().set("display", "flex");
            workflowCanvas.getStyle().set("margin-right", "300px");

            // Force UI update with JavaScript
            UI.getCurrent().getPage().executeJs(
                    "const panel = document.querySelector('.properties-panel');" +
                            "if (panel) {" +
                            "  panel.style.right = '0';" +
                            "  panel.style.visibility = 'visible';" +
                            "  panel.style.display = 'flex';" +
                            "}");
        });

        btn.addAttachListener(event -> {
            UI.getCurrent().getPage().executeJs(
                    "setTimeout(function() {" +
                            "  if (window.workflowConnections) { " +
                            "    window.workflowConnections.createEndpoints($0, $1); " +
                            "  }" +
                            "}, 100);",
                    btn.getElement(), nodeId);
        });

        // DropTarget<Button> drop = DropTarget.create(btn);
        // drop.addDropListener(e -> {
        // if (!editMode) {
        // return;
        // }

        // Optional<Component> dragged = e.getDragSourceComponent();
        // dragged.ifPresent(dc -> {
        // if (dc != btn && workflowCanvas.getChildren().anyMatch(ch -> ch.equals(btn)))
        // {
        // int index = workflowCanvas.indexOf(btn);
        // workflowCanvas.remove(dc);
        // workflowCanvas.addComponentAtIndex(index, dc);
        // updateConnectors();
        // }
        // });
        // });

        btn.addDetachListener(event -> {
            // Remove from nodeIdMap
            nodeIdMap.values().removeIf(component -> component == btn);

            // Remove any connections involving this node
            UI.getCurrent().getPage().executeJs(
                    "window.workflowConnections.removeNodeConnections($0)",
                    nodeId);
        });

        return btn;
    }

    @ClientCallable
    public void updateConnections(String connectionsJson) {
        try {
            // Add a simple check to prevent processing identical connection data repeatedly
            if (connectionsJson == null || connectionsJson.equals("[]")) {
                // Skip processing empty connection arrays
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> newConnections = mapper.readValue(
                    connectionsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {
                    });

            // Check if the connections are actually different before updating and logging
            if (!newConnections.equals(connections)) {
                connections = newConnections;
                System.out.println("Updated connections: " + connections);
            }
        } catch (Exception e) {
            Notification.show("Error updating connections: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupConnectionListener() {
        UI.getCurrent().getPage().executeJs(
                "window.updateNodeConnections = function(connectionsJson) {" +
                        "   $0.$server.updateConnections(connectionsJson);" +
                        "};",
                getElement());
    }

    // ========== Properties Panel ==========

    private void showPropertiesPanel(Component component) {
        if (!editMode)
            return;
        propertiesPanel.removeAll();

        // Create a header layout with title and close button
        HorizontalLayout headerLayout = new HorizontalLayout();
        headerLayout.addClassName("header-layout");
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setPadding(false);
        headerLayout.setSpacing(true);

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
                .set("cursor", "pointer")
                .set("z-index", "3001");

        closeButton.addClickListener(e -> {
            // Explicitly hide the panel
            propertiesPanel.getClassNames().remove("open");
            propertiesPanel.getStyle().set("right", "-300px");
            workflowCanvas.getStyle().remove("margin-right");

            // Deselect the component
            if (selectedComponent != null) {
                selectedComponent.getStyle()
                        .remove("border-top")
                        .remove("border-right")
                        .remove("border-bottom");
                selectedComponent = null;
            }
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

        Button toggleRemovalBtn = new Button("Toggle Connection Removal", e -> {
            UI.getCurrent().getPage().executeJs(
                    "window.workflowConnections.toggleRemovalMode();");
        });
        styleActionButton(toggleRemovalBtn, "#9c27b0"); // Purple color

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
        btnLayout.setWidthFull();
        btnLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        btnLayout.setSpacing(true);
        btnLayout.getStyle()
                .set("padding", "1rem")
                .set("flex-wrap", "wrap")
                .set("gap", "8px"); // Add gap for better spacing in mobile view
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

        btnLayout.add(saveAsButton, toggleRemovalBtn);

        btnLayout.setWidthFull();
        btnLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        btnLayout.setSpacing(true);
        btnLayout.getStyle()
                .set("padding", "1rem")
                .set("flex-wrap", "wrap"); // Use style property instead of setFlexWrap

        UI.getCurrent().getPage().executeJs(
                "window.addEventListener('resize', function() { " +
                        "  const btnLayout = document.querySelector('.responsive-button-layout');" +
                        "  if (window.innerWidth <= 768) {" +
                        "    btnLayout.style.flexDirection = 'column';" +
                        "  } else {" +
                        "    btnLayout.style.flexDirection = 'row';" +
                        "  }" +
                        "});");

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
            String nodeId = selectedComponent.getElement().getAttribute("data-node-id");
            workflowCanvas.remove(selectedComponent);
            nodeProperties.remove(selectedComponent);
            nodeIdMap.values().removeIf(component -> component == selectedComponent);

            if (nodeId != null) {
                // First remove the node connections
                UI.getCurrent().getPage().executeJs(
                        "if (window.workflowConnections) { window.workflowConnections.removeNodeConnections($0); }",
                        nodeId);

                // Then refresh all endpoints to ensure consistency
                UI.getCurrent().getPage().executeJs(
                        "setTimeout(() => { if (window.workflowConnections) window.workflowConnections.refreshAllEndpoints(); }, 100);");
            }

            selectedComponent = null;

            // Use the standard way of hiding the panel
            propertiesPanel.getStyle().set("right", "-300px");
            workflowCanvas.getStyle().remove("margin-right");
        } else {
            Notification.show("No component selected to delete!");
        }
    }

    // Modify the deselectComponent method to accept a parameter indicating whether
    // to hide the properties panel
    private void deselectComponent(boolean hidePropertiesPanel) {
        if (selectedComponent != null) {
            selectedComponent.getStyle()
                    .remove("border-top")
                    .remove("border-right")
                    .remove("border-bottom");

            // Only hide the properties panel if explicitly requested
            if (hidePropertiesPanel) {
                propertiesPanel.getClassNames().remove("open");
                propertiesPanel.getStyle().set("right", "-300px");
                workflowCanvas.getStyle().remove("margin-right");

                // Force UI update
                UI.getCurrent().getPage().executeJs(
                        "const panel = document.querySelector('.properties-panel');" +
                                "if (panel) {" +
                                "  panel.style.right = '-300px';" +
                                "  panel.classList.remove('open');" +
                                "}");
            }

            selectedComponent = null;
        }
    }

    // Add an overloaded method for backward compatibility
    private void deselectComponent() {
        deselectComponent(true);
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

                    // Set the organization
                    entity.setOrganization(organizationService.getCurrentOrganization());

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
            // Get current organization
            OrganizationEntity organization = organizationService.getCurrentOrganization();

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

                            // Save the node ID
                            String nodeId = component.getElement().getAttribute("data-node-id");
                            if (nodeId != null) {
                                nodeMap.put("id", nodeId);
                            }

                            // Save the position of the component
                            String left = component.getElement().getStyle().get("left");
                            String top = component.getElement().getStyle().get("top");
                            if (left != null && top != null) {
                                Map<String, String> position = new HashMap<>();
                                position.put("left", left);
                                position.put("top", top);
                                nodeMap.put("position", position);
                            }

                            nodesData.add(nodeMap);
                        }
                    });

            Map<String, Object> workflowData = new HashMap<>();
            workflowData.put("nodes", nodesData);
            workflowData.put("connections", connections);

            System.out.println("Saving workflow with connections: " + connections);

            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(workflowData);

            String path = UI.getCurrent().getInternals().getActiveViewLocation().getPath();
            if (path.matches("workflow-creator/\\d+")) {
                Long workflowId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

                // Find workflow by ID AND organization
                Optional<WorkflowJsonEntity> workflowOpt = workflowJsonRepository.findById(workflowId);

                if (workflowOpt.isPresent()) {
                    WorkflowJsonEntity entity = workflowOpt.get();

                    // Check if workflow belongs to current organization
                    if (entity.getOrganization() != null &&
                            !entity.getOrganization().getId().equals(organization.getId())) {
                        // If workflow belongs to another organization, save as new
                        WorkflowJsonEntity newEntity = new WorkflowJsonEntity();
                        newEntity.setData(jsonData);
                        showSaveDialog(jsonData);
                        return;
                    }

                    entity.setData(jsonData);
                    // Set organization if not already set
                    if (entity.getOrganization() == null) {
                        entity.setOrganization(organization);
                    }

                    System.out.println("Saving workflow with data: " + jsonData);
                    workflowJsonRepository.save(entity);
                    Notification.show("Workflow updated successfully");

                    // Generate and deploy the OPA policy for this workflow
                    String policy = generateWorkflowPolicy(entity.getId());
                    workflowOPAService.deployWorkflowPolicy(entity.getId(), policy);
                } else {
                    // Workflow not found, create new
                    showSaveDialog(jsonData);
                }
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

        // Update jsPlumb edit mode
        UI.getCurrent().getPage().executeJs(
                "if (window.workflowConnections) { window.workflowConnections.setEditMode($0); }",
                editMode);

        // Hide properties panel when edit mode is disabled
        if (!editMode) {
            deselectComponent();

            // Ensure the properties panel is completely hidden
            propertiesPanel.getStyle()
                    .set("right", "-300px")
                    .set("visibility", "hidden");
            workflowCanvas.getStyle().remove("margin-right");

            // Apply visual indication for read-only components
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

        // Only make toolbar buttons draggable with Vaadin
        // Canvas buttons will be draggable with jsPlumb
        componentsPanel.getChildren().forEach(component -> {
            if (component instanceof Button) {
                Button btn = (Button) component;

                // For toolbar/component panel buttons, use Vaadin drag
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

        // For canvas buttons, we don't need to update draggable state here
        // because jsPlumb will handle it based on the edit mode we set earlier
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

                // Set organization
                newWorkflow.setOrganization(organizationService.getCurrentOrganization());

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

    private void refreshWorkflowConnections() {
        UI.getCurrent().getPage().executeJs(
                "if (window.workflowConnections) { " +
                        "  setTimeout(() => window.workflowConnections.refreshAllEndpoints(), 100); " +
                        "}");
    }

    private void arrangeNodesInGrid() {
        List<Component> nodes = workflowCanvas.getChildren()
                .filter(c -> c != connectorLayer)
                .collect(Collectors.toList());

        int nodeCount = nodes.size();
        if (nodeCount <= 1)
            return;

        // Check if nodes are stacked (have similar positions)
        boolean nodesAreStacked = true;
        String firstLeft = null;
        String firstTop = null;

        for (Component node : nodes) {
            String left = node.getElement().getStyle().get("left");
            String top = node.getElement().getStyle().get("top");

            if (firstLeft == null) {
                firstLeft = left;
                firstTop = top;
            } else if (!firstLeft.equals(left) || !firstTop.equals(top)) {
                nodesAreStacked = false;
                break;
            }
        }

        // If nodes are stacked, arrange them in a grid
        if (nodesAreStacked) {
            int cols = Math.min(3, nodeCount); // Max 3 columns
            int rows = (int) Math.ceil(nodeCount / (double) cols);

            for (int i = 0; i < nodeCount; i++) {
                Component node = nodes.get(i);
                int row = i / cols;
                int col = i % cols;

                node.getElement().getStyle()
                        .set("left", (50 + (col * 200)) + "px")
                        .set("top", (50 + (row * 120)) + "px");
            }

            // Force a repaint of connections
            UI.getCurrent().getPage().executeJs(
                    "setTimeout(() => { if (window.workflowConnections) window.workflowConnections.repaintEverything(); }, 300);");
        }
    }

}
