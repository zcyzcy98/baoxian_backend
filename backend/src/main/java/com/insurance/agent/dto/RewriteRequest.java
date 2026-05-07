package com.insurance.agent.dto;

public class RewriteRequest {
    private String originalTitle;
    private String originalContent;
    private String mode;
    private String requirements;
    private String model;

    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }

    public String getOriginalContent() { return originalContent; }
    public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
