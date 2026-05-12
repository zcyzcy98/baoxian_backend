package com.insurance.agent.service;

import com.insurance.agent.exception.InsufficientCreditsException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class CreditsService {

    private static final Logger log = LoggerFactory.getLogger(CreditsService.class);
    private static final int INITIAL_CREDITS = 8000;

    // Action codes → display labels
    static final Map<String, String> ACTION_LABEL = Map.ofEntries(
        Map.entry("xhs_text",      "小红书文字创作"),
        Map.entry("xhs_images",    "小红书配图生成"),
        Map.entry("gzh_text",      "公众号长文创作"),
        Map.entry("gzh_images",    "公众号封面+配图"),
        Map.entry("video_script",  "口播视频脚本"),
        Map.entry("video_render",  "口播视频成片"),
        Map.entry("drama_script",  "剧情视频脚本"),
        Map.entry("drama_render",  "剧情视频成片"),
        Map.entry("xhs_rewrite",   "小红书仿写"),
        Map.entry("gzh_rewrite",   "公众号仿写"),
        Map.entry("video_rip",     "视频仿做分析"),
        Map.entry("viral_xhs",     "拆解小红书爆款"),
        Map.entry("viral_douyin",  "拆解抖音爆款"),
        Map.entry("advisory",      "客户答疑"),
        Map.entry("topic_refresh", "手动刷新选题"),
        Map.entry("topup",         "积分充值"),
        Map.entry("xhs_title",    "小红书标题生成"),
        Map.entry("gzh_title",    "公众号标题生成"),
        Map.entry("video_title",  "视频标题文案"),
        Map.entry("video_cover",  "视频封面图")
    );

    // Action codes → platform tags for display
    static final Map<String, String> ACTION_PLATFORM = Map.ofEntries(
        Map.entry("xhs_text",     "xhs"),
        Map.entry("xhs_images",   "xhs"),
        Map.entry("gzh_text",     "gzh"),
        Map.entry("gzh_images",   "gzh"),
        Map.entry("video_script", "video"),
        Map.entry("video_render", "video"),
        Map.entry("drama_script", "video"),
        Map.entry("drama_render", "video"),
        Map.entry("xhs_rewrite",  "xhs"),
        Map.entry("gzh_rewrite",  "gzh"),
        Map.entry("video_rip",    "video"),
        Map.entry("viral_xhs",    "xhs"),
        Map.entry("viral_douyin", "douyin"),
        Map.entry("xhs_title",   "xhs"),
        Map.entry("gzh_title",   "gzh"),
        Map.entry("video_title", "video"),
        Map.entry("video_cover", "video")
    );

    private final DataSource dataSource;

    public CreditsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initSchema() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            // Add credits column to users table (existing users get 8000 as default)
            st.execute("""
                ALTER TABLE users ADD COLUMN IF NOT EXISTS credits INTEGER NOT NULL DEFAULT 8000
                """);
            // Create credit transactions table
            st.execute("""
                CREATE TABLE IF NOT EXISTS credit_transactions (
                  id           BIGSERIAL PRIMARY KEY,
                  user_id      BIGINT NOT NULL,
                  delta        INTEGER NOT NULL,
                  balance_after INTEGER NOT NULL,
                  action       VARCHAR(100) NOT NULL,
                  description  VARCHAR(300),
                  created_at   TIMESTAMPTZ DEFAULT NOW()
                )
                """);
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_credit_tx_user
                ON credit_transactions(user_id, created_at DESC)
                """);
            // 扩展 generated_contents.model 字段长度
            st.execute("""
                ALTER TABLE generated_contents
                ALTER COLUMN model TYPE VARCHAR(300)
                """);
            // 添加 content_id 列，关联 generated_contents
            st.execute("""
                ALTER TABLE credit_transactions
                ADD COLUMN IF NOT EXISTS content_id BIGINT
                """);
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_credit_tx_content
                ON credit_transactions(content_id)
                """);
            // 迁移：删除 hot_topics.is_picked（改用 user_topic_action 按用户记录）
            st.execute("""
                ALTER TABLE hot_topics DROP COLUMN IF EXISTS is_picked
                """);
            log.info("[Credits] schema ready");
        } catch (Exception e) {
            log.warn("[Credits] schema init: {}", e.getMessage());
        }
    }

    // ── Balance ────────────────────────────────────────────────────────────

    public int getBalance(long userId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(credits, ?) FROM users WHERE id = ?")) {
            ps.setInt(1, INITIAL_CREDITS);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : INITIAL_CREDITS;
            }
        } catch (SQLException e) {
            log.error("[Credits] getBalance failed for userId={}: {}", userId, e.getMessage());
            return 0;
        }
    }

    // ── Deduct ─────────────────────────────────────────────────────────────

    /**
     * Atomically deducts credits. Throws InsufficientCreditsException if balance < amount.
     * Records the transaction.
     */
    public void deduct(long userId, int amount, String action, String description) {
        deduct(userId, amount, action, description, null);
    }

    /**
     * Deduct with content association.
     */
    public void deduct(long userId, int amount, String action, String description, Long contentId) {
        if (amount <= 0) return;
        Connection c = null;
        try {
            c = dataSource.getConnection();
            c.setAutoCommit(false);

            // Atomic deduction with balance check
            int newBalance;
            try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ? RETURNING credits")) {
                ps.setInt(1, amount);
                ps.setLong(2, userId);
                ps.setInt(3, amount);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        int current = fetchBalance(c, userId);
                        c.rollback();
                        throw new InsufficientCreditsException(current, amount);
                    }
                    newBalance = rs.getInt(1);
                }
            }

            // Record transaction
            if (contentId != null) {
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO credit_transactions(user_id, delta, balance_after, action, description, content_id) VALUES(?,?,?,?,?,?)")) {
                    ps.setLong(1, userId);
                    ps.setInt(2, -amount);
                    ps.setInt(3, newBalance);
                    ps.setString(4, action);
                    ps.setString(5, description);
                    ps.setLong(6, contentId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO credit_transactions(user_id, delta, balance_after, action, description) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId);
                    ps.setInt(2, -amount);
                    ps.setInt(3, newBalance);
                    ps.setString(4, action);
                    ps.setString(5, description);
                    ps.executeUpdate();
                }
            }

            c.commit();
            log.info("[Credits] userId={} action={} -{}分 余{}分", userId, action, amount, newBalance);

        } catch (InsufficientCreditsException e) {
            throw e;
        } catch (SQLException e) {
            try { if (c != null) c.rollback(); } catch (SQLException ignored) {}
            log.error("[Credits] deduct failed userId={} action={}: {}", userId, action, e.getMessage(), e);
            throw new RuntimeException("积分扣除失败", e);
        } finally {
            try { if (c != null) { c.setAutoCommit(true); c.close(); } } catch (SQLException ignored) {}
        }
    }

    // ── Summary ────────────────────────────────────────────────────────────

    public Map<String, Object> getSummary(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", getBalance(userId));
        result.put("total", INITIAL_CREDITS);

        try (Connection c = dataSource.getConnection()) {
            // Total consumed (sum of negative deltas)
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(-delta), 0) FROM credit_transactions WHERE user_id = ? AND delta < 0")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    result.put("totalConsumed", rs.next() ? rs.getLong(1) : 0);
                }
            }
            // Count by action
            try (PreparedStatement ps = c.prepareStatement(
                "SELECT action, COUNT(*) FROM credit_transactions WHERE user_id = ? AND delta < 0 GROUP BY action")) {
                ps.setLong(1, userId);
                Map<String, Long> counts = new LinkedHashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) counts.put(rs.getString(1), rs.getLong(2));
                }
                result.put("actionCounts", counts);
            }
        } catch (Exception e) {
            log.warn("[Credits] getSummary failed: {}", e.getMessage());
        }
        return result;
    }

    // ── Records ────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getRecords(long userId, String filter, int page, int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        String where = switch (filter) {
            case "create" -> "AND action NOT IN ('advisory','topic_refresh','topup')";
            case "qa"     -> "AND action = 'advisory'";
            default       -> "";
        };
        String sql = "SELECT id, delta, balance_after, action, description, content_id, created_at " +
                     "FROM credit_transactions WHERE user_id = ? " + where +
                     " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, size);
            ps.setInt(3, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String action = rs.getString("action");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id",       rs.getLong("id"));
                    row.put("kind",     "topup".equals(action) ? "topup" :
                                        "advisory".equals(action) ? "qa" : "create");
                    row.put("platform", ACTION_PLATFORM.get(action));
                    row.put("title",    ACTION_LABEL.getOrDefault(action, action));
                    row.put("detail",   rs.getString("description"));
                    row.put("cost",     rs.getInt("delta"));
                    row.put("time",     rs.getString("created_at"));
                    Long ctId = rs.getLong("content_id");
                    if (!rs.wasNull()) row.put("contentId", ctId);
                    list.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("[Credits] getRecords failed: {}", e.getMessage());
        }
        return list;
    }

    // ── Content Lookup ────────────────────────────────────────────────────

    /**
     * Query generated content linked to a credit transaction.
     */
    public Map<String, Object> getRecordContent(long userId, long recordId) {
        String sql = """
            SELECT gc.id, gc.type, gc.title, gc.content, gc.image_urls,
                   gc.video_url, gc.cover_url, gc.model, gc.created_at
            FROM credit_transactions ct
            JOIN generated_contents gc ON ct.content_id = gc.id
            WHERE ct.id = ? AND ct.user_id = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("type", rs.getString("type"));
                    row.put("title", rs.getString("title"));
                    row.put("content", rs.getString("content"));
                    Array arr = rs.getArray("image_urls");
                    row.put("imageUrls", arr != null ? Arrays.asList((String[]) arr.getArray()) : List.of());
                    row.put("videoUrl", rs.getString("video_url"));
                    row.put("coverUrl", rs.getString("cover_url"));
                    row.put("model", rs.getString("model"));
                    row.put("createdAt", rs.getString("created_at"));
                    return row;
                }
            }
        } catch (Exception e) {
            log.warn("[Credits] getRecordContent failed recordId={}: {}", recordId, e.getMessage());
        }
        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private int fetchBalance(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT COALESCE(credits, ?) FROM users WHERE id = ?")) {
            ps.setInt(1, INITIAL_CREDITS);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
