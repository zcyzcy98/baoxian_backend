package com.insurance.agent.service;

import com.insurance.agent.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 把多个数据源融合成一个"选题候选列表".
 *
 * 数据源包括:
 *   1) 飞书多维表格 (BitableTopicReader)
 *   2) 今日热榜 API   (TopHubDataService)
 *
 * 每一条候选会被标注:
 *   - sourceCategory: 来源分类 (HOT_TEMPLATE / KNOWLEDGE_BASE / NEWS_HOTSPOT)
 *   - reason: 选题原因
 *   - insuranceTypes: 险种标签 (用于前端筛选)
 *   - demographics: 人群标签 (用于前端筛选)
 */
@Service
public class TopicGenerationService {
    private static final Logger log = LoggerFactory.getLogger(TopicGenerationService.class);

    private final BitableTopicReader bitableReader;
    private final TopHubDataService topHubDataService;
    private final TopicAiFilterService aiFilter;

    public TopicGenerationService(BitableTopicReader bitableReader,
                                  TopHubDataService topHubDataService,
                                  TopicAiFilterService aiFilter) {
        this.bitableReader = bitableReader;
        this.topHubDataService = topHubDataService;
        this.aiFilter = aiFilter;
    }

    private static final Set<String> TOPHUB_CATEGORIES = Set.of("hot", "hotspot", "热点", "热点追踪");

    // 来源分类常量
    static final String CATEGORY_SYSTEM_RECOMMEND = "SYSTEM_RECOMMEND";
    static final String CATEGORY_HOT_TEMPLATE = "HOT_TEMPLATE";
    static final String CATEGORY_KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    static final String CATEGORY_NEWS_HOTSPOT = "NEWS_HOTSPOT";
    static final String CATEGORY_USER_WRITE = "USER_WRITE";

    // 险种关键词 → 险种标签
    private static final Map<String, String> INSURANCE_KEYWORD_MAP = new LinkedHashMap<>();
    static {
        INSURANCE_KEYWORD_MAP.put("重疾", "重疾险");
        INSURANCE_KEYWORD_MAP.put("重疾险", "重疾险");
        INSURANCE_KEYWORD_MAP.put("医疗", "医疗险");
        INSURANCE_KEYWORD_MAP.put("医疗险", "医疗险");
        INSURANCE_KEYWORD_MAP.put("意外", "意外险");
        INSURANCE_KEYWORD_MAP.put("意外险", "意外险");
        INSURANCE_KEYWORD_MAP.put("寿险", "寿险");
        INSURANCE_KEYWORD_MAP.put("养老", "养老险");
        INSURANCE_KEYWORD_MAP.put("年金", "养老险");
        INSURANCE_KEYWORD_MAP.put("车险", "车险");
        INSURANCE_KEYWORD_MAP.put("财产", "财产险");
        INSURANCE_KEYWORD_MAP.put("理财", "理财险");
        INSURANCE_KEYWORD_MAP.put("分红", "理财险");
        INSURANCE_KEYWORD_MAP.put("医保", "医疗险");
        INSURANCE_KEYWORD_MAP.put("惠民保", "医疗险");
    }

