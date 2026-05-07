package com.insurance.agent.dto;

import java.util.List;

public class AgentRequest {
    private String topic;
    private String question;
    private String content;
    private String style;
    private String duration;
    private String script;
    private String videoUrl;
    private String cookie;
    private String outputFormat;
    private String targetMode;
    private String characterImageUrl;
    private String backgroundImageUrl;
    private String templateId;
    private String model;
    private String imageProvider;
    private List<String> videoUrls;
    private String sessionId;
    private Long afterSeq;
    private Boolean finalOnly;
    private Integer wordCount;
    private Integer imageCount;
    private String imageRatio;
    private Boolean needCover;
    private Boolean needImages;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getCookie() { return cookie; }
    public void setCookie(String cookie) { this.cookie = cookie; }

    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public String getTargetMode() { return targetMode; }
    public void setTargetMode(String targetMode) { this.targetMode = targetMode; }

    public String getCharacterImageUrl() { return characterImageUrl; }
    public void setCharacterImageUrl(String characterImageUrl) { this.characterImageUrl = characterImageUrl; }

    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getImageProvider() { return imageProvider; }
    public void setImageProvider(String imageProvider) { this.imageProvider = imageProvider; }

    public List<String> getVideoUrls() { return videoUrls; }
    public void setVideoUrls(List<String> videoUrls) { this.videoUrls = videoUrls; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getAfterSeq() { return afterSeq; }
    public void setAfterSeq(Long afterSeq) { this.afterSeq = afterSeq; }

    public Boolean getFinalOnly() { return finalOnly; }
    public void setFinalOnly(Boolean finalOnly) { this.finalOnly = finalOnly; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }

    public String getImageRatio() { return imageRatio; }
    public void setImageRatio(String imageRatio) { this.imageRatio = imageRatio; }

    public Boolean isNeedCover() { return needCover != null && needCover; }
    public void setNeedCover(Boolean needCover) { this.needCover = needCover; }

    public Boolean isNeedImages() { return needImages != null && needImages; }
    public void setNeedImages(Boolean needImages) { this.needImages = needImages; }
}
