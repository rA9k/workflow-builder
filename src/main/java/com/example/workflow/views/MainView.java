package com.example.workflow.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.RouterLayout;


@Route("")
@PageTitle("Workflow App")
public class MainView extends VerticalLayout implements RouterLayout {
    public MainView() {
        add(new H1("Welcome to the Workflow App"));
        add(new RouterLink("Workflow Creator", WorkflowCreatorView.class));
        add(new RouterLink("Workflow Viewer", WorkflowViewerView.class));
    }
}
