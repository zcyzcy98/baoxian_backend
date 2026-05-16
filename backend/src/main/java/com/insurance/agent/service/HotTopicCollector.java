package com.insurance.agent.service;

import com.insurance.agent.dto.BitableConfig;
import com.insurance.agent.dto.TopicCandidate;
import com.insurance.agent.model.HotTopic;
import com.insurance.agent.repository.HotTopicRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 定时采集热点 + 飞书知识库，经规则预过滤 / AI 增强后存入 hot_topics 表。
 * 每天 8:00 / 18:00 采集 TopHubData，15:30 采集飞书知识库。
 * 维护统一的内存缓存供 /daily 接口快速读取。
 */
@Service
public class HotTopicCollector {

    private static final Logger log = LoggerFactory.getLogger(HotTopicCollector.class);

    private static final List<String> TOPHUB_SOURCE_HASHIDS = Arrays.asList(
            "mproPpoq6O", "rx9oz6oXbq",  // 知乎
            "WnBe01o371", "W1VdJPZoLQ",  // 微信
            "KqndgxeLl9",                // 微博
            "x9ozB4KoXb",               // 今日头条
            "K7GdaMgdQy",               // 抖音
            "Jb0vmloB1G",               // 百度
            "KMZd7VOvrO",               // 知乎日报
            "n6YoVqDeZa",               // 夸克
            "YqoXQGXvOD",               // 汽车之家
            "0MdKam4ow1",               // 第一财经
            "L4MdA5ldxD"                // 小红书
    );

    public static List<String> getSourceHashids() { return TOPHUB_SOURCE_HASHIDS; }

    private final TopHubDataService topHubDataService;
    private final TopicAiFilterService aiFilterService;
    private final HotTopicRepository repository;
    private final BitableTopicReader bitableReader;
    private final BitableConfigService configService;

    /**
     * 内存缓存，供 /daily 接口快速读取。
     * 每天 8:00 / 15:30 / 18:00 采集完成后刷新，包含 TopHub + 飞书数据。
     */
    private volatile List<TopicCandidate> hotTopicsCache = new CopyOnWriteArrayList<>();

    public HotTopicCollector(TopHubDataService topHubDataService,
                             TopicAiFilterService aiFilterService,
                             HotTopicRepository repository,
                             BitableTopicReader bitableReader,
                             BitableConfigService configService) {
        this.topHubDataService = topHubDataService;
        this.aiFilterService = aiFilterService;
        this.repository = repository;
        this.bitableReader = bitableReader;
        this.configService = configService;
    }

    /**
     * 服务启动时从 DB 恢复今日缓存。
     * 避免重启后 /daily 在当天首次定时任务跑完前一直返回空。
     */
    @PostConstruct
    public void initCacheOnStartup() {
        LocalDate today = LocalDate.now();
        List<HotTopic> todayRows = repository.findByBatchDate(today);
        if (!todayRows.isEmpty()) {
            List<TopicCandidate> mapped = new ArrayList<>();
            for (HotTopic ht : todayRows) mapped.add(toCandidate(ht));
            this.hotTopicsCache = new CopyOnWriteArrayList<>(mapped);
            log.info("[HotTopicCollector] 启动缓存恢复：今日 DB 共 {} 条", mapped.size());
        } else {
            // 今天还没采集过，尝试用昨天的数据撑着
            LocalDate yesterday = today.minusDays(1);
            List<HotTopic> yesterdayRows = repository.findByBatchDate(yesterday);
            if (!yesterdayRows.isEmpty()) {
                List<TopicCandidate> mapped = new ArrayList<>();
                for (HotTopic ht : yesterdayRows) mapped.add(toCandidate(ht));
                this.hotTopicsCache = new CopyOnWriteArrayList<>(mapped);
                log.info("[HotTopicCollector] 今日 DB 为空，用昨天 {} 条数据作为启动缓存", mapped.size());
            } else {
                log.warn("[HotTopicCollector] DB 中无今日及昨日数据，等待定时任务首次采集");
            }
        }
    }

    // ==================== TopHub 热点采集（8:00 / 18:00，北京时间） ====================

