package com.insurance.agent.dto;

public class WechatDraftRequest {
    private String title;
    private String content;
    private String author;
    private String digest;
    private String appId;
    private String appSecret;
    private String md2wechatApiKey;
    private String coverImagePath;
    private String coverMediaId;
    private String theme;
    private boolean publish;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getDigest() { return digest; }
    public void setDigest(String digest) { this.digest = digest; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public String getMd2wechatApiKey() { return md2wechatApiKey; }
    public void setMd2wechatApiKey(String md2wechatApiKey) { this.md2wechatApiKey = md2wechatApiKey; }

    public String getCoverImagePath() { return coverImagePath; }
    public void setCoverImagePath(String coverImagePath) { this.coverImagePath = coverImagePath; }

    public String getCoverMediaId() { return coverMediaId; }
    public void setCoverMediaId(String coverMediaId) { this.coverMediaId = coverMediaId; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public boolean isPublish() { return publish; }
    public void setPublish(boolean publish) { this.publish = publish; }
}
