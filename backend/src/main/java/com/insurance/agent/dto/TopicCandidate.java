package com.insurance.agent.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个候选选题, 一张前端"选题卡片" = 一个 TopicCandidate。
 */
public class TopicCandidate {
    public enum Source {
        TEMPLATE,   // 来自飞书模板类表
        HOTSPOT,    // 来自飞书爆款笔记类表
        TOPHUB,     // 来自今日热榜 API 实时热点
        CALENDAR,   // 保留兼容旧前端缓存, 不再由后端生成
        USER        // 保留兼容旧前端缓存, 不再由选题广场生成
    }

    private String id;              // 内部 id (uuid 或 source+hash)
    private Source source;
    private String title;           // 选题标题
    private String reason;          // 选题原因 / 为什么推荐这个
    private String angle;           // 切入角度 / 简短解释 (1-2 句话)
    private List<String> tags;      // 险种 / 客群 / 类型 标签
    private List<String> insuranceTypes; // 险种标签 (用于筛选)
    private List<String> demographics;   // 人群标签 (用于筛选)
    private String sourceLabel;     // 给前端展示的飞书表来源
    private String sourceUrl;       // 原文链接 (如有)
    private String sourceCategory;  // 来源分类: SYSTEM_RECOMMEND / HOT_TEMPLATE / KNOWLEDGE_BASE / NEWS_HOTSPOT / USER_WRITE
    private int score;              // 0-100 爆款可能性
    private String suggestedAgent; // 建议跳转哪个 agent ("xhs-title" / "video-script" 等)

    public TopicCandidate() {
        this.tags = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAngle() { return angle; }
    public void setAngle(String angle) { this.angle = angle; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public List<String> getInsuranceTypes() { return insuranceTypes; }
    public void setInsuranceTypes(List<String> insuranceTypes) { this.insuranceTypes = insuranceTypes; }
    public List<String> getDemographics() { return demographics; }
    public void setDemographics(List<String> demographics) { this.demographics = demographics; }
    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getSourceCategory() { return sourceCategory; }
    public void setSourceCategory(String sourceCategory) { this.sourceCategory = sourceCategory; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getSuggestedAgent() { return suggestedAgent; }
    public void setSuggestedAgent(String suggestedAgent) { this.suggestedAgent = suggestedAgent; }
}
