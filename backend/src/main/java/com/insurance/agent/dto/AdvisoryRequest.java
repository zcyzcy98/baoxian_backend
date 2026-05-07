package com.insurance.agent.dto;

public class AdvisoryRequest {
    private String name;
    private String summary;
    private String customerInfo;
    private String question;
    private String channel;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCustomerInfo() { return customerInfo; }
    public void setCustomerInfo(String customerInfo) { this.customerInfo = customerInfo; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}
