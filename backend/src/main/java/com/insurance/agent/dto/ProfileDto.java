package com.insurance.agent.dto;

import java.util.List;

public class ProfileDto {
    private Long id;
    private String name;
    private String phone;
    private String region;
    private String years;
    private String avatarUrl;
    private List<String> primaryProducts;
    private List<String> targetAudiences;
    private String style;
    private String bio;

    // ---- 平台绑定（读取时附带） ----
    private List<PlatformBinding> platforms;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getYears() { return years; }
    public void setYears(String years) { this.years = years; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public List<String> getPrimaryProducts() { return primaryProducts; }
    public void setPrimaryProducts(List<String> primaryProducts) { this.primaryProducts = primaryProducts; }

    public List<String> getTargetAudiences() { return targetAudiences; }
    public void setTargetAudiences(List<String> targetAudiences) { this.targetAudiences = targetAudiences; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public List<PlatformBinding> getPlatforms() { return platforms; }
    public void setPlatforms(List<PlatformBinding> platforms) { this.platforms = platforms; }

    public static class PlatformBinding {
        private String platform;
        private String accountName;
        private String accountId;

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public String getAccountName() { return accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
    }
}
