package com.insurance.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@Service
public class StyleService {

    private static final Logger log = LoggerFactory.getLogger(StyleService.class);
    private static final long DEFAULT_USER_ID = 1L; // placeholder until auth is implemented

    private final DeepSeekService deepSeek;
    private final XhsExtractService xhsExtract;
    private final WechatExtractService wechatExtract;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;
    @Value("${spring.datasource.username:}")
    private String dbUser;
    @Value("${spring.datasource.password:}")
    private String dbPass;

    public StyleService(DeepSeekService deepSeek,
                        XhsExtractService xhsExtract,
                        WechatExtractService wechatExtract) {
        this.deepSeek = deepSeek;
        this.xhsExtract = xhsExtract;
        this.wechatExtract = wechatExtract;
    }

    @PostConstruct
    public void initSchema() {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return;
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS style_sources (
                  id          BIGSERIAL PRIMARY KEY,
                  user_id     BIGINT NOT NULL DEFAULT 1,
                  title       VARCHAR(200),
                  content_type VARCHAR(20) NOT NULL DEFAULT 'text',
                  url         VARCHAR(1000),
                  raw_text    TEXT,
                  word_count  INTEGER DEFAULT 0,
                  created_at  TIMESTAMPTZ DEFAULT NOW()
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS style_profiles (
                  id           BIGSERIAL PRIMARY KEY,
                  user_id      BIGINT NOT NULL DEFAULT 1 UNIQUE,
                  version      INTEGER NOT NULL DEFAULT 1,
                  source_count INTEGER DEFAULT 0,
                  total_words  INTEGER DEFAULT 0,
                  signature    TEXT,
                  radar        JSONB,
                  traits       JSONB,
                  trained_at   TIMESTAMPTZ DEFAULT NOW()
                )
            """);
            log.info("[StyleService] tables ready");
        } catch (Exception e) {
            log.warn("[StyleService] schema init skipped: {}", e.getMessage());
        }
    }

    // ─── Sources ────────────────────────────────────────────────────────────

    public Map<String, Object> addSource(String title, String type, String url, String rawText, String model) {
        String resolvedText = rawText;
        String resolvedType = type;
        String resolvedUrl  = url;

        if ("link".equals(type) && url != null && !url.isBlank()) {
            // 从可能混有表情、文字的分享文本中提取真实 URL
            String cleanUrl = extractUrlFromText(url);
            resolvedUrl = cleanUrl;
            String lower = cleanUrl.toLowerCase();
            try {
                if (lower.contains("xiaohongshu") || lower.contains("xhslink")) {
                    var note = xhsExtract.extract(cleanUrl);
                    resolvedText = buildXhsText(note.getTitle(), note.getContent());
                    resolvedType = "xhs";
                    if (title == null || title.isBlank()) title = note.getTitle();
                } else if (lower.contains("weixin") || lower.contains("mp.weixin")) {
                    var article = wechatExtract.extract(cleanUrl);
                    resolvedText = buildWechatText(article.getTitle(), article.getContent());
                    resolvedType = "gzh";
                    if (title == null || title.isBlank()) title = article.getTitle();
                } else {
                    resolvedText = "链接: " + cleanUrl;
                    resolvedType = "link";
                }
            } catch (Exception e) {
                log.warn("[StyleService] link extract failed for {}: {}", cleanUrl, e.getMessage());
                resolvedText = "链接: " + cleanUrl + "\n（内容提取失败：" + e.getMessage() + "）";
            }
        }

        int wordCount = resolvedText == null ? 0 : resolvedText.length();
        if (title == null || title.isBlank()) title = "素材 " + Instant.now().toEpochMilli() % 10000;

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO style_sources(user_id,title,content_type,url,raw_text,word_count) VALUES(?,?,?,?,?,?) RETURNING id,created_at")) {
            ps.setLong(1, DEFAULT_USER_ID);
            ps.setString(2, title);
            ps.setString(3, resolvedType);
            ps.setString(4, resolvedUrl);
            ps.setString(5, resolvedText);
            ps.setInt(6, wordCount);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong(1));
                    row.put("title", title);
                    row.put("contentType", resolvedType);
                    row.put("url", resolvedUrl);
                    row.put("preview", preview(resolvedText));
                    row.put("wordCount", wordCount);
                    row.put("createdAt", rs.getString(2));
                    return row;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("添加素材失败: " + e.getMessage(), e);
        }
        throw new RuntimeException("添加素材失败");
    }

    public void deleteSource(long id) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("DELETE FROM style_sources WHERE id=? AND user_id=?")) {
            ps.setLong(1, id);
            ps.setLong(2, DEFAULT_USER_ID);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除素材失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listSources() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id,title,content_type,url,raw_text,word_count,created_at FROM style_sources WHERE user_id=? ORDER BY created_at DESC")) {
            ps.setLong(1, DEFAULT_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("title", rs.getString("title"));
                    row.put("contentType", rs.getString("content_type"));
                    row.put("url", rs.getString("url"));
                    row.put("preview", preview(rs.getString("raw_text")));
                    row.put("wordCount", rs.getInt("word_count"));
                    row.put("createdAt", rs.getString("created_at"));
                    list.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("[StyleService] listSources: {}", e.getMessage());
        }
        return list;
    }

    // ─── Profile ────────────────────────────────────────────────────────────

    public Map<String, Object> getProfile() {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT version,source_count,total_words,signature,radar,traits,trained_at FROM style_profiles WHERE user_id=?")) {
            ps.setLong(1, DEFAULT_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("version", rs.getInt("version"));
                    m.put("sourceCount", rs.getInt("source_count"));
                    m.put("totalWords", rs.getInt("total_words"));
                    m.put("signature", rs.getString("signature"));
                    m.put("radar", parseJson(rs.getString("radar")));
                    m.put("traits", parseJson(rs.getString("traits")));
                    m.put("trainedAt", rs.getString("trained_at"));
                    return m;
                }
            }
        } catch (Exception e) {
            log.warn("[StyleService] getProfile: {}", e.getMessage());
        }
        return null;
    }

    // ─── Train ──────────────────────────────────────────────────────────────

    public Map<String, Object> train(String model) {
        List<Map<String, Object>> sources = listSources();
        if (sources.isEmpty()) throw new RuntimeException("请先添加至少一篇素材");

        // Re-query with full raw_text (listSources only returns preview snippets)
        StringBuilder corpus = new StringBuilder();
        int totalWords = 0;
        List<String> texts = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT raw_text, word_count FROM style_sources WHERE user_id=? AND raw_text IS NOT NULL ORDER BY created_at DESC")) {
            ps.setLong(1, DEFAULT_USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString("raw_text");
                    if (t != null && !t.isBlank()) {
                        texts.add(t);
                        totalWords += rs.getInt("word_count");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("读取素材失败: " + e.getMessage(), e);
        }

        for (int i = 0; i < texts.size(); i++) {
            corpus.append("【素材 ").append(i + 1).append("】\n").append(texts.get(i)).append("\n\n");
        }

        String system = """
                你是一位专业的写作风格分析师。请分析以下多篇文章素材，提炼作者的写作风格特征。

                【分析维度说明】
                - 语气温度（1-10）：文字给人的温暖/冷静感，10=极致温暖，1=极致冷静
                - 专业密度（1-10）：专业术语和知识点的密度，10=大量术语，1=全口语化
                - 句式节奏（1-10）：句子长短和节奏，10=长句/复杂句，1=短句/口语碎句
                - 情绪强度（1-10）：情绪表达的强度，10=感情充沛，1=克制内敛
                - 修辞偏好（1-10）：修辞手法的使用频率，10=大量比喻/排比，1=直白叙述
                - 结构习惯（1-10）：内容结构化程度，10=非常规整，1=随性流动

                请严格按以下 JSON 格式输出，不要有任何其他文字：
                {
                  "signature": "X、X、X（3-5个词，最能代表作者风格的关键词，用顿号分隔）",
                  "radar": {
                    "语气温度": 7.0,
                    "专业密度": 6.0,
                    "句式节奏": 5.0,
                    "情绪强度": 4.0,
                    "修辞偏好": 6.0,
                    "结构习惯": 7.0
                  },
                  "traits": [
                    {"text": "具体风格特征描述（要具体，引用原文词句）", "primary": true},
                    {"text": "具体风格特征描述", "primary": true},
                    {"text": "具体风格特征描述", "primary": false},
                    {"text": "具体风格特征描述", "primary": false},
                    {"text": "具体风格特征描述", "primary": false},
                    {"text": "具体风格特征描述", "primary": false},
                    {"text": "具体风格特征描述", "primary": false},
                    {"text": "具体风格特征描述", "primary": false}
                  ]
                }

                注意：
                - signature 要精炼，能一眼概括风格
                - traits 要具体，最好引用作者原文中的词语或句式特征
                - primary:true 的 traits 是最核心的2-3个特征
                - 所有数值保留一位小数
                """;

        String userPrompt = "请分析以下 " + texts.size() + " 篇素材的写作风格：\n\n" + corpus;
        String raw = deepSeek.chat(system, userPrompt, model);

        // Extract JSON from response
        String json = extractJson(raw);
        Map<String, Object> parsed;
        try {
            parsed = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("AI 返回格式解析失败，请重试: " + e.getMessage());
        }

        // Persist
        int version = 1;
        try (Connection c = conn()) {
            // Get current version
            try (PreparedStatement ps = c.prepareStatement("SELECT version FROM style_profiles WHERE user_id=?")) {
                ps.setLong(1, DEFAULT_USER_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) version = rs.getInt(1) + 1;
                }
            }
            String radarJson  = mapper.writeValueAsString(parsed.get("radar"));
            String traitsJson = mapper.writeValueAsString(parsed.get("traits"));
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO style_profiles(user_id,version,source_count,total_words,signature,radar,traits,trained_at)
                    VALUES(?,?,?,?,?,?::jsonb,?::jsonb,NOW())
                    ON CONFLICT(user_id) DO UPDATE SET
                      version=EXCLUDED.version, source_count=EXCLUDED.source_count,
                      total_words=EXCLUDED.total_words, signature=EXCLUDED.signature,
                      radar=EXCLUDED.radar, traits=EXCLUDED.traits, trained_at=NOW()
                    """)) {
                ps.setLong(1, DEFAULT_USER_ID);
                ps.setInt(2, version);
                ps.setInt(3, texts.size());
                ps.setInt(4, totalWords);
                ps.setString(5, (String) parsed.get("signature"));
                ps.setString(6, radarJson);
                ps.setString(7, traitsJson);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("保存训练结果失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", version);
        result.put("sourceCount", texts.size());
        result.put("totalWords", totalWords);
        result.put("signature", parsed.get("signature"));
        result.put("radar", parsed.get("radar"));
        result.put("traits", parsed.get("traits"));
        return result;
    }

