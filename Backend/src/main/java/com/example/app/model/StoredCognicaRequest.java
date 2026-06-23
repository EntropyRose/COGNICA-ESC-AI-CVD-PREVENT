package com.example.app.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "stored_cognica_requests")
public class StoredCognicaRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(name = "natural_language", columnDefinition = "LONGTEXT")
    private String naturalLanguage;
    
    @Column(nullable = false, length = 50)
    private String actionType = "getPrediction";
    
    @Column(name = "original_policy_in_cognica_dp_cnl", columnDefinition = "LONGTEXT")
    private String originalPolicyInCognicaDPCNL;
    
    @Column(name = "policy_in_cognica_dp_cnl", columnDefinition = "LONGTEXT", nullable = false)
    private String policyInCognicaDPCNL;
    
    @Column(name = "`query`", nullable = false, length = 500)
    private String query;
    
    @Column(name = "complementary_options_json", columnDefinition = "LONGTEXT", nullable = false)
    private String complementaryOptionsJson;
    
    @Column(name = "facts_json", columnDefinition = "LONGTEXT", nullable = false)
    private String factsJson;

    @Column(name = "dp_version", nullable = false, length = 50)
    private String version = "default";
    
    @Column(name = "last_response_json", columnDefinition = "LONGTEXT")
    private String lastResponseJson;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Transient fields (not persisted to database) for in-memory object representation
    @Transient
    private List<String> complementaryOptions;
    
    @Transient
    private List<Fact> facts;

    // Constructors
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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNaturalLanguage() {
        return naturalLanguage;
    }

    public void setNaturalLanguage(String naturalLanguage) {
        this.naturalLanguage = naturalLanguage;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getOriginalPolicyInCognicaDPCNL() {
        return originalPolicyInCognicaDPCNL;
    }

    public void setOriginalPolicyInCognicaDPCNL(String originalPolicyInCognicaDPCNL) {
        this.originalPolicyInCognicaDPCNL = originalPolicyInCognicaDPCNL;
    }

    public String getPolicyInCognicaDPCNL() {
        return policyInCognicaDPCNL;
    }

    public void setPolicyInCognicaDPCNL(String policyInCognicaDPCNL) {
        this.policyInCognicaDPCNL = policyInCognicaDPCNL;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getComplementaryOptionsJson() {
        return complementaryOptionsJson;
    }

    public void setComplementaryOptionsJson(String complementaryOptionsJson) {
        this.complementaryOptionsJson = complementaryOptionsJson;
    }

    public List<String> getComplementaryOptions() {
        return complementaryOptions;
    }

    public void setComplementaryOptions(List<String> complementaryOptions) {
        this.complementaryOptions = complementaryOptions;
    }

    public String getFactsJson() {
        return factsJson;
    }

    public void setFactsJson(String factsJson) {
        this.factsJson = factsJson;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null || version.isBlank() ? "default" : version;
    }

    public List<Fact> getFacts() {
        return facts;
    }

    public void setFacts(List<Fact> facts) {
        this.facts = facts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLastResponseJson() {
        return lastResponseJson;
    }

    public void setLastResponseJson(String lastResponseJson) {
        this.lastResponseJson = lastResponseJson;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
