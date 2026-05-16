package com.insurance.agent.service;

import com.insurance.agent.dto.BitableConfig;
import com.insurance.agent.dto.TopicCandidate;
import com.insurance.agent.dto.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * 把多张飞书"知识库表"按 kind 分发, 转成统一的 TopicCandidate 列表.
 *
 * 现支持 4 种 kind (新增 kind 只需在这里加一个 case + 默认字段映射):
 *   HOT_NOTE  爆款笔记 → 直接当选题候选
 *   TEMPLATE  标题/结构模板 → 渲染成"模板型选题"
 *   CASE      理赔/成功案例 → 一个案例 = 一个选题
 *   FAQ       客户咨询 → 一个常见问题 = 一个选题
 *
 * 每个 kind 自带默认中文列名, 用户表里只要列名一致就不用配 fieldMap;
 * 列名不一样, 在 BitableConfig.fieldMap 里覆盖即可.
 */
@Service
public class BitableTopicReader {
    private static final Logger log = LoggerFactory.getLogger(BitableTopicReader.class);

    public static final String KIND_HOT_NOTE = "HOT_NOTE";
    public static final String KIND_TEMPLATE = "TEMPLATE";
    public static final String KIND_CASE = "CASE";
    public static final String KIND_FAQ = "FAQ";

    /** 每个 kind 的默认 "标准语义名 → 飞书中文列名" 映射. */
    public static final Map<String, Map<String, String>> DEFAULT_FIELD_MAPS;
    static {
        Map<String, Map<String, String>> m = new HashMap<>();

        // 爆款笔记: 你截图里那张表 (标题/正文/来源/点赞数/链接/情绪/人设/标签)
        Map<String, String> hot = new LinkedHashMap<>();
        hot.put("title",       "标题");
        hot.put("body",        "正文");
        hot.put("sourceLabel", "来源");
        hot.put("likes",       "点赞数");
        hot.put("url",         "链接");
        hot.put("emotion",     "情绪");
        hot.put("persona",     "人设");
        hot.put("tags",        "标签");
        hot.put("insuranceTypes", "insurance_types");
        hot.put("demographics",   "demographics");
        hot.put("platforms",      "platforms");
        hot.put("whyThisTopic",   "why_this_topic");
        m.put(KIND_HOT_NOTE, hot);

        // 模板表: 标题模式 / 正文结构 / 适用险种 / 适用客群 ...
        Map<String, String> tpl = new LinkedHashMap<>();
        tpl.put("templateTitle",       "模板标题");
        tpl.put("titlePattern",        "标题模板");
        tpl.put("bodyStructure",       "正文结构");
        tpl.put("applicableProducts",  "适用险种");
        tpl.put("applicableAudiences", "适用客群");
        tpl.put("manualScore",         "人工热度");
        tpl.put("autoScore",           "机器热度");
        tpl.put("sampleUrl",           "样例链接");
        tpl.put("keywords",            "关键词");
        m.put(KIND_TEMPLATE, tpl);

        // 案例库: 标题 + 描述
        Map<String, String> caseMap = new LinkedHashMap<>();
        caseMap.put("title", "案例标题");
        caseMap.put("body",  "案例描述");
        caseMap.put("tags",  "标签");
        caseMap.put("url",   "链接");
        m.put(KIND_CASE, caseMap);

        // 常见问题
        Map<String, String> faq = new LinkedHashMap<>();
        faq.put("title", "问题");
        faq.put("body",  "回答");
        faq.put("tags",  "标签");
        m.put(KIND_FAQ, faq);

        DEFAULT_FIELD_MAPS = Collections.unmodifiableMap(m);
    }

    private final FeishuBitableService feishu;
    private final BitableConfigService configService;

    // 简单内存缓存 (tableId → records, 30 分钟)
    private final Map<String, CachedRecords> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedRecords {
        final long timestamp;
        final List<Map<String, Object>> records;
        CachedRecords(List<Map<String, Object>> records) {
            this.timestamp = System.currentTimeMillis();
            this.records = records;
        }
    }

    public BitableTopicReader(FeishuBitableService feishu, BitableConfigService configService) {
        this.feishu = feishu;
        this.configService = configService;
    }