    // ─── Preview ────────────────────────────────────────────────────────────

    public String preview(String topic, String model) {
        Map<String, Object> profile = getProfile();
        if (profile == null) throw new RuntimeException("请先训练风格");

        String signature = (String) profile.get("signature");
        Object traitsRaw = profile.get("traits");
        String traitsDesc = "";
        try {
            List<?> traits = (List<?>) traitsRaw;
            StringBuilder sb = new StringBuilder();
            for (Object t : traits) {
                Map<?,?> tm = (Map<?,?>) t;
                sb.append("- ").append(tm.get("text")).append("\n");
            }
            traitsDesc = sb.toString();
        } catch (Exception ignored) {}

        String radarDesc = "";
        try {
            Map<?,?> radar = (Map<?,?>) profile.get("radar");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?,?> e : radar.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("/10  ");
            }
            radarDesc = sb.toString();
        } catch (Exception ignored) {}

        String system = String.format("""
                你是一位保险内容博主，有独特的个人写作风格。请严格模仿以下风格特征写作：

                【风格签名】%s

                【风格特征】
                %s

                【各维度评分参考】
                %s

                要求：
                - 严格模仿以上风格特征，特别是高频词、句式、开场方式
                - 字数 200-350字
                - 直接输出内容，不要有任何前言或解释
                """, signature, traitsDesc, radarDesc);

