package com.example.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Fact {
    private String fact;
    private boolean factHold;
    private String factStatus = "unknown";
    private String factType = "external";

    // existing external provenance field (KEEP)
    private String rpSource;

    // optional internal DP linkage (NEW, complementary)
    private String internalSourceDp;

    public Fact() {}

    public Fact(String fact, boolean factHold) {
        this.fact = fact;
        this.factHold = factHold;
        this.factStatus = factHold ? "true" : "false";
        this.factType = "external";
    }

    public Fact(String fact, boolean factHold, String factStatus, String factType) {
        this.fact = fact;
        this.factHold = factHold;
        this.factStatus = (factStatus == null || factStatus.isBlank())
                ? (factHold ? "true" : "false")
                : factStatus;
        this.factType = factType == null ? "external" : factType;
    }

    // --- core ---

    public String getFact() { return fact; }
    public void setFact(String fact) { this.fact = fact; }

    public boolean isFactHold() { return factHold; }
    public void setFactHold(boolean factHold) {
        this.factHold = factHold;
        this.factStatus = factHold ? "true" : "false";
    }

    public String getFactStatus() { return factStatus; }
    public void setFactStatus(String factStatus) {
        this.factStatus = factStatus;
        this.factHold = "true".equalsIgnoreCase(factStatus);
    }

    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }

    // --- KEEP: external provenance ---
    public String getRpSource() { return rpSource; }
    public void setRpSource(String rpSource) { this.rpSource = rpSource; }

    // --- NEW: internal DP provenance ---
    public String getInternalSourceDp() { return internalSourceDp; }
    public void setInternalSourceDp(String internalSourceDp) {
        this.internalSourceDp = internalSourceDp;
    }

    public boolean hasRpSource() {
        return rpSource != null && !rpSource.isBlank();
    }

    public boolean hasInternalSource() {
        return internalSourceDp != null && !internalSourceDp.isBlank();
    }
}