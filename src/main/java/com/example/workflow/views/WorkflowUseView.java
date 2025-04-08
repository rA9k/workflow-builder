package com.example.workflow.views;

import com.example.workflow.components.WorkflowExecutionComponent;
import com.example.workflow.model.WorkflowDefinition;
import com.example.workflow.model.WorkflowExecutionEntity;
import com.example.workflow.model.WorkflowJsonEntity;
import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionEngine;
import com.example.workflow.service.WorkflowOPAService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Route("workflow-use")
@RouteAlias("workflow-use/new")
@CssImport("./styles/responsive-workflow.css")
@JsModule("./js/workflow-responsive.js")
public class WorkflowUseView extends VerticalLayout implements HasUrlParameter<Long>, BeforeEnterObserver {

    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowExecutionEngine executionEngine;
    private final WorkflowOPAService workflowOPAService;
    private Div contentContainer;

    @Autowired
    public WorkflowUseView(
            WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowExecutionEngine executionEngine,
            WorkflowOPAService workflowOPAService) {

        this.workflowJsonRepository = workflowJsonRepository;
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.executionEngine = executionEngine;
        this.workflowOPAService = workflowOPAService;

        setSizeFull();
        setSpacing(false);
        setPadding(true);
        addClassName("responsive-workflow-use");

        // Create header with back button
        HorizontalLayout headerContainer = createHeader();
        add(headerContainer);

        // Create content container
        contentContainer = new Div();
        contentContainer.setWidthFull();
        contentContainer.addClassName("workflow-execution-container");
        add(contentContainer);
    }

