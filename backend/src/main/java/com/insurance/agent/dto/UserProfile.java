package com.insurance.agent.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户画像。前端在"选题广场"的设置弹窗里填, 每次请求 /api/topics/daily 时带上来。
 * 用于个性化排序和打分。
 *
 * 当前不在后端持久化(由前端 localStorage 保存); 后续接 DB 时再说。
 */
public class UserProfile {
    private List<String> primaryProducts;  // 主营险种, 例如 ["重疾", "医疗"]
    private List<String> targetAudiences;  // 目标客群, 例如 ["宝妈", "上班族"]
    private String ageRange;               // "30-45"
    private String region;                 // "上海"
    private String style;                  // 偏好风格: "干货" / "治愈" / ...

    public UserProfile() {
        this.primaryProducts = new ArrayList<>();
        this.targetAudiences = new ArrayList<>();
    }

    public List<String> getPrimaryProducts() { return primaryProducts; }
    public void setPrimaryProducts(List<String> primaryProducts) { this.primaryProducts = primaryProducts; }
    public List<String> getTargetAudiences() { return targetAudiences; }
    public void setTargetAudiences(List<String> targetAudiences) { this.targetAudiences = targetAudiences; }
    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public boolean isEmpty() {
        return (primaryProducts == null || primaryProducts.isEmpty())
                && (targetAudiences == null || targetAudiences.isEmpty())
                && (ageRange == null || ageRange.isBlank())
                && (region == null || region.isBlank())
                && (style == null || style.isBlank());
    }
}