    @Scheduled(cron = "0 0 8,18 * * ?", zone = "Asia/Shanghai")
    public void collectHotTopics() {
        log.info("[HotTopicCollector] 开始定时采集热点...");
        LocalDate today = LocalDate.now();

        List<TopicCandidate> raw = topHubDataService.fetchHotTopics(100, TOPHUB_SOURCE_HASHIDS);
        if (raw.isEmpty()) {
            log.warn("[HotTopicCollector] TopHubData 返回为空，跳过本次采集");
            return;
        }
        log.info("[HotTopicCollector] TopHubData 返回 {} 条", raw.size());

        List<TopicCandidate> filtered = new ArrayList<>();
        java.util.Map<Integer, Integer> preAiScores = new java.util.HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            TopicCandidate c = raw.get(i);
            String title = c.getTitle() == null ? "" : c.getTitle();
            String angle = c.getAngle() == null ? "" : c.getAngle();
            if (TopHubDataService.computeInsuranceRelevance(title, angle) > 0) {
                filtered.add(c);
                preAiScores.put(System.identityHashCode(c), c.getScore());
            }
        }
        log.info("[HotTopicCollector] 规则预过滤后保留 {} 条", filtered.size());

        if (filtered.isEmpty()) {
            log.warn("[HotTopicCollector] 预过滤后无相关热点，跳过 AI 分析和入库");
            hotTopicsCache = List.of();
            return;
        }

        List<TopicCandidate> enriched;
        boolean aiSucceeded = true;
        try {
            enriched = aiFilterService.enrichWithAi(filtered);
        } catch (Exception e) {
            log.error("[HotTopicCollector] AI 增强失败，使用规则过滤结果", e);
            enriched = filtered;
            aiSucceeded = false;
        }
        log.info("[HotTopicCollector] AI 增强后保留 {} 条", enriched.size());

        // AI 增强成功后：保留 relevant=true 或 aiScore>=3 的条目；
        // 不再强制要求有险种标签，避免 AI 漏打标签时把所有热点都过滤掉
        List<TopicCandidate> filteredByAi;
        if (aiSucceeded) {
            filteredByAi = new ArrayList<>();
            for (TopicCandidate c : enriched) {
                // aiScore 存在 score 字段里（TopicAiFilterService 已写入）
                // whyThisTopic 非空 = AI 认为有价值；insuranceTypes 非空 = AI 给了险种
                boolean hasTypes = c.getInsuranceTypes() != null && !c.getInsuranceTypes().isEmpty();
                boolean hasWhy   = c.getWhyThisTopic() != null && !c.getWhyThisTopic().isBlank();
                if (hasTypes || hasWhy) {
                    filteredByAi.add(c);
                }
            }
        } else {
            filteredByAi = enriched;
        }
        log.info("[HotTopicCollector] AI 质量过滤后保留 {} 条", filteredByAi.size());

        List<HotTopic> toInsert = new ArrayList<>();
        for (TopicCandidate c : filteredByAi) {
            // 使用 AI 增强前的原始基础分，避免 AI 改写导致分数膨胀
            int baseScore = preAiScores.getOrDefault(System.identityHashCode(c), c.getScore());
            HotTopic ht = toHotTopic(c, today, baseScore, 0,
                    "TOPHUB", "NEWS_HOTSPOT");
            if (ht != null) toInsert.add(ht);
        }

        if (!toInsert.isEmpty()) {
            repository.batchInsert(toInsert);
            log.info("[HotTopicCollector] 成功入库 {} 条热点选题", toInsert.size());
        }