    // 人群关键词 → 人群标签
    private static final Map<String, String> DEMOGRAPHIC_KEYWORD_MAP = new LinkedHashMap<>();
    static {
        DEMOGRAPHIC_KEYWORD_MAP.put("年轻", "年轻人");
        DEMOGRAPHIC_KEYWORD_MAP.put("中年", "中年人");
        DEMOGRAPHIC_KEYWORD_MAP.put("老年", "老年人");
        DEMOGRAPHIC_KEYWORD_MAP.put("父母", "父母");
        DEMOGRAPHIC_KEYWORD_MAP.put("家长", "父母");
        DEMOGRAPHIC_KEYWORD_MAP.put("宝妈", "宝爸宝妈");
        DEMOGRAPHIC_KEYWORD_MAP.put("宝爸", "宝爸宝妈");
        DEMOGRAPHIC_KEYWORD_MAP.put("孩子", "孩子");
        DEMOGRAPHIC_KEYWORD_MAP.put("少儿", "孩子");
        DEMOGRAPHIC_KEYWORD_MAP.put("儿童", "孩子");
        DEMOGRAPHIC_KEYWORD_MAP.put("宝宝", "孩子");
        DEMOGRAPHIC_KEYWORD_MAP.put("上班族", "上班族");
        DEMOGRAPHIC_KEYWORD_MAP.put("白领", "上班族");
        DEMOGRAPHIC_KEYWORD_MAP.put("打工", "上班族");
        DEMOGRAPHIC_KEYWORD_MAP.put("自由职业", "自由职业者");
        DEMOGRAPHIC_KEYWORD_MAP.put("创业", "创业者");
        DEMOGRAPHIC_KEYWORD_MAP.put("学生", "学生");
        DEMOGRAPHIC_KEYWORD_MAP.put("家庭主妇", "家庭主妇");
    }

    // 社会热点 → 保险切入角度映射
    private static final Map<String, String> HOTSPOT_ANGLE_MAP = new LinkedHashMap<>();
    static {
        HOTSPOT_ANGLE_MAP.put("熬夜", "长期熬夜增加重疾风险，重疾险配置不容忽视");
        HOTSPOT_ANGLE_MAP.put("加班", "996工作制下，如何用保险抵御职业健康风险");
        HOTSPOT_ANGLE_MAP.put("猝死", "猝死频发，年轻人更需要定期寿险和意外险");
        HOTSPOT_ANGLE_MAP.put("癌症", "癌症年轻化趋势，重疾险和医疗险的搭配方案");
        HOTSPOT_ANGLE_MAP.put("体检", "体检异常指标增多，买保险前要注意什么");
        HOTSPOT_ANGLE_MAP.put("三高", "三高人群还能买什么保险？这些产品不限健康告知");
        HOTSPOT_ANGLE_MAP.put("失业", "经济下行期，失业保障和保险规划策略");
        HOTSPOT_ANGLE_MAP.put("裁员", "裁员潮下，社保断缴怎么办？商业保险来兜底");
        HOTSPOT_ANGLE_MAP.put("降薪", "收入减少时，保险配置的优先级调整建议");
        HOTSPOT_ANGLE_MAP.put("地震", "自然灾害频发，家财险和意外险的必要性");
        HOTSPOT_ANGLE_MAP.put("洪水", "极端天气增多，家庭财产险和车险理赔指南");
        HOTSPOT_ANGLE_MAP.put("台风", "台风季来临，你的车险和家财险买对了吗");
        HOTSPOT_ANGLE_MAP.put("火灾", "火灾事故警示，家庭财产险和燃气险配置建议");
        HOTSPOT_ANGLE_MAP.put("交通事故", "交通事故理赔全流程，车险这样买才够用");
        HOTSPOT_ANGLE_MAP.put("养老", "养老焦虑催生保险需求，商业养老保险怎么选");
        HOTSPOT_ANGLE_MAP.put("老龄化", "老龄化社会下的养老险和护理险配置思路");
        HOTSPOT_ANGLE_MAP.put("延迟退休", "延迟退休落地，商业养老保险成为刚需");
        HOTSPOT_ANGLE_MAP.put("生育", "生育率走低，母婴险和少儿险市场分析");
        HOTSPOT_ANGLE_MAP.put("双减", "教育政策变化，教育金保险还值得买吗");
        HOTSPOT_ANGLE_MAP.put("房价", "房价波动下的家庭资产配置，保险的压舱石作用");
        HOTSPOT_ANGLE_MAP.put("医疗", "医疗改革动态，商业医疗险的补充价值凸显");
        HOTSPOT_ANGLE_MAP.put("DRG", "DRG付费改革后，中高端医疗险需求上升");
        HOTSPOT_ANGLE_MAP.put("医保", "医保改革要点解读，为什么还需要商业保险");
        HOTSPOT_ANGLE_MAP.put("惠民保", "惠民保升级迭代，和百万医疗险怎么选");
        HOTSPOT_ANGLE_MAP.put("健康", "全民健康意识提升，健康管理和保险组合策略");
        HOTSPOT_ANGLE_MAP.put("运动", "运动损伤频发，运动意外险配置指南");
        HOTSPOT_ANGLE_MAP.put("跑步", "跑步猝死案例警示，运动爱好者的保险方案");
        HOTSPOT_ANGLE_MAP.put("旅游", "旅游出行高峰，旅行险和意外险购买攻略");
        HOTSPOT_ANGLE_MAP.put("假期", "长假出行安全指南，这几份保险必不可少");
        HOTSPOT_ANGLE_MAP.put("食品安全", "食品安全问题频出，食品安全责任险和健康险");
        HOTSPOT_ANGLE_MAP.put("AI", "AI替代人工焦虑，自由职业者和转型期的保险规划");
        HOTSPOT_ANGLE_MAP.put("chatgpt", "AI时代职业变革，收入不稳定时的保险策略");
        HOTSPOT_ANGLE_MAP.put("35岁", "35岁危机，职场中年人的保险配置清单");
        HOTSPOT_ANGLE_MAP.put("中年", "中年人上有老下有小，家庭支柱的保险配置方案");
        HOTSPOT_ANGLE_MAP.put("婚姻", "婚姻财产新规，婚前婚后保险怎么买");
        HOTSPOT_ANGLE_MAP.put("离婚", "离婚财产分割中的保险权益处理指南");
        HOTSPOT_ANGLE_MAP.put("继承", "遗产继承新规，保险在财富传承中的作用");
        HOTSPOT_ANGLE_MAP.put("税", "税优健康险和税延养老险，省钱又保障");
        HOTSPOT_ANGLE_MAP.put("CPI", "通胀背景下的保险配置，保额够用吗");
        HOTSPOT_ANGLE_MAP.put("利率", "利率下行期，锁定长期收益的年金险优势");
    }

