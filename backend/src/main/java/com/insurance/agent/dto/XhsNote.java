package com.insurance.agent.dto;

import java.util.List;

public class XhsNote {
    private String noteId;
    private String url;
    private String type;
    private String title;
    private String content;
    private List<String> tags;
    private List<String> imageUrls;
    private String videoUrl;
    private String publishTime;
    private String authorName;
    private String authorId;
    private String likedCount;
    private String collectedCount;
    private String commentCount;
    private String shareCount;

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getPublishTime() { return publishTime; }
    public void setPublishTime(String publishTime) { this.publishTime = publishTime; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getLikedCount() { return likedCount; }
    public void setLikedCount(String likedCount) { this.likedCount = likedCount; }

    public String getCollectedCount() { return collectedCount; }
    public void setCollectedCount(String collectedCount) { this.collectedCount = collectedCount; }

    public String getCommentCount() { return commentCount; }
    public void setCommentCount(String commentCount) { this.commentCount = commentCount; }

    public String getShareCount() { return shareCount; }
    public void setShareCount(String shareCount) { this.shareCount = shareCount; }
}
