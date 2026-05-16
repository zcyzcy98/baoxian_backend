package com.insurance.agent.service;

import com.insurance.agent.dto.ProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private final DataSource dataSource;

    public UserProfileService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Connection getConn() throws SQLException {
        return dataSource.getConnection();
    }

    // ---- 读取 ----

    public List<Long> getAllUserIds() {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT id FROM user_profile ORDER BY id";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            log.error("[Profile] 查询所有用户ID失败", e);
        }
        return ids;
    }

    public ProfileDto getProfile(long userId) {
        ProfileDto dto = new ProfileDto();
        dto.setId(userId);

        String sql = "SELECT name, phone, region, years, avatar_url, primary_products, " +
                     "target_audiences, style, bio, tags FROM user_profile WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setName(rs.getString("name"));
                    dto.setPhone(rs.getString("phone"));
                    dto.setRegion(rs.getString("region"));
                    dto.setYears(rs.getString("years"));
                    dto.setAvatarUrl(rs.getString("avatar_url"));
                    dto.setStyle(rs.getString("style"));
                    dto.setBio(rs.getString("bio"));
                    dto.setTags(rs.getString("tags"));
                    dto.setPrimaryProducts(arrayToList(rs.getArray("primary_products")));
                    dto.setTargetAudiences(arrayToList(rs.getArray("target_audiences")));
                }
            }
        } catch (SQLException e) {
            log.error("[Profile] 读取失败", e);
        }

        // 读取 users 表的 created_at
        String userSql = "SELECT created_at FROM users WHERE id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(userSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        dto.setCreatedAt(ts.toInstant().toString());
                    }
                }
            }
        } catch (SQLException e) {
            log.error("[Profile] 读取 users.created_at 失败", e);
        }

        dto.setPlatforms(getPlatforms(userId));
        return dto;
    }

    // ---- 保存 ----
    public ProfileDto saveProfile(long userId, ProfileDto req) {
        String sql = """
                INSERT INTO user_profile
                    (id, name, phone, region, years, avatar_url, primary_products, target_audiences, style, bio, tags, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (id) DO UPDATE SET
                    name             = EXCLUDED.name,
                    phone            = EXCLUDED.phone,
                    region           = EXCLUDED.region,
                    years            = EXCLUDED.years,
                    avatar_url       = EXCLUDED.avatar_url,
                    primary_products = EXCLUDED.primary_products,
                    target_audiences = EXCLUDED.target_audiences,
                    style            = EXCLUDED.style,
                    bio              = EXCLUDED.bio,
                    tags             = EXCLUDED.tags,
                    updated_at       = NOW()
                """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, req.getName());
            ps.setString(3, req.getPhone());
            ps.setString(4, req.getRegion());
            ps.setString(5, req.getYears());
            ps.setString(6, req.getAvatarUrl());
            ps.setArray(7, listToArray(c, req.getPrimaryProducts()));
            ps.setArray(8, listToArray(c, req.getTargetAudiences()));
            ps.setString(9, req.getStyle());
            ps.setString(10, req.getBio());
            ps.setString(11, req.getTags());
            ps.executeUpdate();
            log.info("[Profile] 保存成功 userId={}", userId);
        } catch (SQLException e) {
            log.error("[Profile] 保存失败", e);
            throw new RuntimeException("保存个人信息失败", e);
        }
        return getProfile(userId);
    }

    // ---- 平台绑定列表 ----
    public List<ProfileDto.PlatformBinding> getPlatforms(long userId) {
        List<ProfileDto.PlatformBinding> list = new ArrayList<>();
        String sql = "SELECT platform, account_name, account_id FROM platform_binding WHERE user_id = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProfileDto.PlatformBinding b = new ProfileDto.PlatformBinding();
                    b.setPlatform(rs.getString("platform"));
                    b.setAccountName(rs.getString("account_name"));
                    b.setAccountId(rs.getString("account_id"));
                    list.add(b);
                }
            }
        } catch (SQLException e) {
            log.error("[Platform] 读取失败", e);
        }
        return list;
    }

    // ---- 绑定平台 ----
    public void bindPlatform(long userId, ProfileDto.PlatformBinding req) {
        String sql = """
                INSERT INTO platform_binding (user_id, platform, account_name, account_id, bound_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (user_id, platform) DO UPDATE SET
                    account_name = EXCLUDED.account_name,
                    account_id   = EXCLUDED.account_id,
                    bound_at     = NOW()
                """;
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, req.getPlatform());
            ps.setString(3, req.getAccountName());
            ps.setString(4, req.getAccountId());
            ps.executeUpdate();
            log.info("[Platform] 绑定成功: {}", req.getPlatform());
        } catch (SQLException e) {
            log.error("[Platform] 绑定失败", e);
            throw new RuntimeException("绑定平台失败", e);
        }
    }

    // ---- 解绑平台 ----
    public void unbindPlatform(long userId, String platform) {
        String sql = "DELETE FROM platform_binding WHERE user_id = ? AND platform = ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, platform);
            ps.executeUpdate();
            log.info("[Platform] 解绑成功: {}", platform);
        } catch (SQLException e) {
            log.error("[Platform] 解绑失败", e);
            throw new RuntimeException("解绑平台失败", e);
        }
    }

    // ---- 工具方法 ----
    private List<String> arrayToList(Array arr) throws SQLException {
        if (arr == null) return new ArrayList<>();
        String[] raw = (String[]) arr.getArray();
        return new ArrayList<>(Arrays.asList(raw));
    }

    private Array listToArray(Connection c, List<String> list) throws SQLException {
        if (list == null || list.isEmpty()) {
            return c.createArrayOf("text", new String[0]);
        }
        return c.createArrayOf("text", list.toArray(new String[0]));
    }
}
