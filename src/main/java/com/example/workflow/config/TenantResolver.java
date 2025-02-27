package com.example.workflow.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {
    public String resolveTenant(HttpServletRequest request) {
        // For example, read from a header called "X-Tenant-ID"
        return request.getHeader("X-Tenant-ID");
    }
}
