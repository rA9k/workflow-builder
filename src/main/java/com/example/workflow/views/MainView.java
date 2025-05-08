package com.example.workflow.views;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.OrganizationService;
import com.example.workflow.service.WorkflowExecutionService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("")
@PageTitle("Workflow App")
public class MainView extends AppLayout implements BeforeEnterObserver {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Now you can safely use organizationService
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        try {
            // Try to get current organization
            organizationService.getCurrentOrganization();
        } catch (IllegalStateException e) {
            // If no organization is found, show error
            Notification.show("No organization context found. Please contact your administrator.",
                    3000, Notification.Position.MIDDLE);
        }
    }

    public MainView(WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionService workflowExecutionService,
            WorkflowInUseListView workflowInUseListView) { // Add this parameter

        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("Workflow App");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        Tabs tabs = getTabs();

        // Create a layout for the drawer content
        VerticalLayout drawerContent = new VerticalLayout();
        drawerContent.setSizeFull();
        drawerContent.setPadding(false);
        drawerContent.setSpacing(false);

        // Add tabs to the drawer content
        drawerContent.add(tabs);
        drawerContent.setFlexGrow(1, tabs);

        // Create logout button
        Button logoutButton = new Button("Logout", e -> {
            UI.getCurrent().getPage().executeJs("window.location.href='logout';");
        });
        logoutButton.getStyle()
                .set("margin", "var(--lumo-space-m)")
                .set("width", "calc(100% - var(--lumo-space-m)*2)");

        // Add logout button at the bottom
        drawerContent.add(logoutButton);
        drawerContent.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, logoutButton);

        addToDrawer(drawerContent);
        addToNavbar(toggle, title);

        // Use the injected instance instead of creating a new one
        VerticalLayout welcomeLayout = new VerticalLayout();
        welcomeLayout.setSizeFull();
        welcomeLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        welcomeLayout.setAlignItems(Alignment.CENTER);

        H2 welcomeText = new H2("Welcome to Workflow Builder");
        welcomeLayout.add(welcomeText);

        setContent(welcomeLayout);
    }

    private Tabs getTabs() {
        Tabs tabs = new Tabs();
        tabs.add(createTab("Workflow Creator", WorkflowCreatorView.class),
                createTab("Workflow Viewer", WorkflowViewerView.class),
                createTab("Workflows In Use", WorkflowInUseListView.class));
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        return tabs;
    }

    private Tab createTab(String viewName, Class cls) {
        RouterLink link = new RouterLink();
        link.add(viewName);
        link.setRoute(cls);
        link.setTabIndex(-1);

        return new Tab(link);
    }
}