        return deepSeek.chat(system, "主题: " + topic, model);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
    }

    private String preview(String text) {
        if (text == null) return "";
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }

    /**
     * 从带表情、标题、随机码的分享文本中提取第一个 http(s) URL。
     * 例：「24 【一篇教你车险...】 😆 3e3X... 😆 https://www.xiaohongshu.com/...」→ 真实 URL
     */
    private String extractUrlFromText(String text) {
        if (text == null || text.isBlank()) return text;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://[^\\s\\u4e00-\\u9fa5\\[\\]【】「」（）()]+")
                .matcher(text.trim());
        if (m.find()) {
            String url = m.group().replaceAll("[,，。.!！?？]+$", "");
            log.info("[StyleService] extracted URL: {}", url);
            return url;
        }
        return text.trim();
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        // Strip markdown code fences
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        int start = raw.indexOf('{');
        int end   = raw.lastIndexOf('}');
        if (start >= 0 && end > start) return raw.substring(start, end + 1);
        return raw;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, Object.class); } catch (Exception e) { return null; }
    }

    private String buildXhsText(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append("\n\n");
        if (content != null) sb.append(content);
        return sb.toString();
    }

    private String buildWechatText(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null) sb.append(title).append("\n\n");
        if (content != null) sb.append(content);
        return sb.toString();
    }
}
