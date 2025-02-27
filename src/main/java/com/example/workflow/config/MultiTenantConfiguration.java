package com.example.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiTenantConfiguration {
    @Bean
    public TenantResolver tenantResolver() {
        return new TenantResolver();
    }
}