    /**
     * 生成"选题广场"今日推荐.
     *
     * @param profile 用户画像 (可为 null)
     * @param categories 限制只返回某些来源
     * @param limit 返回最多多少条
     * @param insuranceTypesFilter 险种筛选 (可为 null/空 = 不限)
     * @param demographicsFilter 人群筛选 (可为 null/空 = 不限)
     */
    public List<TopicCandidate> generateDaily(UserProfile profile, Set<String> categories, int limit,
                                              List<String> insuranceTypesFilter, List<String> demographicsFilter) {
        boolean onlyHot = categories != null && categories.stream().anyMatch(TOPHUB_CATEGORIES::contains);
        boolean onlyBitable = categories != null && !onlyHot;

        List<TopicCandidate> bag = new ArrayList<>();

        if (!onlyHot) {
            bag.addAll(bitableReader.readAll(profile));
        }

        if (!onlyBitable && topHubDataService.isConfigured()) {
            try {
                // 始终从 TopHub 拉满 100 条，经过噪音过滤和 AI 筛选后候选会减少，
                // 这样无论用户选 10/20/30 都能从足够大的池里挑到指定数量
                List<TopicCandidate> hot = topHubDataService.fetchHotTopics(100);
                // AI 增强：过滤噪音 + 补全 whyThisTopic / insuranceTypes / recommendedPlatforms
                List<TopicCandidate> aiEnriched = aiFilter.enrichWithAi(hot);
                bag.addAll(aiEnriched);
            } catch (Exception e) {
                log.warn("fetch hot topics failed, will serve bitable-only: {}", e.getMessage());
            }
        }

        // 为每个候选标注来源分类、选题原因、险种/人群标签
        for (TopicCandidate c : bag) {
            enrichCandidate(c);
        }

        // 按险种筛选
        if (insuranceTypesFilter != null && !insuranceTypesFilter.isEmpty()) {
            bag.removeIf(c -> {
                List<String> types = c.getInsuranceTypes();
                return types == null || types.isEmpty() || Collections.disjoint(types, insuranceTypesFilter);
            });
        }

        // 按人群筛选
        if (demographicsFilter != null && !demographicsFilter.isEmpty()) {
            bag.removeIf(c -> {
                List<String> demos = c.getDemographics();
                return demos == null || demos.isEmpty() || Collections.disjoint(demos, demographicsFilter);
            });
        }

        bag.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        Set<String> seen = new HashSet<>();
        List<TopicCandidate> out = new ArrayList<>();
        for (TopicCandidate c : bag) {
            String key = c.getTitle() == null ? "" : c.getTitle().trim();
            if (seen.add(key) && !key.isEmpty()) {
                out.add(c);
                if (out.size() >= Math.max(1, limit)) break;
            }
        }
        return out;
    }

