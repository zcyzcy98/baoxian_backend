package com.insurance.agent.controller;

import com.insurance.agent.dto.*;
import com.insurance.agent.exception.InsufficientCreditsException;
import com.insurance.agent.model.HotTopic;
import com.insurance.agent.repository.HotTopicRepository;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.BitableConfigService;
import com.insurance.agent.service.BitableTopicReader;
import com.insurance.agent.service.CreditsService;
import com.insurance.agent.service.HotTopicCollector;
import com.insurance.agent.service.TopicGenerationService;
import com.insurance.agent.service.TopHubDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private static final int REFRESH_CREDIT_COST = 1;

    private final TopicGenerationService generation;
    private final BitableTopicReader bitableReader;
    private final BitableConfigService bitableConfig;
    private final TopHubDataService topHubDataService;
    private final HotTopicCollector collector;
    private final CreditsService creditsService;
    private final AuthService authService;
    private final HotTopicRepository hotTopicRepository;

    public TopicController(TopicGenerationService generation,
                           BitableTopicReader bitableReader,
                           BitableConfigService bitableConfig,
                           TopHubDataService topHubDataService,
                           HotTopicCollector collector,
                           CreditsService creditsService,
                           AuthService authService,
                           HotTopicRepository hotTopicRepository) {
        this.generation = generation;
        this.bitableReader = bitableReader;
        this.bitableConfig = bitableConfig;
        this.topHubDataService = topHubDataService;
        this.collector = collector;
        this.creditsService = creditsService;
        this.authService = authService;
        this.hotTopicRepository = hotTopicRepository;
    }

    private long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return 0;
        return authService.userIdByToken(authHeader.substring(7));
    }

    /**
     * 选题广场主接口：从 hot_topics 数据库读取全部热点（最近200条）。
     * 数据由定时任务（8:00/18:00 TopHub + 15:30 飞书知识库）自动写入。
     * 老的选题通过时间衰减自动扣分，越久的分越低。
     * 再按用户画像排序后返回。
     */
    @PostMapping("/daily")
    public ResponseEntity<Map<String, Object>> daily(@RequestBody(required = false) DailyRequest req) {
        DailyRequest r = req == null ? new DailyRequest() : req;
        int limit = r.getLimit() <= 0 ? 30 : Math.min(100, r.getLimit());

        // 读取全部热点，不再按日期筛选 — 老的通过时间衰减自动扣分
        List<HotTopic> dbTopics = hotTopicRepository.findAll();
        List<TopicCandidate> list = new ArrayList<>();
        for (HotTopic ht : dbTopics) {
            list.add(HotTopicCollector.toCandidate(ht));
        }

        list = generation.filterByProfile(list, r.getProfile(),
                r.getInsuranceTypesFilter(), r.getDemographicsFilter(), r.getSourceCategory(), limit);

        int activeBitables = 0;
        for (BitableConfig cfg : bitableConfig.getAllConfigs()) {
            if (cfg.isActive()
                    && cfg.getAppToken() != null && !cfg.getAppToken().isBlank()
                    && cfg.getTableId() != null && !cfg.getTableId().isBlank()) {
                activeBitables++;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("count", list.size());
        out.put("items", list);
        out.put("activeBitables", activeBitables);
        return ResponseEntity.ok(out);
    }

    /**
     * 手动刷新数据 — 扣 1 积分。
     * 实时调用 TopHubData 拉热榜 → AI 分析（whyThisTopic / 险种 / 客群 / 平台）→
     * 按用户画像个性化排序，返回完整富文本字段。
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) DailyRequest req) {
        long userId = resolveUserId(auth);
        if (userId == 0) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "请先登录"));
        }

        creditsService.deduct(userId, REFRESH_CREDIT_COST, "topic_refresh",
                "手动刷新选题广场数据", null);

        int newBalance = creditsService.getBalance(userId);

        DailyRequest r = req == null ? new DailyRequest() : req;
        int limit = r.getLimit() <= 0 ? 30 : Math.min(100, r.getLimit());

        // 手动刷新独立评分：AI 主导（最高 65）+ 个人标签加成（+20）+ 热度微调（+10），封顶 95
        List<TopicCandidate> list = generation.generateForManualRefresh(
                r.getProfile(),
                limit,
                r.getInsuranceTypesFilter(),
                r.getDemographicsFilter()
        );

        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("count", list.size());
        out.put("items", list);
        out.put("balance", newBalance);
        out.put("refreshedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(out);
    }

    /** 获取搜索可用的来源网站列表. */
    @GetMapping("/search-options")
    public ResponseEntity<Map<String, Object>> searchOptions() {
        List<TopHubDataService.NodeInfo> nodes = topHubDataService.fetchNodes();
        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("nodes", nodes);
        return ResponseEntity.ok(out);
    }

    /** 按关键词搜索全网热点（TopHubData API）+ AI 增强 + 个性化评分. */
    @PostMapping("/search")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String keyword = body == null ? null : (String) body.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "关键词不能为空"));
        }

        int limit = 50;
        Object l = body.get("limit");
        if (l instanceof Number) limit = Math.min(100, Math.max(1, ((Number) l).intValue()));

        String hashid = body == null ? null : (String) body.get("hashid");

        // 可选：前端可在 body 里带 profile，用户画像加成生效
        UserProfile profile = null;
        Object p = body.get("profile");
        if (p instanceof Map) {
            profile = mapToProfile((Map<?, ?>) p);
        }

        List<TopicCandidate> results = generation.searchWithAi(keyword.trim(), limit, hashid, profile);

        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("keyword", keyword);
        out.put("count", results.size());
        out.put("items", results);
        return ResponseEntity.ok(out);
    }

    /** 用户输入一个选题, 立即包成 candidate (用于"我自己想个"按钮). */
    @PostMapping("/from-input")
    public ResponseEntity<TopicCandidate> fromInput(@RequestBody Map<String, Object> body) {
        String title = body == null ? null : (String) body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserProfile profile = null;
        Object p = body.get("profile");
        if (p instanceof Map) {
            profile = mapToProfile((Map<?, ?>) p);
        }
        return ResponseEntity.ok(generation.fromUserInput(title.trim(), profile));
    }

    /**
     * ========== 调试 / 手动触发接口 ==========
     * 方便开发时测试定时采集逻辑，不用等 cron 执行。
     * 生产环境建议加上 @Profile("dev") 限制。
     */

    /** 手动触发 TopHub 热点采集（含清库重采） */
    @PostMapping("/debug/collect-tophub")
    public ResponseEntity<Map<String, Object>> debugCollectTopHub() {
        int deleted = hotTopicRepository.deleteByBatchDateAndSource(LocalDate.now(), "TOPHUB");
        collector.collectHotTopics();
        int cacheSize = collector.getCachedTopics().size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "清库 " + deleted + " 条 TopHub + 采集完成",
                "cacheSize", cacheSize
        ));
    }

    /** 手动触发飞书知识库采集（先清今日 BITABLE 记录，再重新采集） */
    @PostMapping("/debug/collect-bitable")
    public ResponseEntity<Map<String, Object>> debugCollectBitable() {
        int deleted = hotTopicRepository.deleteByBatchDateAndSource(LocalDate.now(), "BITABLE");
        collector.collectBitableTopics();
        int cacheSize = collector.getCachedTopics().size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "清库 " + deleted + " 条 BITABLE + 采集完成",
                "cacheSize", cacheSize
        ));
    }



    /** 查看当前内存缓存状态 */
    @GetMapping("/debug/cache")
    public ResponseEntity<Map<String, Object>> debugCache() {
        List<TopicCandidate> cached = collector.getCachedTopics();
        List<Map<String, Object>> summary = new ArrayList<>();
        for (TopicCandidate c : cached) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("title", c.getTitle() != null ? c.getTitle().substring(0, Math.min(30, c.getTitle().length())) : "");
            item.put("source", c.getSourceLabel());
            item.put("score", c.getScore());
            item.put("insuranceTypes", c.getInsuranceTypes());
            item.put("demographics", c.getDemographics());
            summary.add(item);
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", cached.size(),
                "items", summary
        ));
    }

    /** 清缓存 (调试用). */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> clearCache() {
        bitableReader.clearCache();
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 列出所有飞书选题源 (active 配置), 让前端知道现在有几张表在供数据. */
    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BitableConfig cfg : bitableConfig.getAllConfigs()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", cfg.getId());
            info.put("name", cfg.getName());
            info.put("description", cfg.getDescription());
            info.put("kind", cfg.getKind() == null ? "HOT_NOTE" : cfg.getKind());
            info.put("category", cfg.getCategory());
            info.put("active", cfg.isActive());
            info.put("hasAppToken", cfg.getAppToken() != null && !cfg.getAppToken().isBlank());
            info.put("hasTableId", cfg.getTableId() != null && !cfg.getTableId().isBlank());
            info.put("fieldMap", cfg.getFieldMap());
            out.add(info);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "supportedKinds", bitableReader.supportedKinds(),
                "defaultFieldMaps", BitableTopicReader.DEFAULT_FIELD_MAPS,
                "sources", out
        ));
    }

    /** 调试: 单独读某个飞书表, 看转出来的 candidate 长啥样. */
    @PostMapping("/sources/{configId}/preview")
    public ResponseEntity<Map<String, Object>> previewSource(@PathVariable String configId,
                                                              @RequestBody(required = false) Map<String, Object> body) {
        BitableConfig cfg = bitableConfig.getConfig(configId);
        if (cfg == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "未找到配置"));
        }
        UserProfile profile = null;
        if (body != null && body.get("profile") instanceof Map) {
            profile = mapToProfile((Map<?, ?>) body.get("profile"));
        }
        List<TopicCandidate> list = bitableReader.readOne(cfg, profile);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "configName", cfg.getName(),
                "kind", cfg.getKind() == null ? "HOT_NOTE" : cfg.getKind(),
                "count", list.size(),
                "items", list
        ));
    }

    @SuppressWarnings("unchecked")
    private static UserProfile mapToProfile(Map<?, ?> m) {
        UserProfile p = new UserProfile();
        Object pp = m.get("primaryProducts");
        if (pp instanceof List) p.setPrimaryProducts(((List<Object>) pp).stream()
                .map(String::valueOf).toList());
        Object ta = m.get("targetAudiences");
        if (ta instanceof List) p.setTargetAudiences(((List<Object>) ta).stream()
                .map(String::valueOf).toList());
        if (m.get("ageRange") instanceof String) p.setAgeRange((String) m.get("ageRange"));
        if (m.get("region") instanceof String) p.setRegion((String) m.get("region"));
        if (m.get("style") instanceof String) p.setStyle((String) m.get("style"));
        return p;
    }

    public static class DailyRequest {
        private UserProfile profile;
        private List<String> categories;
        private int limit;
        private List<String> insuranceTypesFilter;
        private List<String> demographicsFilter;
        private String sourceCategory;

        public UserProfile getProfile() { return profile; }
        public void setProfile(UserProfile profile) { this.profile = profile; }
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public List<String> getInsuranceTypesFilter() { return insuranceTypesFilter; }
        public void setInsuranceTypesFilter(List<String> insuranceTypesFilter) { this.insuranceTypesFilter = insuranceTypesFilter; }
        public List<String> getDemographicsFilter() { return demographicsFilter; }
        public void setDemographicsFilter(List<String> demographicsFilter) { this.demographicsFilter = demographicsFilter; }
        public String getSourceCategory() { return sourceCategory; }
        public void setSourceCategory(String sourceCategory) { this.sourceCategory = sourceCategory; }
    }
}
