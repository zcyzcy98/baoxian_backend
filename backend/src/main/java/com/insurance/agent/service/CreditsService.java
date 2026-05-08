package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class CreditsService {

    private static final Logger log = LoggerFactory.getLogger(CreditsService.class);
    private static final long DEFAULT_USER_ID = 1L;

    private static final Map<String, String> TYPE_LABEL = Map.of(
            "xhs_title",   "小红书标题",
            "xhs_post",    "小红书正文",
            "gzh_title",   "公众号标题",
            "gzh_article", "公众号长文",
            "video_script","视频脚本",
            "image",       "AI 配图"
    );

    private static final Map<String, Integer> TYPE_COST = Map.of(
            "xhs_title",    2,
            "xhs_post",     8,
            "gzh_title",    2,
            "gzh_article", 20,
            "video_script", 10,
            "image",        15
    );

    private static final Map<String, String> TYPE_PLATFORM = Map.of(
            "xhs_title",   "xhs",
            "xhs_post",    "xhs",
            "gzh_title",   "gzh",
            "gzh_article", "gzh",
            "video_script","douyin"
    );

    private final DataSource dataSource;

    public CreditsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 汇总数据：各类型数量、总消耗 */
    public Map<String, Object> getSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            // 生成内容统计
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT type, COUNT(*) as cnt
                    FROM generated_contents WHERE user_id = ?
                    GROUP BY type
                    """)) {
                ps.setLong(1, DEFAULT_USER_ID);
                Map<String, Long> typeCounts = new LinkedHashMap<>();
                long totalCost = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        long cnt = rs.getLong("cnt");
                        typeCounts.put(type, cnt);
                        totalCost += cnt * TYPE_COST.getOrDefault(type, 5);
                    }
                }
                result.put("typeCounts", typeCounts);
                result.put("totalCost", totalCost);
            }
            // 客户答疑统计
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*) as cnt FROM customer_messages m
                    JOIN customer_sessions s ON m.session_id = s.id
                    WHERE s.user_id = ?
                    """)) {
                ps.setLong(1, DEFAULT_USER_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    result.put("totalQa", rs.next() ? rs.getLong("cnt") : 0);
                }
            }
        } catch (Exception e) {
            log.warn("[Credits] getSummary 失败: {}", e.getMessage());
        }
        return result;
    }

    /** 消耗记录列表（生成内容 + 客户答疑合并，按时间倒序） */
    public List<Map<String, Object>> getRecords(String filter, int page, int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            if ("all".equals(filter) || "create".equals(filter)) {
                list.addAll(getCreateRecords(c, size));
            }
            if ("all".equals(filter) || "qa".equals(filter)) {
                list.addAll(getQaRecords(c, size));
            }
        } catch (Exception e) {
            log.warn("[Credits] getRecords 失败: {}", e.getMessage());
        }

        // 合并后按时间倒序，截取分页
        list.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))));
        int from = page * size;
        if (from >= list.size()) return List.of();
        return list.subList(from, Math.min(from + size, list.size()));
    }

    private List<Map<String, Object>> getCreateRecords(Connection c, int limit) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id, type, title, model, created_at
                FROM generated_contents WHERE user_id = ?
                ORDER BY created_at DESC LIMIT ?
                """)) {
            ps.setLong(1, DEFAULT_USER_ID);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", "c" + rs.getLong("id"));
                    row.put("kind", "create");
                    row.put("platform", TYPE_PLATFORM.get(type));
                    row.put("title", TYPE_LABEL.getOrDefault(type, type));
                    row.put("detail", rs.getString("title") != null ? rs.getString("title") : "");
                    row.put("cost", -TYPE_COST.getOrDefault(type, 5));
                    row.put("time", rs.getString("created_at"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    private List<Map<String, Object>> getQaRecords(Connection c, int limit) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT m.id, m.question, m.created_at
                FROM customer_messages m
                JOIN customer_sessions s ON m.session_id = s.id
                WHERE s.user_id = ?
                ORDER BY m.created_at DESC LIMIT ?
                """)) {
            ps.setLong(1, DEFAULT_USER_ID);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String q = rs.getString("question");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", "q" + rs.getLong("id"));
                    row.put("kind", "qa");
                    row.put("platform", null);
                    row.put("title", "客户答疑");
                    row.put("detail", q != null && q.length() > 40 ? q.substring(0, 40) + "…" : q);
                    row.put("cost", -3);
                    row.put("time", rs.getString("created_at"));
                    list.add(row);
                }
            }
        }
        return list;
    }
}
