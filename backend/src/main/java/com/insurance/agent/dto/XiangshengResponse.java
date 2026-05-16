package com.insurance.agent.dto;

public class XiangshengResponse {
    private String styleName;
    private String dialogue;
    private String storyboard;
    private String groupPrompts;
    private String model;

    public XiangshengResponse() {}

    public XiangshengResponse(String styleName, String dialogue, String storyboard, String groupPrompts, String model) {
        this.styleName = styleName;
        this.dialogue = dialogue;
        this.storyboard = storyboard;
        this.groupPrompts = groupPrompts;
        this.model = model;
    }

    public String getStyleName() { return styleName; }
    public void setStyleName(String styleName) { this.styleName = styleName; }

    public String getDialogue() { return dialogue; }
    public void setDialogue(String dialogue) { this.dialogue = dialogue; }

    public String getStoryboard() { return storyboard; }
    public void setStoryboard(String storyboard) { this.storyboard = storyboard; }

    public String getGroupPrompts() { return groupPrompts; }
    public void setGroupPrompts(String groupPrompts) { this.groupPrompts = groupPrompts; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
