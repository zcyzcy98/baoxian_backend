package com.insurance.agent.repository;

import com.insurance.agent.model.HotTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HotTopicRepository {

    private static final Logger log = LoggerFactory.getLogger(HotTopicRepository.class);
    private final DataSource dataSource;

    public HotTopicRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection getConn() throws SQLException {
        return dataSource.getConnection();
    }

    public int deleteByBatchDate(LocalDate batchDate) {
        String sql = "DELETE FROM hot_topics WHERE batch_date = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchDate);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[HotTopic] deleteByBatchDate 失败", e);
            return 0;
        }
    }

    public int deleteByBatchDateAndSource(LocalDate batchDate, String source) {
        String sql = "DELETE FROM hot_topics WHERE batch_date = ? AND source = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchDate);
            ps.setString(2, source);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[HotTopic] deleteByBatchDateAndSource 失败", e);
            return 0;
        }
    }

    public int countByBatchDate(LocalDate batchDate) {
        String sql = "SELECT COUNT(*) FROM hot_topics WHERE batch_date = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("[HotTopic] countByBatchDate 失败", e);
        }
        return 0;
    }

    // ---- 按批次日期查询 ----
    public List<HotTopic> findByBatchDate(LocalDate batchDate) {
        List<HotTopic> list = new ArrayList<>();
        String sql = "SELECT * FROM hot_topics WHERE batch_date = ? ORDER BY (heat_score + ai_score) DESC";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, batchDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("[HotTopic] findByBatchDate 查询失败", e);
        }
        return list;
    }

    // ---- 按摘要哈希查询（去重用） ----
    public HotTopic findByTitleHash(String hash) {
        String sql = "SELECT * FROM hot_topics WHERE title_hash = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            log.error("[HotTopic] findByTitleHash 查询失败", e);
        }
        return null;
    }

    // ---- 插入 ----
    public void insert(HotTopic topic) {
        String sql = "INSERT INTO hot_topics (title, title_hash, source, source_url, source_site, " +
                     "heat_score, ai_score, insurance_types, demographics, platforms, why_this_topic, " +
                     "source_category, batch_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (title_hash) DO UPDATE SET " +
                     "heat_score = EXCLUDED.heat_score, " +
                     "ai_score = EXCLUDED.ai_score, " +
                     "source_url = EXCLUDED.source_url, " +
                     "source_site = EXCLUDED.source_site, " +
                     "insurance_types = EXCLUDED.insurance_types, " +
                     "demographics = EXCLUDED.demographics, " +
                     "platforms = EXCLUDED.platforms, " +
                     "why_this_topic = EXCLUDED.why_this_topic, " +
                     "batch_date = EXCLUDED.batch_date, " +
                     "created_at = NOW()";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, topic.getTitle());
            ps.setString(2, topic.getTitleHash());
            ps.setString(3, topic.getSource());
            ps.setString(4, topic.getSourceUrl());
            ps.setString(5, topic.getSourceSite());
            ps.setInt(6, topic.getHeatScore());
            ps.setInt(7, topic.getAiScore());
            ps.setString(8, topic.getInsuranceTypesRaw());
            ps.setString(9, topic.getDemographicsRaw());
            ps.setString(10, topic.getPlatformsRaw());
            ps.setString(11, topic.getWhyThisTopic());
            ps.setString(12, topic.getSourceCategory());
            ps.setObject(13, topic.getBatchDate());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) topic.setId(keys.getLong(1));
            }
        } catch (SQLException e) {
            log.error("[HotTopic] 插入失败: {}", topic.getTitle(), e);
        }
    }

    // ---- 批量插入 ----
    public void batchInsert(List<HotTopic> topics) {
        String sql = "INSERT INTO hot_topics (title, title_hash, source, source_url, source_site, " +
                     "heat_score, ai_score, insurance_types, demographics, platforms, why_this_topic, " +
                     "source_category, batch_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (title_hash) DO UPDATE SET " +
                     "heat_score = EXCLUDED.heat_score, " +
                     "ai_score = EXCLUDED.ai_score, " +
                     "source_url = EXCLUDED.source_url, " +
                     "source_site = EXCLUDED.source_site, " +
                     "insurance_types = EXCLUDED.insurance_types, " +
                     "demographics = EXCLUDED.demographics, " +
                     "platforms = EXCLUDED.platforms, " +
                     "why_this_topic = EXCLUDED.why_this_topic, " +
                     "batch_date = EXCLUDED.batch_date, " +
                     "created_at = NOW()";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (HotTopic t : topics) {
                ps.setString(1, t.getTitle());
                ps.setString(2, t.getTitleHash());
                ps.setString(3, t.getSource());
                ps.setString(4, t.getSourceUrl());
                ps.setString(5, t.getSourceSite());
                ps.setInt(6, t.getHeatScore());
                ps.setInt(7, t.getAiScore());
                ps.setString(8, t.getInsuranceTypesRaw());
                ps.setString(9, t.getDemographicsRaw());
                ps.setString(10, t.getPlatformsRaw());
                ps.setString(11, t.getWhyThisTopic());
                ps.setString(12, t.getSourceCategory());
                ps.setObject(13, t.getBatchDate());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.error("[HotTopic] 批量插入失败, size={}", topics.size(), e);
        }
    }

    // ---- 推送记录 ----
    public void savePushLog(long userId, long topicId) {
        String sql = "INSERT INTO topic_push_log (user_id, topic_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, topicId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[HotTopic] 保存推送记录失败", e);
        }
    }

    /** 查询某用户已推送过的 topic_id 集合 */
    public List<Long> findPushedTopicIds(long userId) {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT topic_id FROM topic_push_log WHERE user_id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("topic_id"));
                }
            }
        } catch (SQLException e) {
            log.error("[HotTopic] 查询已推送记录失败", e);
        }
        return ids;
    }

    // ---- 用户行为记录 ----
    public void recordAction(long userId, long topicId, String action) {
        String sql = "INSERT INTO user_topic_action (user_id, topic_id, action) VALUES (?, ?, ?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, topicId);
            ps.setString(3, action);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("[HotTopic] 记录用户行为失败", e);
        }
    }

    // ---- 行映射 ----
    private HotTopic mapRow(ResultSet rs) throws SQLException {
        HotTopic t = new HotTopic();
        t.setId(rs.getLong("id"));
        t.setTitle(rs.getString("title"));
        t.setTitleHash(rs.getString("title_hash"));
        t.setSource(rs.getString("source"));
        t.setSourceUrl(rs.getString("source_url"));
        t.setSourceSite(rs.getString("source_site"));
        t.setHeatScore(rs.getInt("heat_score"));
        t.setAiScore(rs.getInt("ai_score"));
        t.setInsuranceTypesRaw(rs.getString("insurance_types"));
        t.setDemographicsRaw(rs.getString("demographics"));
        t.setPlatformsRaw(rs.getString("platforms"));
        t.setWhyThisTopic(rs.getString("why_this_topic"));
        t.setSourceCategory(rs.getString("source_category"));
        t.setBatchDate(rs.getObject("batch_date", LocalDate.class));
        t.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return t;
    }
}
