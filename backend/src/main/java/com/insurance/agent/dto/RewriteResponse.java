package com.insurance.agent.dto;

public class RewriteResponse {
    private String title;
    private String content;
    private String mode;
    private String model;

    public RewriteResponse() {}

    public RewriteResponse(String title, String content, String mode, String model) {
        this.title = title;
        this.content = content;
        this.mode = mode;
        this.model = model;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