    /** 兼容旧调用 (无筛选参数). */
    public List<TopicCandidate> generateDaily(UserProfile profile, Set<String> categories, int limit) {
        return generateDaily(profile, categories, limit, null, null);
    }

    /** 为单个候选标注来源分类、选题原因、险种/人群标签. */
    private void enrichCandidate(TopicCandidate c) {
        if (c.getSource() == null) return;
        String title = c.getTitle() == null ? "" : c.getTitle();
        String angle = c.getAngle() == null ? "" : c.getAngle();
        String sourceLabel = c.getSourceLabel() == null ? "" : c.getSourceLabel();
        String sourceUrl = c.getSourceUrl() == null ? "" : c.getSourceUrl();

        // 提取险种标签
        List<String> insuranceTypes = extractInsuranceTypes(title + " " + angle + " " + String.join(" ", c.getTags()));
        if (!insuranceTypes.isEmpty()) {
            c.setInsuranceTypes(insuranceTypes);
        }

        // 提取人群标签
        List<String> demographics = extractDemographics(title + " " + angle + " " + String.join(" ", c.getTags()));
        if (!demographics.isEmpty()) {
            c.setDemographics(demographics);
        }

        switch (c.getSource()) {
            case TEMPLATE:
                c.setSourceCategory(CATEGORY_HOT_TEMPLATE);
                if (c.getReason() == null) {
                    c.setReason("基于热门模板推荐，该格式经过市场验证，爆款概率高");
                }
                break;

            case HOTSPOT:
                c.setSourceCategory(CATEGORY_HOT_TEMPLATE);
                if (c.getReason() == null) {
                    c.setReason("参考同类爆款笔记，切入角度和内容结构已获市场验证");
                }
                break;

            case TOPHUB:
                c.setSourceCategory(CATEGORY_NEWS_HOTSPOT);
                if (c.getReason() == null) {
                    boolean isInsuranceRelated = c.getTags().contains("保险相关");
                    if (isInsuranceRelated) {
                        c.setReason("保险热点话题，热度高，容易引起目标用户关注");
                    } else {
                        String socialReason = findSocialHotspotAngle(title, angle);
                        c.setReason(socialReason);
                        if (c.getAngle() == null || c.getAngle().isBlank()) {
                            c.setAngle(socialReason);
                        }
                    }
                }
                break;

            case CALENDAR:
                c.setSourceCategory(CATEGORY_KNOWLEDGE_BASE);
                if (c.getReason() == null) {
                    c.setReason("时令节点选题，借势营销效果更佳");
                }
                break;

            case USER:
                c.setSourceCategory(CATEGORY_USER_WRITE);
                if (c.getReason() == null) {
                    c.setReason("用户自定义选题");
                }
                break;
        }

        // 根据 tags 推断的险种/人群如果还没有, 也设置
        if (c.getInsuranceTypes() == null || c.getInsuranceTypes().isEmpty()) {
            List<String> inferred = inferFromTags(c.getTags(), INSURANCE_KEYWORD_MAP);
            if (!inferred.isEmpty()) c.setInsuranceTypes(inferred);
        }
        if (c.getDemographics() == null || c.getDemographics().isEmpty()) {
            List<String> inferred = inferFromTags(c.getTags(), DEMOGRAPHIC_KEYWORD_MAP);
            if (!inferred.isEmpty()) c.setDemographics(inferred);
        }
    }