        refreshCache(today);
    }

    // ==================== 飞书知识库采集（15:30） ====================

    @Scheduled(cron = "0 30 15 * * ?", zone = "Asia/Shanghai")
    public void collectBitableTopics() {
        log.info("[HotTopicCollector] 开始定时采集飞书知识库...");
        LocalDate today = LocalDate.now();

        int activeTableCount = 0;
        for (BitableConfig cfg : configService.getAllConfigs()) {
            if (cfg.isActive()
                    && cfg.getAppToken() != null && !cfg.getAppToken().isBlank()
                    && cfg.getTableId() != null && !cfg.getTableId().isBlank()) {
                activeTableCount++;
            }
        }
        if (activeTableCount == 0) {
            log.info("[HotTopicCollector] 无活跃飞书知识库表，跳过采集");
            return;
        }

        // 清除飞书缓存，确保读到最新数据
        bitableReader.clearCache();

        List<TopicCandidate> bitableTopics = bitableReader.readAll(null);
        if (bitableTopics.isEmpty()) {
            log.warn("[HotTopicCollector] 飞书知识库返回为空，跳过本次采集");
            return;
        }
        log.info("[HotTopicCollector] 飞书知识库返回 {} 条", bitableTopics.size());

        List<HotTopic> toInsert = new ArrayList<>();
        for (TopicCandidate c : bitableTopics) {
            String sourceCategory = deriveSourceCategory(c);
            HotTopic ht = toHotTopic(c, today, c.getScore(), 0,
                    "BITABLE", sourceCategory);
            if (ht != null) {
                ht.setSourceSite(c.getSourceLabel());
                toInsert.add(ht);
            }
        }

        if (!toInsert.isEmpty()) {
            repository.batchInsert(toInsert);
            log.info("[HotTopicCollector] 飞书知识库入库 {} 条", toInsert.size());
        }

        refreshCache(today);
    }

    /** 飞书选题的来源分类 */
    private static String deriveSourceCategory(TopicCandidate c) {
        if (c.getSourceCategory() != null && !c.getSourceCategory().isBlank()) {
            return c.getSourceCategory();
        }
        if (c.getSource() == TopicCandidate.Source.HOTSPOT) return "HOTSPOT_NOTE";
        if (c.getSource() == TopicCandidate.Source.TEMPLATE) return "HOT_TEMPLATE";
        return "BITABLE_TOPIC";
    }

    /** 刷新内存缓存（从 DB 读取当日所有数据，包括 TopHub + 飞书） */
    public void refreshCache(LocalDate date) {
        List<HotTopic> fromDb = repository.findByBatchDate(date);
        List<TopicCandidate> mapped = new ArrayList<>();
        for (HotTopic ht : fromDb) {
            mapped.add(toCandidate(ht));
        }
        this.hotTopicsCache = new CopyOnWriteArrayList<>(mapped);
        log.info("[HotTopicCollector] 内存缓存刷新，共 {} 条（{}）", mapped.size(), date);
    }

    /** 获取缓存中的今日热点（用于 /daily 快速返回） */
    public List<TopicCandidate> getCachedTopics() {
        return hotTopicsCache;
    }

    // ==================== 映射辅助 ====================

    static HotTopic toHotTopic(TopicCandidate c, LocalDate batchDate,
                                int heatScore, int aiScore,
                                String source, String sourceCategory) {
        String title = c.getTitle();
        if (title == null || title.isBlank()) return null;

        HotTopic ht = new HotTopic();
        ht.setTitle(title);
        ht.setTitleHash(sha256(title));
        ht.setSource(source);
        ht.setSourceUrl(c.getSourceUrl());
        ht.setSourceSite(extractSite(c));
        ht.setHeatScore(heatScore);
        ht.setAiScore(aiScore);
        ht.setWhyThisTopic(c.getWhyThisTopic());
        ht.setSourceCategory(sourceCategory);
        ht.setBatchDate(batchDate);

        if (c.getInsuranceTypes() != null && !c.getInsuranceTypes().isEmpty()) {
            ht.setInsuranceTypes(c.getInsuranceTypes());
        }
        if (c.getDemographics() != null && !c.getDemographics().isEmpty()) {
            ht.setDemographics(c.getDemographics());
        }
        if (c.getRecommendedPlatforms() != null && !c.getRecommendedPlatforms().isEmpty()) {
            ht.setPlatforms(c.getRecommendedPlatforms());
        }

        return ht;
    }

    public static TopicCandidate toCandidate(HotTopic ht) {
        TopicCandidate c = new TopicCandidate();
        String src = ht.getSource();

        if ("BITABLE".equals(src)) {
            c.setSource(TopicCandidate.Source.HOTSPOT);
            String site = ht.getSourceSite();
            c.setSourceLabel(site != null ? site : "飞书知识库 · 素材");
        } else {
            c.setSource(TopicCandidate.Source.TOPHUB);
            c.setSourceLabel("今日热榜 · " + (ht.getSourceSite() != null ? ht.getSourceSite() : "热点"));
        }

        c.setId("ht-" + ht.getId());
        c.setTitle(ht.getTitle());
        c.setSourceUrl(ht.getSourceUrl());
        c.setScore(Math.min(100, Math.max(0, ht.getHeatScore())));
        c.setWhyThisTopic(ht.getWhyThisTopic());
        c.setSourceCategory(ht.getSourceCategory());
        if (ht.getCreatedAt() != null) c.setCreatedAt(ht.getCreatedAt());

        if (ht.getInsuranceTypes() != null) c.setInsuranceTypes(ht.getInsuranceTypes());
        if (ht.getDemographics() != null) c.setDemographics(ht.getDemographics());
        if (ht.getPlatforms() != null) c.setRecommendedPlatforms(ht.getPlatforms());

        c.setReason(ht.getWhyThisTopic());
        c.setSuggestedAgent("xhs-title");
        return c;
    }

    private static String extractSite(TopicCandidate c) {
        String label = c.getSourceLabel();
        if (label != null && label.contains("·")) {
            String[] parts = label.split("·");
            return parts.length > 1 ? parts[1].trim() : label;
        }
        return label != null ? label : "热点";
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

}
