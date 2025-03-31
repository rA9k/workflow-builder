package com.example.workflow.views;

import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("")
@PageTitle("Workflow App")
public class MainView extends AppLayout {
    public MainView(WorkflowExecutionRepository workflowExecutionRepository,
            WorkflowJsonRepository workflowJsonRepository,
            WorkflowExecutionService workflowExecutionService) {

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

        setContent(new WorkflowInUseListView(workflowExecutionRepository, workflowJsonRepository,
                workflowExecutionService));
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
