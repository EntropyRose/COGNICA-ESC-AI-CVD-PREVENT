package com.example.app.model;

import java.util.List;

public class CognicaRequest {
    private String actionType;
    private String policyInCognicaDPCNL;
    private String query;
    private List<String> complementaryOptions;
    private List<Fact> facts;

    public CognicaRequest() {
    }

    public CognicaRequest(String actionType, String policyInCognicaDPCNL, String query,
                         List<String> complementaryOptions, List<Fact> facts) {
        this.actionType = actionType;
        this.policyInCognicaDPCNL = policyInCognicaDPCNL;
        this.query = query;
        this.complementaryOptions = complementaryOptions;
        this.facts = facts;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
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

    public List<String> getComplementaryOptions() {
        return complementaryOptions;
    }

    public void setComplementaryOptions(List<String> complementaryOptions) {
        this.complementaryOptions = complementaryOptions;
    }

    public List<Fact> getFacts() {
        return facts;
    }

    public void setFacts(List<Fact> facts) {
        this.facts = facts;
    }
}
