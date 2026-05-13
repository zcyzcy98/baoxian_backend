package com.insurance.agent.dto;

import java.util.List;
import java.util.Map;

public class AgentResponse {
    private String content;
    private String cleanedContent;
    private String imageUrl;
    private String coverUrl;
    private String videoUrl;
    private List<String> resultUrls;
    private String sessionId;
    private String projectUuid;
    private String projectUrl;
    private Long nextSeq;
    private String statusText;
    private String model;
    private Integer currentStep;
    private Integer totalSteps;
    private Integer progressPercent;
    private String stepName;
    private List<String> steps;
    private Boolean finalResultReady;
    private Integer allVideoCount;
    private List<Map<String, String>> complianceWarnings;
    private List<Map<String, String>> citations;

    public AgentResponse() {}

    public AgentResponse(String content, String model) {
        this.content = content;
        this.model = model;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCleanedContent() { return cleanedContent; }
    public void setCleanedContent(String cleanedContent) { this.cleanedContent = cleanedContent; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public List<String> getResultUrls() { return resultUrls; }
    public void setResultUrls(List<String> resultUrls) { this.resultUrls = resultUrls; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getProjectUuid() { return projectUuid; }
    public void setProjectUuid(String projectUuid) { this.projectUuid = projectUuid; }

    public String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }

    public Long getNextSeq() { return nextSeq; }
    public void setNextSeq(Long nextSeq) { this.nextSeq = nextSeq; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }

    public Integer getTotalSteps() { return totalSteps; }
    public void setTotalSteps(Integer totalSteps) { this.totalSteps = totalSteps; }

    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }

    public Boolean getFinalResultReady() { return finalResultReady; }
    public void setFinalResultReady(Boolean finalResultReady) { this.finalResultReady = finalResultReady; }

    public Integer getAllVideoCount() { return allVideoCount; }
    public void setAllVideoCount(Integer allVideoCount) { this.allVideoCount = allVideoCount; }

    public List<Map<String, String>> getComplianceWarnings() { return complianceWarnings; }
    public void setComplianceWarnings(List<Map<String, String>> complianceWarnings) { this.complianceWarnings = complianceWarnings; }

    public List<Map<String, String>> getCitations() { return citations; }
    public void setCitations(List<Map<String, String>> citations) { this.citations = citations; }
}
