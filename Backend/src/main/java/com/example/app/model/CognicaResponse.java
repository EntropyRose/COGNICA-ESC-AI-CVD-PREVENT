package com.example.app.model;

public class CognicaResponse {
    private boolean hasError;
    private String cognicaPrediction;
    private String cognicaSummaryExplanation;
    private String errorMessage;

    public CognicaResponse() {
    }

    public CognicaResponse(boolean hasError, String cognicaPrediction, String cognicaSummaryExplanation) {
        this.hasError = hasError;
        this.cognicaPrediction = cognicaPrediction;
        this.cognicaSummaryExplanation = cognicaSummaryExplanation;
    }

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public String getCognicaPrediction() {
        return cognicaPrediction;
    }

    public void setCognicaPrediction(String cognicaPrediction) {
        this.cognicaPrediction = cognicaPrediction;
    }

    public String getCognicaSummaryExplanation() {
        return cognicaSummaryExplanation;
    }

    public void setCognicaSummaryExplanation(String cognicaSummaryExplanation) {
        this.cognicaSummaryExplanation = cognicaSummaryExplanation;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