    /** 从标题/描述中提取险种标签 */
    private List<String> extractInsuranceTypes(String text) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : INSURANCE_KEYWORD_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                if (!result.contains(entry.getValue())) {
                    result.add(entry.getValue());
                }
            }
        }
        return result;
    }

    /** 从标题/描述中提取人群标签 */
    private List<String> extractDemographics(String text) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : DEMOGRAPHIC_KEYWORD_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                if (!result.contains(entry.getValue())) {
                    result.add(entry.getValue());
                }
            }
        }
        return result;
    }

    /** 从 tags 列表中匹配关键词 */
    private List<String> inferFromTags(List<String> tags, Map<String, String> keywordMap) {
        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) continue;
            for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
                if (tag.contains(entry.getKey())) {
                    if (!result.contains(entry.getValue())) {
                        result.add(entry.getValue());
                    }
                }
            }
        }
        return result;
    }

    /** 为社会热点找保险切入角度 */
    private String findSocialHotspotAngle(String title, String angle) {
        String combined = (title + " " + angle).toLowerCase();
        for (Map.Entry<String, String> entry : HOTSPOT_ANGLE_MAP.entrySet()) {
            if (combined.contains(entry.getKey().toLowerCase())) {
                return "社会热点切入：" + entry.getValue();
            }
        }
        return "社会热点话题，可从保险角度切入，引发用户共鸣";
    }

    /**
     * 从已缓存的候选列表中按用户画像个性化排序。
     * 用于 /daily 接口的热点库缓存路径，无需重复调用 TopHubData + AI。
     *
     * @param cachedTopics      HotTopicCollector 缓存的热点列表
     * @param profile            用户画像（可为 null）
     * @param insuranceFilter    险种筛选（可为 null/空 = 不限）
     * @param demographicFilter  人群筛选（可为 null/空 = 不限）
     * @param limit              返回条数上限
     */
    public List<TopicCandidate> filterByProfile(List<TopicCandidate> cachedTopics,
                                                UserProfile profile,
                                                List<String> insuranceFilter,
                                                List<String> demographicFilter,
                                                int limit) {
        if (cachedTopics == null || cachedTopics.isEmpty()) {
            return List.of();
        }

        // 为每个候选计算匹配分
        List<ScoredCandidate> scored = new ArrayList<>();
        for (TopicCandidate c : cachedTopics) {
            enrichCandidate(c);
            int matchScore = computeMatchScore(c, profile);
            scored.add(new ScoredCandidate(c, matchScore));
        }

        // 按险种筛选
        if (insuranceFilter != null && !insuranceFilter.isEmpty()) {
            scored.removeIf(s -> {
                List<String> types = s.candidate.getInsuranceTypes();
                return types == null || types.isEmpty() || Collections.disjoint(types, insuranceFilter);
            });
        }

        // 按人群筛选
        if (demographicFilter != null && !demographicFilter.isEmpty()) {
            scored.removeIf(s -> {
                List<String> demos = s.candidate.getDemographics();
                return demos == null || demos.isEmpty() || Collections.disjoint(demos, demographicFilter);
            });
        }

        // 按综合得分降序：原始热度 + 匹配分 - 时间衰减
        scored.sort((a, b) -> Integer.compare(b.totalScore(), a.totalScore()));

        Set<String> seen = new HashSet<>();
        List<TopicCandidate> out = new ArrayList<>();
        for (ScoredCandidate s : scored) {
            String key = s.candidate.getTitle() == null ? "" : s.candidate.getTitle().trim();
            if (seen.add(key) && !key.isEmpty()) {
                TopicCandidate c = s.candidate;
                c.setScore(Math.min(100, s.totalScore()));
                out.add(c);
                if (out.size() >= Math.max(1, limit)) break;
            }
        }

        // 全局归一化：所有候选一起映射到 10-100，保证展示区分度
        if (out.size() >= 2) {
            int minS = out.stream().mapToInt(TopicCandidate::getScore).min().orElse(0);
            int maxS = out.stream().mapToInt(TopicCandidate::getScore).max().orElse(1);
            int range = maxS - minS;
            if (range == 0) {
                for (int i = 0; i < out.size(); i++) {
                    double pct = (double) i / (out.size() - 1);
                    out.get(i).setScore(10 + (int) Math.round(pct * 90));
                }
            } else {
                for (TopicCandidate c : out) {
                    double normalized = (double) (c.getScore() - minS) / range;
                    c.setScore(10 + (int) Math.round(normalized * 90));
                }
            }
            // 归一化后重新排序
            out.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        }
        return out;
    }

    /** 计算候选与用户画像的匹配分 */
    private int computeMatchScore(TopicCandidate c, UserProfile profile) {
        if (profile == null || profile.isEmpty()) return 0;

        int score = 0;

        // 1. 主营险种匹配：每匹配一项 +3
        List<String> products = profile.getPrimaryProducts();
        List<String> insTypes = c.getInsuranceTypes();
        if (products != null && insTypes != null) {
            for (String p : products) {
                for (String t : insTypes) {
                    if (t.contains(p) || p.contains(t)) {
                        score += 3;
                        break;
                    }
                }
            }
        }

        // 2. 目标客群匹配：每匹配一项 +3
        List<String> audiences = profile.getTargetAudiences();
        List<String> demos = c.getDemographics();
        if (audiences != null && demos != null) {
            for (String a : audiences) {
                for (String d : demos) {
                    if (d.contains(a) || a.contains(d)) {
                        score += 3;
                        break;
                    }
                }
            }
        }

        // 3. 风格偏好匹配 +2
        String style = profile.getStyle();
        if (style != null && !style.isBlank()) {
            if (c.getTags() != null) {
                for (String tag : c.getTags()) {
                    if (tag.contains(style) || style.contains(tag)) {
                        score += 2;
                        break;
                    }
                }
            }
            String reason = c.getReason();
            if (reason != null && reason.contains(style)) {
                score += 2;
            }
        }

        return score;
    }

    /** 辅助记录，携带候选和匹配分 */
    private static class ScoredCandidate {
        final TopicCandidate candidate;
        final int matchScore;

        ScoredCandidate(TopicCandidate candidate, int matchScore) {
            this.candidate = candidate;
            this.matchScore = matchScore;
        }

        int totalScore() {
            int base = candidate.getScore() + matchScore;
            return applyTimeDecay(base);
        }

        /** 时间衰减：仅 TopHub 热点超过12小时后每小时-0.1，最多-1。飞书数据不做衰减。 */
        private int applyTimeDecay(int base) {
            // 飞书数据不做热点衰减
            if (candidate.getSource() != TopicCandidate.Source.TOPHUB) return base;
            java.time.OffsetDateTime createdAt = candidate.getCreatedAt();
            if (createdAt == null) return base;
            long hoursAgo = java.time.Duration.between(createdAt, java.time.OffsetDateTime.now()).toHours();
            if (hoursAgo <= 12) return base;
            double penalty = Math.min(10.0, (hoursAgo - 12) * 1.0);
            return Math.max(0, base - (int) Math.round(penalty));
        }
    }

    /** 单条用户输入选题: 直接包成 TopicCandidate, 给最高优先级. */
    public TopicCandidate fromUserInput(String title, UserProfile profile) {
        TopicCandidate c = new TopicCandidate();
        c.setId(uid("user", title));
        c.setSource(TopicCandidate.Source.USER);
        c.setTitle(title);
        c.setReason("用户自定义选题");
        c.setAngle("用户输入选题");
        c.setSourceLabel("我的输入");
        c.setSourceCategory(CATEGORY_USER_WRITE);
        c.setScore(100);
        c.setSuggestedAgent("xhs-title");
        if (profile != null && profile.getPrimaryProducts() != null) {
            c.getTags().addAll(profile.getPrimaryProducts());
        }
        enrichCandidate(c);
        return c;
    }

    private static String uid(String prefix, String source) {
        return prefix + "-" + Integer.toHexString((source == null ? "" : source).hashCode());
    }
}
