package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class GeneratedContentService {

    private static final Logger log = LoggerFactory.getLogger(GeneratedContentService.class);
    private static final long DEFAULT_USER_ID = 1L;

    private final DataSource dataSource;

    public GeneratedContentService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 保存一条生成记录，失败时只打日志不抛异常，不影响主流程。
     *
     * @return 新记录的 id，失败时返回 null
     */
    public Long save(String type, String title, String content,
                     String imageUrl, String videoUrl, String coverUrl, String model) {
        log.debug("[GeneratedContent] 准备保存 type={} title={} hasImage={} hasVideo={} model={}",
                type, title, imageUrl != null, videoUrl != null, model);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO generated_contents
                  (user_id, type, title, content, image_urls, video_url, cover_url, model)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            ps.setLong(1, DEFAULT_USER_ID);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setArray(5, imageUrl != null
                    ? c.createArrayOf("text", new String[]{imageUrl})
                    : c.createArrayOf("text", new String[0]));
            ps.setString(6, videoUrl);
            ps.setString(7, coverUrl);
            ps.setString(8, model);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    log.info("[GeneratedContent] 保存成功 id={} type={} title={}", id, type, title);
                    return id;
                }
            }
        } catch (Exception e) {
            log.warn("[GeneratedContent] 保存记录失败（不影响主流程）type={} error={}", type, e.getMessage(), e);
        }
        return null;
    }

    /** 查询历史记录，按时间倒序，默认最近 50 条 */
    public List<Map<String, Object>> list(String type, int limit) {
        String sql = type != null
                ? "SELECT id, type, title, content, image_urls, video_url, cover_url, model, created_at FROM generated_contents WHERE user_id = ? AND type = ? ORDER BY created_at DESC LIMIT ?"
                : "SELECT id, type, title, content, image_urls, video_url, cover_url, model, created_at FROM generated_contents WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, DEFAULT_USER_ID);
            if (type != null) {
                ps.setString(2, type);
                ps.setInt(3, limit);
            } else {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
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
                    result.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("[GeneratedContent] 查询历史失败: {}", e.getMessage());
        }
        return result;
    }
}
