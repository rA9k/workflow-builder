package com.example.workflow.service;

import com.example.workflow.entity.OrganizationEntity;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class WorkflowOPAService {

    private final WebClient opaWebClient;

    @Autowired
    private OrganizationService organizationService;

    public WorkflowOPAService(@Value("${opa.url}") String opaUrl) {
        this.opaWebClient = WebClient.builder()
                .baseUrl(opaUrl)
                .build();
    }

    /**
     * Deploys a generated policy for a given workflow.
     * The policy is deployed under a package name unique to the workflow (e.g.,
     * workflows_<workflowId>).
     */
    public void deployWorkflowPolicy(Long workflowId, String policyContent) {
        String policyPackage = "workflow_" + workflowId;
        System.out.println("Deploying policy for " + policyPackage + ":\n" + policyContent);

        try {
            opaWebClient.put()
                    .uri("/v1/policies/" + policyPackage)
                    .header("Content-Type", "text/plain")
                    .bodyValue(policyContent)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> {
                        System.err.println("Error deploying policy: " + error.getMessage());
                    })
                    .block();
        } catch (Exception e) {
            System.err.println("Error deploying workflow policy: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Deploys an upload-stage policy for a workflow execution.
     * This policy is deployed under a package name that combines the workflow id
     * and the execution id.
     * Only the user that created the workflow execution (the initiator) is allowed
     * to perform the upload action.
     */
    public void deployWorkflowExecutionPolicy(Long workflowId, Long executionId, String username) {
        String policyPackageName = "workflow_" + workflowId + "_" + executionId;
        System.out.println("deploying execution-specific policy for " + policyPackageName);
        // Create a policy that only allows the original creator to upload files
        StringBuilder policy = new StringBuilder();
        policy.append("package ").append(policyPackageName).append("\n\n");
        policy.append("default allow = false\n\n");
        policy.append("allow if {\n")
                .append("    input.action == \"upload\"\n")
                .append("    input.username == \"").append(username).append("\"\n")
                .append("}\n");

        System.out.println("Deploying execution-specific policy:\n" + policy);

        opaWebClient.put()
                .uri("/v1/policies/" + policyPackageName)
                .header("Content-Type", "text/plain")
                .bodyValue(policy.toString())
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> {
                    System.err.println("Error deploying execution policy: " + error.getMessage());
                })
                .block();
    }

    /**
     * Checks whether an action is allowed using a given policy package.
     * For instance, this is used for both the workflow-level policies and the
     * execution (upload stage) policy.
     * The method sends an input map that includes the action and a value for a
     * field (such as username or role).
     */
    public boolean isActionAllowed(String policyPackage, String action, String valueToCheck, String fieldName) {
        OrganizationEntity organization = organizationService.getCurrentOrganization();

        Map<String, Object> input = new HashMap<>();
        input.put("action", action);
        input.put(fieldName, valueToCheck);
        input.put("organization_id", organization.getId()); // Add organization ID to policy input

        Map<String, Object> requestBody = Map.of("input", input);

        try {
            JsonNode response = opaWebClient.post()
                    .uri("/v1/data/" + policyPackage + "/allow")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            boolean allowed = response != null && response.path("result").asBoolean(false);
            System.out.println(
                    "OPA check for action [" + action + "] with " + fieldName + " [" + valueToCheck +
                            "] on policy package [" + policyPackage + "] for organization [" + organization.getName() +
                            "] returned: " + allowed);
            return allowed;
        } catch (Exception e) {
            System.err.println("Error checking execution permissions: " + e.getMessage());
            return false;
        }
    }

    public boolean isActionAllowed(Long workflowId, String action, String role) {
        String policyPackage = "workflow_" + workflowId;
        Map<String, Object> input = new HashMap<>();
        input.put("action", action);
        input.put("role", role);

        Map<String, Object> requestBody = Map.of("input", input);

        try {
            JsonNode response = opaWebClient.post()
                    .uri("/v1/data/" + policyPackage + "/allow")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            boolean allowed = response != null && response.path("result").asBoolean(false);
            System.out
                    .println("OPA check for action [" + action + "] with role [" + role + "] on workflow_" + workflowId
                            + " returned: " + allowed);
            return allowed;
        } catch (Exception e) {
            System.err.println("Error checking workflow roles: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}
