package com.example.workflow.service;

import com.example.workflow.entity.OrganizationEntity;
import com.example.workflow.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class OrganizationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public OrganizationEntity getCurrentOrganization() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
                OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

                // Debug: Log all claims to help troubleshoot
                logger.info("OIDC Claims: {}", oidcUser.getClaims());

                // Extract organization info from claims
                Map<String, Object> orgClaims = oidcUser.getAttribute("organization");
                logger.info("Organization claims: {}", orgClaims);

                if (orgClaims != null && !orgClaims.isEmpty()) {
                    // Get the first organization (assuming a user belongs to one org for now)
                    String orgName = orgClaims.keySet().iterator().next();
                    Map<String, String> orgDetails = (Map<String, String>) orgClaims.get(orgName);
                    String orgId = orgDetails.get("id");

                    // Find or create the organization
                    Optional<OrganizationEntity> existingOrg = organizationRepository.findById(orgId);
                    if (existingOrg.isPresent()) {
                        return existingOrg.get();
                    } else {
                        // Create new organization if it doesn't exist
                        OrganizationEntity newOrg = new OrganizationEntity();
                        newOrg.setId(orgId);
                        newOrg.setName(orgName);
                        newOrg.setCreatedAt(LocalDateTime.now());
                        return organizationRepository.save(newOrg);
                    }
                }

                // If we can't extract from claims, try to find by username
                String username = oidcUser.getPreferredUsername();
                if (username != null) {
                    // Try to find organization by username (if you have such mapping)
                    // ...
                }
            }

            // If we still don't have an organization, use a default one
            String defaultOrgId = "noOrgFound";
            Optional<OrganizationEntity> defaultOrgOpt = organizationRepository.findById(defaultOrgId);

            if (defaultOrgOpt.isPresent()) {
                logger.info("Using default organization: {}", defaultOrgId);
                return defaultOrgOpt.get();
            } else {
                // Create a default organization if it doesn't exist
                logger.info("Creating default organization with ID: {}", defaultOrgId);
                OrganizationEntity defaultOrg = new OrganizationEntity();
                defaultOrg.setId(defaultOrgId);
                defaultOrg.setName("Default Organization");
                defaultOrg.setCreatedAt(LocalDateTime.now());
                return organizationRepository.save(defaultOrg);
            }
        } catch (Exception e) {
            logger.error("Error in getCurrentOrganization", e);
            // Create an emergency fallback organization
            OrganizationEntity fallbackOrg = new OrganizationEntity();
            fallbackOrg.setId("fallback-org");
            fallbackOrg.setName("Fallback Organization");
            fallbackOrg.setCreatedAt(LocalDateTime.now());
            // Don't save this to the database
            return fallbackOrg;
        }
    }
}