    /** 读所有 active 且填了 appToken/tableId 且允许出现在选题广场的飞书表, 转成 TopicCandidate. */
    public List<TopicCandidate> readAll(UserProfile profile) {
        List<TopicCandidate> out = new ArrayList<>();
        for (BitableConfig cfg : configService.getAllConfigs()) {
            if (!cfg.isActive()) continue;
            if (!cfg.isShowInTopicSquare()) continue;   // 不展示在选题广场的表直接跳过
            if (isBlank(cfg.getAppToken()) || isBlank(cfg.getTableId())) continue;
            try {
                List<TopicCandidate> one = readOne(cfg, profile);
                out.addAll(one);
            } catch (Exception e) {
                log.warn("read bitable {} failed: {}", cfg.getName(), e.getMessage());
            }
        }
        return out;
    }

    /** 读单个表 (带缓存). */
    @SuppressWarnings("unchecked")
    public List<TopicCandidate> readOne(BitableConfig cfg, UserProfile profile) {
        String kind = (cfg.getKind() == null || cfg.getKind().isBlank())
                ? KIND_HOT_NOTE : cfg.getKind().toUpperCase();
        Map<String, String> fieldMap = cfg.resolveFieldMap(
                DEFAULT_FIELD_MAPS.getOrDefault(kind, DEFAULT_FIELD_MAPS.get(KIND_HOT_NOTE)));

        List<Map<String, Object>> records = loadRecordsCached(cfg);
        if (records.isEmpty()) return List.of();

        switch (kind) {
            case KIND_HOT_NOTE: return convertHotNotes(records, cfg, fieldMap, profile);
            case KIND_TEMPLATE: return convertTemplates(records, cfg, fieldMap, profile);
            case KIND_CASE:     return convertCases(records, cfg, fieldMap, profile);
            case KIND_FAQ:      return convertFaqs(records, cfg, fieldMap, profile);
            default:            return convertHotNotes(records, cfg, fieldMap, profile);
        }
    }

    public void clearCache() { cache.clear(); }

    public Set<String> supportedKinds() { return DEFAULT_FIELD_MAPS.keySet(); }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadRecordsCached(BitableConfig cfg) {
        String key = cfg.getAppToken() + ":" + cfg.getTableId();
        CachedRecords c = cache.get(key);
        if (c != null && System.currentTimeMillis() - c.timestamp < 30 * 60_000L) {
            return c.records;
        }
        Map<String, Object> result = feishu.getAllRecords(null, cfg.getAppToken(), cfg.getTableId());
        if (!Boolean.TRUE.equals(result.get("success"))) {
            log.warn("飞书读取失败 ({}): {}", cfg.getName(), result.get("error"));
            return List.of();
        }
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        if (records == null) records = List.of();
        cache.put(key, new CachedRecords(records));
        return records;
    }

    // ─── 各 kind 的转换方法 ─────────────────────────

