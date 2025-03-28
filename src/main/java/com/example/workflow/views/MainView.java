package com.example.workflow.views;

import com.example.workflow.repository.WorkflowExecutionRepository;
import com.example.workflow.repository.WorkflowJsonRepository;
import com.example.workflow.service.WorkflowExecutionService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import org.springframework.beans.factory.annotation.Autowired;


@Route("")
@PageTitle("Workflow App")
public class MainView extends AppLayout {

    private final WorkflowExecutionRepository workflowExecutionRepository;
    private final WorkflowJsonRepository workflowJsonRepository;
    private final WorkflowExecutionService workflowExecutionService;
    @Autowired
    public MainView(WorkflowExecutionRepository workflowExecutionRepository,
                      WorkflowJsonRepository workflowJsonRepository,
                      WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionRepository = workflowExecutionRepository;
        this.workflowJsonRepository = workflowJsonRepository;
        this.workflowExecutionService = workflowExecutionService;

        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("Workflow App");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        Tabs tabs = getTabs();

        addToDrawer(tabs);
        addToNavbar(toggle, title);

        setContent(new WorkflowInUseListView(workflowExecutionRepository, workflowJsonRepository, workflowExecutionService));
    }

    private Tabs getTabs() {
        Tabs tabs = new Tabs();
        tabs.add(createTab("Workflow Creator", WorkflowCreatorView.class),
            createTab("Workflow Viewer", WorkflowViewerView.class),
            createTab("Workflows In Use",WorkflowInUseListView.class)
        );
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
