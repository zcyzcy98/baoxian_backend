package com.insurance.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public class HotTopic {

    private Long id;
    private String title;
    private String titleHash;
    private String source;
    private String sourceUrl;
    private String sourceSite;
    private int heatScore;
    private int aiScore;
    private String insuranceTypesRaw;
    private String demographicsRaw;
    private String platformsRaw;
    private String whyThisTopic;
    private String sourceCategory;
    private LocalDate batchDate;
    private OffsetDateTime createdAt;

    private static final ObjectMapper OM = new ObjectMapper();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleHash() { return titleHash; }
    public void setTitleHash(String titleHash) { this.titleHash = titleHash; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceSite() { return sourceSite; }
    public void setSourceSite(String sourceSite) { this.sourceSite = sourceSite; }

    public int getHeatScore() { return heatScore; }
    public void setHeatScore(int heatScore) { this.heatScore = heatScore; }

    public int getAiScore() { return aiScore; }
    public void setAiScore(int aiScore) { this.aiScore = aiScore; }

    public String getInsuranceTypesRaw() { return insuranceTypesRaw; }
    public void setInsuranceTypesRaw(String insuranceTypesRaw) { this.insuranceTypesRaw = insuranceTypesRaw; }

    public String getDemographicsRaw() { return demographicsRaw; }
    public void setDemographicsRaw(String demographicsRaw) { this.demographicsRaw = demographicsRaw; }

    public String getPlatformsRaw() { return platformsRaw; }
    public void setPlatformsRaw(String platformsRaw) { this.platformsRaw = platformsRaw; }

    public String getWhyThisTopic() { return whyThisTopic; }
    public void setWhyThisTopic(String whyThisTopic) { this.whyThisTopic = whyThisTopic; }

    public String getSourceCategory() { return sourceCategory; }
    public void setSourceCategory(String sourceCategory) { this.sourceCategory = sourceCategory; }

    public LocalDate getBatchDate() { return batchDate; }
    public void setBatchDate(LocalDate batchDate) { this.batchDate = batchDate; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @JsonIgnore
    public List<String> getInsuranceTypes() {
        return parseJsonArray(insuranceTypesRaw);
    }

    @JsonIgnore
    public List<String> getDemographics() {
        return parseJsonArray(demographicsRaw);
    }

    @JsonIgnore
    public List<String> getPlatforms() {
        return parseJsonArray(platformsRaw);
    }

    public void setInsuranceTypes(List<String> list) {
        this.insuranceTypesRaw = toJsonArray(list);
    }

    public void setDemographics(List<String> list) {
        this.demographicsRaw = toJsonArray(list);
    }

    public void setPlatforms(List<String> list) {
        this.platformsRaw = toJsonArray(list);
    }

    public int getTotalScore() {
        return heatScore + aiScore;
    }

    private static List<String> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return OM.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return OM.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