    @SuppressWarnings("unchecked")
    private List<TopicCandidate> convertHotNotes(List<Map<String, Object>> records,
                                                  BitableConfig cfg,
                                                  Map<String, String> fieldMap,
                                                  UserProfile profile) {
        List<TopicCandidate> out = new ArrayList<>();
        List<Integer> rawQList = new ArrayList<>(); // 与 out 一一对应

        for (Map<String, Object> r : records) {
            Map<String, Object> f = (Map<String, Object>) r.get("fields");
            String title = asText(getField(f, fieldMap, "title"));
            if (isBlank(title)) continue;

            String emotion      = asText(getField(f, fieldMap, "emotion"));
            String persona      = asText(getField(f, fieldMap, "persona"));
            String src          = asText(getField(f, fieldMap, "sourceLabel"));
            String url          = asText(getField(f, fieldMap, "url"));
            List<String> tags           = asList(getField(f, fieldMap, "tags"));
            List<String> insuranceTypes = asList(getField(f, fieldMap, "insuranceTypes"));
            List<String> demographics   = asList(getField(f, fieldMap, "demographics"));
            List<String> platforms      = asList(getField(f, fieldMap, "platforms"));
            String whyThisTopic = asText(getField(f, fieldMap, "whyThisTopic"));

            TopicCandidate c = new TopicCandidate();
            c.setId("hot-" + Integer.toHexString(((cfg.getId() == null ? "" : cfg.getId()) + title).hashCode()));
            c.setSource(TopicCandidate.Source.HOTSPOT);
            c.setTitle(title);
            c.setSourceLabel(cfg.getName() + (isBlank(src) ? "" : " · " + src));
            c.setSourceUrl(url);
            c.setAngle(String.join(" · ", filterBlanks(persona, emotion)));

            if (tags != null)           c.getTags().addAll(tags);
            if (!isBlank(persona))      c.getTags().add(persona);
            if (!isBlank(emotion))      c.getTags().add(emotion);
            if (insuranceTypes != null) c.setInsuranceTypes(insuranceTypes);
            if (demographics != null)   c.setDemographics(demographics);
            if (platforms != null)      c.setRecommendedPlatforms(platforms);
            if (!isBlank(whyThisTopic)) c.setWhyThisTopic(whyThisTopic);
            c.setSuggestedAgent("xhs-title");

            // ── 多维原始质量分（用于批内 rank 排序，不是最终分）──────────────
            int rq = 0;
            if (!isBlank(whyThisTopic)) rq += 20;                                      // 最重要：有 AI 推荐理由
            if (insuranceTypes != null) rq += Math.min(4, insuranceTypes.size()) * 5;  // 险种丰富度 0-20
            if (demographics != null)   rq += Math.min(3, demographics.size())   * 4;  // 人群丰富度 0-12
            if (platforms != null)      rq += Math.min(3, platforms.size())       * 3;  // 平台覆盖度 0-9
            if (!isBlank(persona))      rq += 4;
            if (!isBlank(emotion))      rq += 3;
            if (!isBlank(url))          rq += 1;
            // Profile 匹配纳入排序依据（优先展示匹配用户主营险种/目标客群的内容）
            rq += hotNoteProfileBonus(profile, insuranceTypes, demographics);

            rawQList.add(rq);
            out.add(c);
        }

        if (out.isEmpty()) return out;

        // ── Rank 归一化：把批内质量排名映射到 [60, 95] 区间 ─────────────────
        // 保证：① 每条分数大概率唯一；② BITABLE 最低 60 > TOPHUB 封顶 58
        int n = out.size();

        // 构造 (原始下标, rawQuality) 对，按 rawQ 降序排列，rawQ 相同时用 id hash 做确定性微扰
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int tiebreak = Math.abs(out.get(i).getId().hashCode() % 1000);
            pairs.add(new int[]{i, rawQList.get(i) * 1000 + tiebreak}); // 高位是质量，低位是微扰
        }
        pairs.sort((a, b) -> Integer.compare(b[1], a[1]));

        for (int rank = 0; rank < n; rank++) {
            int idx = pairs.get(rank)[0];
            // rank=0 → 最优 → 95 分；rank=n-1 → 最差 → 60 分
            int score = n == 1 ? 77 : (int) Math.round(60.0 + 35.0 * (n - 1 - rank) / (n - 1));
            out.get(idx).setScore(score);
        }

