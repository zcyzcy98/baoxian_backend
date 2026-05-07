package com.insurance.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class CustomerAdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAdvisoryService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深保险顾问助手，帮助保险经纪人分析客户意图并制定应对策略。
            请根据输入信息，严格输出以下 JSON 格式，不要输出任何其他文字：
            {
              "surfaceQuestion": "客户表面问题（一句话）",
              "trueIntent": "客户真实意图和心理动机（2-3句，要透彻）",
              "emotionState": "情绪状态标签，如：焦虑观望/信任危机/比价犹豫/已购反悔/主动咨询",
              "anxietyLevel": 焦虑程度1-5的整数,
              "responseStable": "稳妥版：先建立信任不急推销，含1-2句可直接用的话术（150-200字）",
              "responseDeep": "深度版：展示专业知识精准解答疑虑，含具体数据或案例（150-200字）",
              "responseClose": "促单版：引导成交或续费，含具体行动指引（150-200字）",
              "nextSteps": ["第一步具体行动", "第二步", "第三步"]
            }
            """;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;
    @Value("${spring.datasource.username:}")
    private String username;
    @Value("${spring.datasource.password:}")
    private String password;

    private final DeepSeekService deepSeek;
    private final ObjectMapper mapper = new ObjectMapper();

    public CustomerAdvisoryService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    @PostConstruct
    public void init() {
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customer_sessions (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        summary TEXT,
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customer_messages (
                        id BIGSERIAL PRIMARY KEY,
                        session_id BIGINT NOT NULL REFERENCES customer_sessions(id) ON DELETE CASCADE,
                        customer_info TEXT,
                        question TEXT NOT NULL,
                        channel VARCHAR(50),
                        surface_question TEXT,
                        true_intent TEXT,
                        emotion_state VARCHAR(100),
                        anxiety_level INT,
                        response_stable TEXT,
                        response_deep TEXT,
                        response_close TEXT,
                        next_steps TEXT,
                        created_at TIMESTAMP DEFAULT NOW()
                    )""");
        } catch (Exception e) {
            log.warn("初始化客户答疑表失败，服务继续启动: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getSessions() {
        String sql = """
                SELECT s.id, s.name, s.summary, s.updated_at,
                       m.question AS last_question, m.emotion_state AS last_emotion
                FROM customer_sessions s
                LEFT JOIN LATERAL (
                    SELECT question, emotion_state FROM customer_messages
                    WHERE session_id = s.id ORDER BY created_at DESC LIMIT 1
                ) m ON true
                ORDER BY s.updated_at DESC
                """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> list = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("name", rs.getString("name"));
                row.put("summary", rs.getString("summary"));
                row.put("updatedAt", rs.getString("updated_at"));
                row.put("lastQuestion", rs.getString("last_question"));
                row.put("lastEmotion", rs.getString("last_emotion"));
                list.add(row);
            }
            return list;
        } catch (Exception e) {
            throw new RuntimeException("查询客户会话失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createSession(String name, String summary) {
        String sql = "INSERT INTO customer_sessions (name, summary) VALUES (?, ?) RETURNING id, name, summary, created_at, updated_at";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, summary);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("summary", rs.getString("summary"));
                    row.put("updatedAt", rs.getString("updated_at"));
                    row.put("messages", List.of());
                    return row;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("创建客户会话失败: " + e.getMessage(), e);
        }
        throw new RuntimeException("创建客户会话失败");
    }

    public Map<String, Object> getSession(long id) {
        String sessionSql = "SELECT id, name, summary, created_at, updated_at FROM customer_sessions WHERE id = ?";
        String msgSql = """
                SELECT id, customer_info, question, channel,
                       surface_question, true_intent, emotion_state, anxiety_level,
                       response_stable, response_deep, response_close, next_steps, created_at
                FROM customer_messages WHERE session_id = ? ORDER BY created_at ASC
                """;
        try (Connection conn = openConnection()) {
            Map<String, Object> session = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sessionSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new RuntimeException("客户会话不存在: " + id);
                    session.put("id", rs.getLong("id"));
                    session.put("name", rs.getString("name"));
                    session.put("summary", rs.getString("summary"));
                    session.put("updatedAt", rs.getString("updated_at"));
                }
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(msgSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> msg = new LinkedHashMap<>();
                        msg.put("id", rs.getLong("id"));
                        msg.put("customerInfo", rs.getString("customer_info"));
                        msg.put("question", rs.getString("question"));
                        msg.put("channel", rs.getString("channel"));
                        msg.put("surfaceQuestion", rs.getString("surface_question"));
                        msg.put("trueIntent", rs.getString("true_intent"));
                        msg.put("emotionState", rs.getString("emotion_state"));
                        msg.put("anxietyLevel", rs.getObject("anxiety_level"));
                        msg.put("responseStable", rs.getString("response_stable"));
                        msg.put("responseDeep", rs.getString("response_deep"));
                        msg.put("responseClose", rs.getString("response_close"));
                        String nextStepsJson = rs.getString("next_steps");
                        msg.put("nextSteps", parseNextSteps(nextStepsJson));
                        msg.put("createdAt", rs.getString("created_at"));
                        messages.add(msg);
                    }
                }
            }
            session.put("messages", messages);
            return session;
        } catch (Exception e) {
            throw new RuntimeException("读取客户会话失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> analyze(long sessionId, String customerInfo, String question, String channel) {
        // 1. 获取历史上下文
        Map<String, Object> session = getSession(sessionId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) session.get("messages");
        String context = buildContext(history);

        // 2. 构建 prompt
        String userPrompt = buildUserPrompt(customerInfo, question, channel, context);
        log.info("[Advisory] 开始分析 sessionId={} channel={}", sessionId, channel);

        // 3. 调用 DeepSeek
        String rawResponse = deepSeek.chat(SYSTEM_PROMPT, userPrompt, "chat");

        // 4. 解析 JSON
        Map<String, Object> analysis = parseAnalysisJson(rawResponse);

        // 5. 存库
        long msgId = saveMessage(sessionId, customerInfo, question, channel, analysis);
        updateSessionTimestamp(sessionId);

        log.info("[Advisory] 分析完成 sessionId={} msgId={} emotion={}",
                sessionId, msgId, analysis.get("emotionState"));

        // 6. 返回完整结果
        Map<String, Object> result = new LinkedHashMap<>(analysis);
        result.put("id", msgId);
        result.put("sessionId", sessionId);
        result.put("customerInfo", customerInfo);
        result.put("question", question);
        result.put("channel", channel);
        return result;
    }

    public void deleteSession(long id) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM customer_sessions WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("删除客户会话失败: " + e.getMessage(), e);
        }
    }

    // ── private helpers ─────────────────────────────────────────────────────

    private String buildContext(List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) return "";
        int start = Math.max(0, history.size() - 3);
        List<Map<String, Object>> recent = history.subList(start, history.size());
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : recent) {
            sb.append("---\n");
            if (msg.get("customerInfo") != null) sb.append("客户情况: ").append(msg.get("customerInfo")).append("\n");
            if (msg.get("question") != null) sb.append("客户问题: ").append(msg.get("question")).append("\n");
            if (msg.get("trueIntent") != null) sb.append("当时分析意图: ").append(msg.get("trueIntent")).append("\n");
        }
        return sb.toString();
    }

    private String buildUserPrompt(String customerInfo, String question, String channel, String context) {
        StringBuilder sb = new StringBuilder();
        if (context != null && !context.isBlank()) {
            sb.append("【与该客户的历史沟通记录（最近3次）】\n").append(context).append("\n");
        }
        sb.append("【本次客户基本情况】\n").append(customerInfo).append("\n\n");
        sb.append("【客户提出的问题】\n").append(question).append("\n\n");
        if (channel != null && !channel.isBlank()) sb.append("【沟通渠道】").append(channel).append("\n\n");
        sb.append("请输出 JSON 分析：");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAnalysisJson(String raw) {
        try {
            String text = raw == null ? "" : raw.trim();
            text = text.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
            text = text.replaceAll("(?s)^```\\s*", "").trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) text = text.substring(start, end + 1);
            return mapper.readValue(text, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Advisory] JSON 解析失败，回退到原始文本: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("trueIntent", raw);
            fallback.put("responseStable", raw);
            fallback.put("emotionState", "分析中");
            fallback.put("anxietyLevel", 3);
            fallback.put("nextSteps", List.of("请查看上方原始分析内容"));
            return fallback;
        }
    }

    private long saveMessage(long sessionId, String customerInfo, String question,
                             String channel, Map<String, Object> a) {
        String sql = """
                INSERT INTO customer_messages
                (session_id, customer_info, question, channel,
                 surface_question, true_intent, emotion_state, anxiety_level,
                 response_stable, response_deep, response_close, next_steps)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id
                """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setString(2, customerInfo);
            ps.setString(3, question);
            ps.setString(4, channel);
            ps.setString(5, str(a, "surfaceQuestion"));
            ps.setString(6, str(a, "trueIntent"));
            ps.setString(7, str(a, "emotionState"));
            Object level = a.get("anxietyLevel");
            if (level instanceof Number n) ps.setInt(8, n.intValue());
            else ps.setNull(8, Types.INTEGER);
            ps.setString(9, str(a, "responseStable"));
            ps.setString(10, str(a, "responseDeep"));
            ps.setString(11, str(a, "responseClose"));
            Object steps = a.get("nextSteps");
            ps.setString(12, steps != null ? mapper.writeValueAsString(steps) : null);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        } catch (Exception e) {
            throw new RuntimeException("保存分析记录失败: " + e.getMessage(), e);
        }
        return -1;
    }

    private void updateSessionTimestamp(long sessionId) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE customer_sessions SET updated_at = NOW() WHERE id = ?")) {
            ps.setLong(1, sessionId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("更新会话时间戳失败: {}", e.getMessage());
        }
    }

    private List<String> parseNextSteps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(json);
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