    private HorizontalLayout createHeader() {
        HorizontalLayout headerContainer = new HorizontalLayout();
        headerContainer.setWidthFull();
        headerContainer.setSpacing(true);
        headerContainer.setPadding(false);
        headerContainer.setAlignItems(Alignment.CENTER);
        headerContainer.addClassName("workflow-header");

        Button backButton = new Button("Back to Workflows", new Icon(VaadinIcon.ARROW_LEFT));
        backButton.addClassName("back-button");
        backButton.getStyle()
                .set("margin-right", "1rem")
                .set("background-color", "transparent")
                .set("color", "var(--primary-color)");
        backButton.addClickListener(e -> UI.getCurrent().navigate(WorkflowViewerView.class));

        H2 header = new H2("Workflow Execution");
        header.getStyle().set("margin", "0");
        header.addClassName("workflow-title");

        headerContainer.add(backButton, header);
        headerContainer.setFlexGrow(1, header);

        return headerContainer;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Long workflowId = event.getRouteParameters().getLong("id").orElse(null);

        if (workflowId != null) {
            Optional<WorkflowExecutionEntity> executionOpt = workflowExecutionRepository.findById(workflowId);

            if (executionOpt.isPresent()) {
                WorkflowExecutionEntity execution = executionOpt.get();
                String currentUsername = getCurrentUsername();
                List<String> userRoles = getCurrentUserRoles();

                // Check if user has permission to view this workflow
                boolean hasPermission = execution.getCreatedBy().equals(currentUsername);

                if (!hasPermission) {
                    // Check if the user has the required role for the current node
                    try {
                        WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());
                        int currentNodeIndex = execution.getCurrentNodeIndex();

                        if (currentNodeIndex >= 0 && currentNodeIndex < definition.getNodeCount()) {
                            var currentNode = definition.getNodeAt(currentNodeIndex);
                            String nodeType = currentNode.getType();
                            Map<String, String> props = currentNode.getProperties();

                            // For Document Review nodes
                            if ("Document Review".equals(nodeType)) {
                                String reviewerRole = props.getOrDefault("reviewerRole", "");
                                if (!reviewerRole.isEmpty() && userRoles.contains(reviewerRole)) {
                                    hasPermission = true;
                                }
                            }
                            // For Approval nodes
                            else if ("Approve/Reject".equals(nodeType)) {
                                String approverRole = props.getOrDefault("Approver Role", "");
                                if (!approverRole.isEmpty() && userRoles.contains(approverRole)) {
                                    hasPermission = true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Log error but don't fail the check
                        System.err.println("Error checking workflow roles: " + e.getMessage());
                    }
                }

                if (!hasPermission) {
                    // Redirect to workflows list with error message
                    UI.getCurrent().navigate(WorkflowInUseListView.class);
                    Notification.show("You don't have permission to view this workflow",
                            3000, Notification.Position.MIDDLE);
                }
            }
        }
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long parameter) {
        if (parameter == null) {
            Notification.show("No workflow specified");
            UI.getCurrent().navigate(WorkflowViewerView.class);
            return;
        }

        // Clear any previous content
        contentContainer.removeAll();

        String path = event.getLocation().getPath();
        boolean isNew = path.contains("/new");

        try {
            if (isNew) {
                // For a new execution, treat the parameter as a workflow definition ID
                workflowJsonRepository.findById(parameter).ifPresentOrElse(
                        entity -> {
                            try {
                                // Create workflow definition
                                WorkflowDefinition definition = new WorkflowDefinition(entity);

                                // Start a new execution
                                String username = getCurrentUsername();
                                WorkflowExecutionEntity execution = executionEngine.startExecution(definition,
                                        username);

                                // Create the execution component
                                WorkflowExecutionComponent executionComponent = createExecutionComponent(
                                        execution, definition);

                                // Create the progress indicator
                                VerticalLayout progressLayout = createProgressIndicator(execution, definition);

                                // Add both to the content container
                                contentContainer.add(progressLayout);
                                contentContainer.add(executionComponent);

                                // Deploy the execution-specific upload-stage policy
                                workflowOPAService.deployWorkflowExecutionPolicy(
                                        entity.getId(), execution.getId(), username);
                            } catch (Exception e) {
                                Notification.show("Error starting workflow: " + e.getMessage());
                                UI.getCurrent().navigate(WorkflowViewerView.class);
                            }
                        },
                        () -> {
                            Notification.show("Workflow not found");
                            UI.getCurrent().navigate(WorkflowViewerView.class);
                        });
            } else {
                // Otherwise, treat the parameter as a workflow execution ID
                workflowExecutionRepository.findById(parameter).ifPresentOrElse(
                        execution -> {
                            try {
                                // Create workflow definition from the execution's workflow
                                WorkflowDefinition definition = new WorkflowDefinition(execution.getWorkflow());

                                // Create the execution component
                                WorkflowExecutionComponent executionComponent = createExecutionComponent(
                                        execution, definition);

                                // Create the progress indicator
                                VerticalLayout progressLayout = createProgressIndicator(execution, definition);

                                // Add both to the content container
                                contentContainer.add(progressLayout);
                                contentContainer.add(executionComponent);
                            } catch (Exception e) {
                                Notification.show("Error loading execution: " + e.getMessage());
                                UI.getCurrent().navigate(WorkflowViewerView.class);
                            }
                        },
                        () -> {
                            Notification.show("Workflow execution not found");
                            UI.getCurrent().navigate(WorkflowViewerView.class);
                        });
            }
        } catch (Exception e) {
            Notification.show("Error: " + e.getMessage());
            UI.getCurrent().navigate(WorkflowViewerView.class);
        }
    }

    private WorkflowExecutionComponent createExecutionComponent(
            WorkflowExecutionEntity execution, WorkflowDefinition definition) {

        // Create the progress indicator first
        VerticalLayout progressLayout = createProgressIndicator(execution, definition);

        // Create the execution component
        WorkflowExecutionComponent component = new WorkflowExecutionComponent(
                execution, definition, executionEngine);

        // Create a container to hold both the progress indicator and the execution
        // component
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(true);

        // Add the progress indicator at the top
        container.add(progressLayout);

        // Add the execution component below it
        container.add(component);

        // Return the component itself (we'll add the container to the contentContainer
        // in setParameter)
        return component;
    }

    private VerticalLayout createProgressIndicator(
            WorkflowExecutionEntity execution, WorkflowDefinition definition) {

        VerticalLayout progressLayout = new VerticalLayout();
        progressLayout.setSpacing(false);
        progressLayout.setPadding(false);
        progressLayout.setWidthFull();
        progressLayout.addClassName("workflow-progress-container");

        // Create progress bar
        Div progressBar = new Div();
        progressBar.addClassName("workflow-progress-bar");

        Div progressFill = new Div();
        progressFill.addClassName("workflow-progress-fill");

        // Calculate progress percentage
        int totalNodes = definition.getNodeCount();
        int currentNodeIndex = execution.getCurrentNodeIndex();
        int progressPercentage = totalNodes > 0 ? (currentNodeIndex * 100) / totalNodes : 0;

        // Set progress data attribute for JavaScript to use
        progressFill.getElement().setAttribute("data-progress", String.valueOf(progressPercentage));

        progressBar.add(progressFill);
        // progressLayout.add(progressBar);

        // Create stage indicators
        HorizontalLayout stagesLayout = new HorizontalLayout();
        stagesLayout.setWidthFull();
        stagesLayout.setPadding(false);
        stagesLayout.setSpacing(false);
        stagesLayout.addClassName("workflow-stages");

        // Add stage for each node in the workflow
        for (int i = 0; i < totalNodes; i++) {
            var node = definition.getNodeAt(i);

            Div stageDiv = new Div();
            stageDiv.addClassName("workflow-stage");

            Div indicator = new Div();
            indicator.addClassName("stage-indicator");
            if (i < currentNodeIndex) {
                indicator.addClassName("completed");
                indicator.setText("✓");
            } else if (i == currentNodeIndex) {
                indicator.addClassName("active");
                indicator.setText(String.valueOf(i + 1));
            } else {
                indicator.setText(String.valueOf(i + 1));
            }

            Div label = new Div();
            label.setText(node.getName());
            label.addClassName("stage-label");

            stageDiv.add(indicator, label);
            stagesLayout.add(stageDiv);
        }

        progressLayout.add(stagesLayout);
        return progressLayout;
    }

    private List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(role -> role.replace("ROLE_", "")) // Remove ROLE_ prefix if present
                    .collect(Collectors.toList());
        }
        return List.of();
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

        // Fallback to the default behavior if we can't extract from OidcUser
        return (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "anonymous";
    }
}
