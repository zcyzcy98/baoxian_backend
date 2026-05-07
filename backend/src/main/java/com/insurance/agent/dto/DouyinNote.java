package com.insurance.agent.dto;

import java.util.List;

public class DouyinNote {
    private String awemeId;
    private String workUrl;
    private String workType;
    private String title;
    private List<String> topics;
    private String publishTime;
    private String nickname;
    private String userId;
    private String userDesc;
    private String likeCount;
    private String commentCount;
    private String collectCount;
    private String shareCount;
    private String followerCount;
    private String videoDownloadUrl;

    public String getAwemeId() { return awemeId; }
    public void setAwemeId(String awemeId) { this.awemeId = awemeId; }

    public String getWorkUrl() { return workUrl; }
    public void setWorkUrl(String workUrl) { this.workUrl = workUrl; }

    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }

    public String getPublishTime() { return publishTime; }
    public void setPublishTime(String publishTime) { this.publishTime = publishTime; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserDesc() { return userDesc; }
    public void setUserDesc(String userDesc) { this.userDesc = userDesc; }

    public String getLikeCount() { return likeCount; }
    public void setLikeCount(String likeCount) { this.likeCount = likeCount; }

    public String getCommentCount() { return commentCount; }
    public void setCommentCount(String commentCount) { this.commentCount = commentCount; }

    public String getCollectCount() { return collectCount; }
    public void setCollectCount(String collectCount) { this.collectCount = collectCount; }

    public String getShareCount() { return shareCount; }
    public void setShareCount(String shareCount) { this.shareCount = shareCount; }

    public String getFollowerCount() { return followerCount; }
    public void setFollowerCount(String followerCount) { this.followerCount = followerCount; }

    public String getVideoDownloadUrl() { return videoDownloadUrl; }
    public void setVideoDownloadUrl(String videoDownloadUrl) { this.videoDownloadUrl = videoDownloadUrl; }
}