        out.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return out;
    }

    /**
     * 计算 HOT_NOTE 记录与用户画像的匹配奖励分（纳入 rank 排序）。
     * 直接匹配 insuranceTypes / demographics 字段，不再依赖 tags。
     */
    private static int hotNoteProfileBonus(UserProfile profile,
                                            List<String> insuranceTypes,
                                            List<String> demographics) {
        if (profile == null) return 0;
        int bonus = 0;
        // 主营险种匹配：每匹配一个 +8，最多 +16
        if (profile.getPrimaryProducts() != null && insuranceTypes != null) {
            for (String prod : profile.getPrimaryProducts()) {
                if (prod == null) continue;
                for (String type : insuranceTypes) {
                    if (type != null && (type.contains(prod) || prod.contains(type))) {
                        bonus += 8;
                        break;
                    }
                }
            }
        }
        // 目标客群匹配：每匹配一个 +6，最多 +12
        if (profile.getTargetAudiences() != null && demographics != null) {
            for (String aud : profile.getTargetAudiences()) {
                if (aud == null) continue;
                for (String demo : demographics) {
                    if (demo != null && (demo.contains(aud) || aud.contains(demo))) {
                        bonus += 6;
                        break;
                    }
                }
            }
        }
        return Math.min(20, bonus);
    }

    @SuppressWarnings("unchecked")
    private List<TopicCandidate> convertTemplates(List<Map<String, Object>> records,
                                                   BitableConfig cfg,
                                                   Map<String, String> fieldMap,
                                                   UserProfile profile) {
        List<TopicCandidate> out = new ArrayList<>();
        for (Map<String, Object> r : records) {
            Map<String, Object> f = (Map<String, Object>) r.get("fields");
            String tpl = asText(getField(f, fieldMap, "templateTitle"));
            String pattern = asText(getField(f, fieldMap, "titlePattern"));
            String body = asText(getField(f, fieldMap, "bodyStructure"));
            if (isBlank(tpl) && isBlank(pattern)) continue;
            Integer manual = parseInt(getField(f, fieldMap, "manualScore"));
            Integer auto = parseInt(getField(f, fieldMap, "autoScore"));
            List<String> products = asList(getField(f, fieldMap, "applicableProducts"));
            List<String> audiences = asList(getField(f, fieldMap, "applicableAudiences"));

            TopicCandidate c = new TopicCandidate();
            c.setId("tpl-" + Integer.toHexString((cfg.getId() + tpl + pattern).hashCode()));
            c.setSource(TopicCandidate.Source.TEMPLATE);
            c.setTitle(isBlank(pattern) ? tpl : pattern);
            c.setAngle(body);
            c.setSourceLabel(cfg.getName() + " · " + (isBlank(tpl) ? "模板" : tpl));

            int score = 60
                    + (manual == null ? 0 : Math.min(5, Math.max(0, manual)) * 4)  // 0-20
                    + (auto   == null ? 0 : Math.min(100, Math.max(0, auto)) / 5)  // 0-20
                    + bonusForUserMatch(profile, mergeTags(products, audiences));
            c.setScore(Math.min(100, score));

            if (products != null) c.getTags().addAll(products);
            if (audiences != null) c.getTags().addAll(audiences);
            c.setSuggestedAgent("xhs-text");
            out.add(c);
        }
        out.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<TopicCandidate> convertCases(List<Map<String, Object>> records,
                                               BitableConfig cfg,
                                               Map<String, String> fieldMap,
                                               UserProfile profile) {
        List<TopicCandidate> out = new ArrayList<>();
        for (Map<String, Object> r : records) {
            Map<String, Object> f = (Map<String, Object>) r.get("fields");
            String title = asText(getField(f, fieldMap, "title"));
            if (isBlank(title)) continue;
            String body = asText(getField(f, fieldMap, "body"));
            List<String> tags = asList(getField(f, fieldMap, "tags"));
            String url = asText(getField(f, fieldMap, "url"));

            TopicCandidate c = new TopicCandidate();
            c.setId("case-" + Integer.toHexString((cfg.getId() + title).hashCode()));
            c.setSource(TopicCandidate.Source.TEMPLATE); // 没有 CASE source, 借用 TEMPLATE 标记
            c.setTitle(title);
            c.setAngle(body == null ? "" : truncate(body, 100));
            c.setSourceLabel(cfg.getName());
            c.setSourceUrl(url);
            c.setScore(70 + bonusForUserMatch(profile, tags));
            if (tags != null) c.getTags().addAll(tags);
            c.setSuggestedAgent("xhs-text");
            out.add(c);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<TopicCandidate> convertFaqs(List<Map<String, Object>> records,
                                              BitableConfig cfg,
                                              Map<String, String> fieldMap,
                                              UserProfile profile) {
        List<TopicCandidate> out = new ArrayList<>();
        for (Map<String, Object> r : records) {
            Map<String, Object> f = (Map<String, Object>) r.get("fields");
            String q = asText(getField(f, fieldMap, "title"));
            if (isBlank(q)) continue;
            String a = asText(getField(f, fieldMap, "body"));
            List<String> tags = asList(getField(f, fieldMap, "tags"));

            TopicCandidate c = new TopicCandidate();
            c.setId("faq-" + Integer.toHexString((cfg.getId() + q).hashCode()));
            c.setSource(TopicCandidate.Source.TEMPLATE);
            c.setTitle(q);
            c.setAngle(a == null ? "" : "参考答: " + truncate(a, 80));
            c.setSourceLabel(cfg.getName());
            c.setScore(55 + bonusForUserMatch(profile, tags));
            if (tags != null) c.getTags().addAll(tags);
            c.setSuggestedAgent("xhs-text");
            out.add(c);
        }
        return out;
    }

    // ─── 工具方法 ─────────────────────────

    private static Object getField(Map<String, Object> fields, Map<String, String> fieldMap, String semanticKey) {
        if (fields == null || fieldMap == null) return null;
        String chineseName = fieldMap.get(semanticKey);
        if (chineseName == null) return null;
        // 容错: 飞书有时候字段名前后带空格
        Object raw = fields.get(chineseName);
        if (raw != null) return raw;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getKey() != null && e.getKey().trim().equals(chineseName.trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String asText(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String) {
            String s = (String) raw;
            if (s.startsWith("{") && (s.contains("\"link\"") || s.contains("\"text\""))) {
                try {
                    Map<?, ?> m = JSON.readValue(s, Map.class);
                    Object link = m.get("link");
                    if (link != null) return asText(link);
                    Object text = m.get("text");
                    if (text != null) return asText(text);
                } catch (Exception ignored) {
                }
            }
            return s;
        }
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            if (list.isEmpty()) return null;
            Object first = list.get(0);
            return asText(first);
        }
        if (raw instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) raw;
            Object link = m.get("link");
            if (link != null) return asText(link);
            Object text = m.get("text");
            if (text != null) return asText(text);
        }
        return String.valueOf(raw);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List) {
            List<Object> in = (List<Object>) raw;
            List<String> out = new ArrayList<>();
            for (Object o : in) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) return null;
            String[] parts = s.split("[,，、;；/]");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
        return List.of(String.valueOf(raw));
    }

    private static Integer parseInt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).intValue();
        try { return Integer.valueOf(String.valueOf(raw).trim()); }
        catch (Exception e) { return null; }
    }

    private static Long parseLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number) return ((Number) raw).longValue();
        try { return Long.valueOf(String.valueOf(raw).trim().replaceAll("[^0-9]", "")); }
        catch (Exception e) { return null; }
    }

    private static int bonusForUserMatch(UserProfile profile, List<String> tags) {
        if (profile == null || tags == null || tags.isEmpty()) return 0;
        int b = 0;
        if (profile.getPrimaryProducts() != null) {
            for (String p : profile.getPrimaryProducts()) {
                for (String t : tags) {
                    if (p != null && t != null && t.toLowerCase().contains(p.toLowerCase())) { b += 5; break; }
                }
            }
        }
        if (profile.getTargetAudiences() != null) {
            for (String a : profile.getTargetAudiences()) {
                for (String t : tags) {
                    if (a != null && t != null && t.toLowerCase().contains(a.toLowerCase())) { b += 5; break; }
                }
            }
        }
        return Math.min(10, b);
    }

    private static List<String> mergeTags(Object... arrays) {
        List<String> out = new ArrayList<>();
        for (Object a : arrays) {
            if (a == null) continue;
            if (a instanceof List) {
                for (Object o : (List<?>) a) {
                    if (o != null) out.add(String.valueOf(o));
                }
            } else {
                out.add(String.valueOf(a));
            }
        }
        return out;
    }

    private static List<String> filterBlanks(String... arr) {
        List<String> out = new ArrayList<>();
        for (String s : arr) if (!isBlank(s)) out.add(s);
        return out;
    }

    private static String formatNumber(long n) {
        if (n >= 10000) return String.format("%.1fw", n / 10000.0);
        if (n >= 1000) return String.format("%.1fk", n / 1000.0);
        return String.valueOf(n);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
