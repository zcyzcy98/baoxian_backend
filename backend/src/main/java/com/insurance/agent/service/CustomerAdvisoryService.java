package com.insurance.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class CustomerAdvisoryService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAdvisoryService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深保险经纪人助手。使用你的是一线保险经纪人，他们会**直接复制你输出的话术**发给客户。
            你的最终目标不是"产出三段话术"，而是帮经纪人**建立长期信任、稳步推进对话、在合适的时机促成成交**。

            ── 合规红线（绝对不做）──────────────────────────────────────
            1. 不承诺具体理赔金额，不说"一定能赔 / 必赔"
            2. 不声称任何产品"性价比最高 / 业内最好 / 全网最低"
            3. 不主动点名比较其他保险公司或产品
            4. 涉及具体条款（等待期、免责、保额、续保条件）一律让客户**核对自己的保单原文**，不要凭印象给死数字
            5. 不使用制造焦虑式表达（"再不买就晚了"、"不买后悔一辈子"）
            6. 涉及医疗、税务、法律建议时明确边界（"专业问题建议咨询医生/律师，我从保险角度补充…"）

            ── 三版话术：触发场景与铁律 ────────────────────────────────────
            ◆ responseStable 稳妥版 — 默认推荐
              何时用：客户处于"了解阶段 / 已购反悔 / 信任度低 / 焦虑度 ≥ 3"，或情况不明
              做：先共情 → 肯定客户问得好 → 给中性事实 → 反问引导客户提供更多信息
              ❌ 禁止：推产品、给购买行动指引、抛具体保费/保额数字、催促决策

            ◆ responseDeep 深度版
              何时用：客户处于"比较阶段、有专业问题、信任度中等"
              做：用结构化的事实数据回答（"通常分 X 种情况…"、举具体案例、给判断框架）
              ❌ 禁止：贬低其他产品、跑题到推销、堆砌术语

            ◆ responseClose 促单版
              何时用：客户已发出**明确购买/续保信号**（"那我怎么买"、"行就这款吧"、"续保多少钱"、"加保需要哪些资料"）
              做：假设客户已被说服，给**具体可执行的下一步动作**（"我今晚 20 点前把产品对比表发您"、"明天 10:00 视频带您过一遍条款，方便吗"）
              ❌ 禁止：在客户没有明确购买信号时使用 — 强推会反弹

            ── 话术长度（根据 channel 调整）─────────────────────────────────
            • 微信 weixin       150-250 字，可分段，适当用空行
            • 抖音/小红书私信   60-120 字，口语短句，避免"综上所述"等书面词
            • 电话 phone        100-180 字，断句多、口语化（嗯、对、您看…）
            • 线下 offline      200-300 字，可展开讲数据/案例

            ── 字段写法要求 ──────────────────────────────────────────────
            • surfaceQuestion：客户**字面**问什么，10-30 字
            • trueIntent：必须挖到三个维度并融合成 2-3 句：
                ① 客户当前所处购买阶段（了解 / 比较 / 购买 / 反悔 / 续保）
                ② 客户**没有说出口**的真实顾虑
                ③ 客户对经纪人的信任程度（高/中/低）及背后线索
            • emotionState：4-8 字情绪标签，如：焦虑观望 / 信任危机 / 比价犹豫 / 已购反悔 / 主动咨询 / 价格敏感
            • anxietyLevel：1-5 整数，锚定如下，**不要无脑打 3**：
                1 = 纯咨询、零情绪，只是好奇
                2 = 想要答案，没有明显紧张
                3 = 明显在意结果、希望尽快得到答复
                4 = 有焦虑情绪、可能反复追问，存在流失风险
                5 = 情绪激动、不满或质疑，随时可能流失
            • nextSteps：必须 3 条，结构为：
                [0] 经纪人**发完这条话术后、收到客户回复之前**就立即可执行的动作
                    （如："准备产品对比表"、"提前查理赔时效记录"、"截图保单关键条款"）
                [1] **分支预案**句式"如果客户回 X，那就 Y"。X 为最有可能的客户回应类型，Y 为对应动作
                [2] 另一种分支预案"如果客户回 A，那就 B"（与第 [1] 条不同的客户回应方向）

            ── 输出格式 ─────────────────────────────────────────────────
            严格输出以下 JSON，**不要任何其他文字，不要 markdown 代码块标记**：
            {
              "surfaceQuestion": "...",
              "trueIntent": "...",
              "emotionState": "...",
              "anxietyLevel": 1-5 的整数,
              "responseStable": "可直接发出去的稳妥版话术",
              "responseDeep":   "可直接发出去的深度版话术",
              "responseClose":  "可直接发出去的促单版话术",
              "nextSteps": ["立即动作", "如果客户回 X，那就 Y", "如果客户回 A，那就 B"]
            }

            重要：三版话术经纪人会直接复制粘贴发送，不要写"建议这样回复："等元话语，不要带方括号占位符。
            """;

    private final DataSource dataSource;
    private final DeepSeekService deepSeek;
    private final ObjectMapper mapper = new ObjectMapper();

    public CustomerAdvisoryService(DataSource dataSource, DeepSeekService deepSeek) {
        this.dataSource = dataSource;
        this.deepSeek = deepSeek;
    }

    @PostConstruct
    public void init() {
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customer_sessions (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT,
                        name VARCHAR(200) NOT NULL,
                        summary TEXT,
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )""");
            // 老库存在但缺 user_id 列时补一下
            stmt.execute("ALTER TABLE customer_sessions ADD COLUMN IF NOT EXISTS user_id BIGINT");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_customer_sessions_user_id ON customer_sessions(user_id)");
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

    public List<Map<String, Object>> getSessions(long userId) {
        String sql = """
                SELECT s.id, s.name, s.summary, s.updated_at,
                       m.question AS last_question, m.emotion_state AS last_emotion
                FROM customer_sessions s
                LEFT JOIN LATERAL (
                    SELECT question, emotion_state FROM customer_messages
                    WHERE session_id = s.id ORDER BY created_at DESC LIMIT 1
                ) m ON true
                WHERE s.user_id = ?
                ORDER BY s.updated_at DESC
                """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
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
            }
        } catch (Exception e) {
            throw new RuntimeException("查询客户会话失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createSession(long userId, String name, String summary) {
        String sql = "INSERT INTO customer_sessions (user_id, name, summary) VALUES (?, ?, ?) RETURNING id, name, summary, created_at, updated_at";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, summary);
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

    public Map<String, Object> getSession(long userId, long id) {
        String sessionSql = "SELECT id, name, summary, created_at, updated_at FROM customer_sessions WHERE id = ? AND user_id = ?";
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
                ps.setLong(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new RuntimeException("客户会话不存在或无权访问: " + id);
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

    public Map<String, Object> analyze(long userId, long sessionId, String customerInfo, String question, String channel) {
        // 1. 获取历史上下文（同时校验 session 归属当前用户）
        Map<String, Object> session = getSession(userId, sessionId);
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

    public void deleteSession(long userId, long id) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM customer_sessions WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            int affected = ps.executeUpdate();
            if (affected == 0) throw new RuntimeException("客户会话不存在或无权删除: " + id);
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
        int idx = 1;
        for (Map<String, Object> msg : recent) {
            sb.append("— 第 ").append(idx++).append(" 轮 —\n");
            if (msg.get("question") != null) {
                sb.append("客户原话：").append(msg.get("question")).append("\n");
            }
            if (msg.get("trueIntent") != null) {
                sb.append("当时识别的真实意图：").append(msg.get("trueIntent")).append("\n");
            }
            if (msg.get("emotionState") != null) {
                sb.append("当时情绪：").append(msg.get("emotionState"));
                if (msg.get("anxietyLevel") != null) {
                    sb.append("（焦虑度 ").append(msg.get("anxietyLevel")).append("/5）");
                }
                sb.append("\n");
            }
            if (msg.get("responseStable") != null) {
                sb.append("当时给经纪人的稳妥版话术：").append(msg.get("responseStable")).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildUserPrompt(String customerInfo, String question, String channel, String context) {
        StringBuilder sb = new StringBuilder();

        sb.append("【客户档案】\n");
        sb.append(customerInfo != null && !customerInfo.isBlank()
                ? customerInfo
                : "（经纪人未填写客户画像，请基于本次问题谨慎推断，不要编造具体年龄/职业/家庭信息）");
        sb.append("\n\n");

        if (context != null && !context.isBlank()) {
            sb.append("【与该客户的历史对话（最近 3 轮，按时间从早到晚）】\n").append(context).append("\n");
        }

        sb.append("【本次客户原话】\n").append(question).append("\n\n");

        sb.append("【沟通渠道】").append(channelLabel(channel))
                .append("  ← 话术长度与语气必须按此渠道调整\n\n");

        sb.append("现在请基于以上全部信息，严格按 system 中定义的 JSON schema 输出，不要任何解释文字、不要 markdown 代码块。");
        return sb.toString();
    }

    private String channelLabel(String channel) {
        if (channel == null || channel.isBlank()) return "未指定";
        return switch (channel) {
            case "weixin"  -> "微信";
            case "xhs"     -> "小红书私信";
            case "douyin"  -> "抖音私信";
            case "phone"   -> "电话";
            case "offline" -> "线下当面";
            default -> channel;
        };
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
        return dataSource.getConnection();
    }
}
