package com.insurance.agent.dto;

public class XiangshengRequest {
    private String topic;
    private String model;
    private Integer styleIndex;
    private String dialogue;
    private String storyboard;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Integer getStyleIndex() { return styleIndex; }
    public void setStyleIndex(Integer styleIndex) { this.styleIndex = styleIndex; }

    public String getDialogue() { return dialogue; }
    public void setDialogue(String dialogue) { this.dialogue = dialogue; }

    public String getStoryboard() { return storyboard; }
    public void setStoryboard(String storyboard) { this.storyboard = storyboard; }
}
