package com.insurance.agent.dto;

public class XiangshengRequest {
    private String topic;
    private String model;
    private Integer duration;    // 目标视频时长（秒）
    private String dialogue;
    private String storyboard;

    // 多维度参数（替代原 styleIndex）
    private String hookType;       // 钩子类型
    private String structure;      // 剧本结构
    private String emotionArc;     // 情绪弧线
    private String audience;       // 目标受众
    private String topicDirection; // 话题方向
    private String toneStyle;      // 语气风格（可选）

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getDialogue() { return dialogue; }
    public void setDialogue(String dialogue) { this.dialogue = dialogue; }

    public String getStoryboard() { return storyboard; }
    public void setStoryboard(String storyboard) { this.storyboard = storyboard; }

    public String getHookType() { return hookType; }
    public void setHookType(String hookType) { this.hookType = hookType; }

    public String getStructure() { return structure; }
    public void setStructure(String structure) { this.structure = structure; }

    public String getEmotionArc() { return emotionArc; }
    public void setEmotionArc(String emotionArc) { this.emotionArc = emotionArc; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getTopicDirection() { return topicDirection; }
    public void setTopicDirection(String topicDirection) { this.topicDirection = topicDirection; }

    public String getToneStyle() { return toneStyle; }
    public void setToneStyle(String toneStyle) { this.toneStyle = toneStyle; }
}
