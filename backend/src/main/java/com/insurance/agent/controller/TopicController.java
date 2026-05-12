package com.insurance.agent.controller;

import com.insurance.agent.dto.*;
import com.insurance.agent.service.BitableConfigService;
import com.insurance.agent.service.BitableTopicReader;
import com.insurance.agent.service.HotTopicCollector;
import com.insurance.agent.service.TopicGenerationService;
import com.insurance.agent.service.TopHubDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicGenerationService generation;
    private final BitableTopicReader bitableReader;
    private final BitableConfigService bitableConfig;
    private final TopHubDataService topHubDataService;
    private final HotTopicCollector collector;

    public TopicController(TopicGenerationService generation,
                           BitableTopicReader bitableReader,
                           BitableConfigService bitableConfig,
                           TopHubDataService topHubDataService,
                           HotTopicCollector collector) {
        this.generation = generation;
        this.bitableReader = bitableReader;
        this.bitableConfig = bitableConfig;
        this.topHubDataService = topHubDataService;
        this.collector = collector;
    }

    /**
     * 选题广场主接口：优先从 HotTopicCollector 缓存读取今日热点选题，
     * 再按用户画像排序；无缓存时降级到实时拉取 + AI（首次部署 / 8:00 前）。
     */
    @PostMapping("/daily")
    public ResponseEntity<Map<String, Object>> daily(@RequestBody(required = false) DailyRequest req) {
        DailyRequest r = req == null ? new DailyRequest() : req;
        int limit = r.getLimit() <= 0 ? 30 : Math.min(100, r.getLimit());
        List<TopicCandidate> list;

        // 阶段四：缓存优先路径——HotTopicCollector 每天8:00/18:00已采集好
        List<TopicCandidate> cached = collector.getCachedTopics();
        if (!cached.isEmpty()) {
            list = generation.filterByProfile(cached, r.getProfile(),
                    r.getInsuranceTypesFilter(), r.getDemographicsFilter(), limit);
        } else {
            // 降级路径：首次部署或 8:00 前，实时从 Bitable + TopHub 拉取
            Set<String> categories = r.getCategories() == null ? null
                    : new HashSet<>(r.getCategories());
            list = generation.generateDaily(r.getProfile(), categories, limit,
                    r.getInsuranceTypesFilter(), r.getDemographicsFilter());
        }

        // 统计已配置好的飞书表 (active + 有 appToken/tableId)
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

    /** 获取搜索可用的来源网站列表. */
    @GetMapping("/search-options")
    public ResponseEntity<Map<String, Object>> searchOptions() {
        List<TopHubDataService.NodeInfo> nodes = topHubDataService.fetchNodes();
        Map<String, Object> out = new HashMap<>();
        out.put("success", true);
        out.put("nodes", nodes);
        return ResponseEntity.ok(out);
    }

    /** 按关键词搜索全网热点（通过 TopHubData API）. */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String keyword = body == null ? null : (String) body.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "关键词不能为空"));
        }

        int limit = 50;
        Object l = body.get("limit");
        if (l instanceof Number) limit = Math.min(100, Math.max(1, ((Number) l).intValue()));

        String hashid = body == null ? null : (String) body.get("hashid");

        List<TopicCandidate> results = topHubDataService.searchByKeyword(keyword.trim(), limit, hashid);

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

    /** 手动触发 TopHub 热点采集 */
    @PostMapping("/debug/collect-tophub")
    public ResponseEntity<Map<String, Object>> debugCollectTopHub() {
        collector.collectHotTopics();
        int cacheSize = collector.getCachedTopics().size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "TopHub 采集完成",
                "cacheSize", cacheSize
        ));
    }

    /** 手动触发飞书知识库采集 */
    @PostMapping("/debug/collect-bitable")
    public ResponseEntity<Map<String, Object>> debugCollectBitable() {
        collector.collectBitableTopics();
        int cacheSize = collector.getCachedTopics().size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "飞书知识库采集完成",
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
    }
}
