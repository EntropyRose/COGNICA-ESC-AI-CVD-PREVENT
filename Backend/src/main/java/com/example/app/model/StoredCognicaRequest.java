package com.example.app.model;

import java.time.LocalDateTime;
import java.util.List;

public class StoredCognicaRequest {

    private Long id;
    private String name;
    private String naturalLanguage;
    private String actionType = "getPrediction";
    private String originalPolicyInCognicaDPCNL;
    private String policyInCognicaDPCNL;
    private String query;
    private String complementaryOptionsJson;
    private String factsJson;
    private String version = "default";
    private String lastResponseJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> complementaryOptions;
    private List<Fact> facts;

    public StoredCognicaRequest() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.actionType = "getPrediction";
    }

    public StoredCognicaRequest(String name, String naturalLanguage, String policyInCognicaDPCNL,
                                String query, String complementaryOptionsJson, String factsJson) {
        this.name = name;
        this.naturalLanguage = naturalLanguage;
        this.policyInCognicaDPCNL = policyInCognicaDPCNL;
        this.query = query;
        this.complementaryOptionsJson = complementaryOptionsJson;
        this.factsJson = factsJson;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.actionType = "getPrediction";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNaturalLanguage() { return naturalLanguage; }
    public void setNaturalLanguage(String naturalLanguage) { this.naturalLanguage = naturalLanguage; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getOriginalPolicyInCognicaDPCNL() { return originalPolicyInCognicaDPCNL; }
    public void setOriginalPolicyInCognicaDPCNL(String originalPolicyInCognicaDPCNL) {
        this.originalPolicyInCognicaDPCNL = originalPolicyInCognicaDPCNL;
    }

    public String getPolicyInCognicaDPCNL() { return policyInCognicaDPCNL; }
    public void setPolicyInCognicaDPCNL(String policyInCognicaDPCNL) {
        this.policyInCognicaDPCNL = policyInCognicaDPCNL;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getComplementaryOptionsJson() { return complementaryOptionsJson; }
    public void setComplementaryOptionsJson(String complementaryOptionsJson) {
        this.complementaryOptionsJson = complementaryOptionsJson;
    }

    public List<String> getComplementaryOptions() { return complementaryOptions; }
    public void setComplementaryOptions(List<String> complementaryOptions) {
        this.complementaryOptions = complementaryOptions;
    }

    public String getFactsJson() { return factsJson; }
    public void setFactsJson(String factsJson) { this.factsJson = factsJson; }

    public String getVersion() { return version; }
    public void setVersion(String version) {
        this.version = (version == null || version.isBlank()) ? "default" : version;
    }

    public List<Fact> getFacts() { return facts; }
    public void setFacts(List<Fact> facts) { this.facts = facts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getLastResponseJson() { return lastResponseJson; }
    public void setLastResponseJson(String lastResponseJson) { this.lastResponseJson = lastResponseJson; }
}
