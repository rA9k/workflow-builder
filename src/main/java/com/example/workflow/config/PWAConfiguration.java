package com.example.workflow.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

@PWA(name = "Workflow Builder", shortName = "Workflows", description = "Custom workflow builder and execution application", backgroundColor = "#f8f9fa", themeColor = "#2196F3", offlineResources = {
        "./styles/wave-styles.css",
        "./images/logo.png"
}, offlinePath = "offline.html")
@Meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
@Meta(name = "apple-mobile-web-app-capable", content = "yes")
@Meta(name = "theme-color", content = "#2196F3")
@Push
@Theme("lumo")
public class PWAConfiguration implements AppShellConfigurator {
    // The AppShellConfigurator interface is implemented to trigger the @PWA
    // scanning
}
