package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final DataSource dataSource;

    public AuthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String sendCode(String phone) {
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        log.debug("[Auth] sendCode 开始 phone={}", phone);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO sms_codes (phone, code, expires_at)
                VALUES (?, ?, NOW() + INTERVAL '5 minutes')
                ON CONFLICT (phone) DO UPDATE
                  SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at
                """)) {
            ps.setString(1, phone);
            ps.setString(2, code);
            ps.executeUpdate();
            log.info("[Auth] 验证码已存库 phone={} code={}", phone, code);
        } catch (SQLException e) {
            log.error("[Auth] 存储验证码失败 phone={} error={}", phone, e.getMessage(), e);
            throw new RuntimeException("发送验证码失败", e);
        }
        log.info("[MOCK SMS] phone={} code={}", phone, code);
        return code;
    }

    public String verifyCode(String phone, String code) {
        log.debug("[Auth] verifyCode 开始 phone={}", phone);
        try (Connection c = dataSource.getConnection()) {
            // 1. 查验证码
            String expected;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT code FROM sms_codes WHERE phone = ? AND expires_at > NOW()")) {
                ps.setString(1, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("[Auth] 验证码不存在或已过期 phone={}", phone);
                        throw new IllegalArgumentException("验证码已过期，请重新获取");
                    }
                    expected = rs.getString("code");
                }
            }
            if (!expected.equals(code)) {
                log.warn("[Auth] 验证码错误 phone={} input={}", phone, code);
                throw new IllegalArgumentException("验证码错误");
            }
            log.debug("[Auth] 验证码校验通过 phone={}", phone);

            // 2. 删除已用验证码
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM sms_codes WHERE phone = ?")) {
                ps.setString(1, phone);
                ps.executeUpdate();
            }

            // 3. 确保 users 表有该手机号
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO users (phone) VALUES (?)
                    ON CONFLICT (phone) DO NOTHING
                    """)) {
                ps.setString(1, phone);
                ps.executeUpdate();
            }

            // 4. 取 user_id
            long userId;
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE phone = ?")) {
                ps.setString(1, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    userId = rs.getLong("id");
                }
            }
            log.debug("[Auth] 用户 userId={} phone={}", userId, phone);

            // 5. 写 token，30 天有效
            String token = UUID.randomUUID().toString().replace("-", "");
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO auth_tokens (token, user_id, expires_at)
                    VALUES (?, ?, NOW() + INTERVAL '30 days')
                    """)) {
                ps.setString(1, token);
                ps.setLong(2, userId);
                ps.executeUpdate();
            }
            log.info("[Auth] 登录成功 phone={} userId={} token={}...", phone, userId, token.substring(0, 8));
            return token;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            log.error("[Auth] 验证码校验失败 phone={} error={}", phone, e.getMessage(), e);
            throw new RuntimeException("登录失败", e);
        }
    }

    public long userIdByToken(String token) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                "SELECT user_id FROM auth_tokens WHERE token = ? AND expires_at > NOW()")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("user_id");
                throw new IllegalArgumentException("登录已过期，请重新登录");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            log.error("[Auth] userIdByToken 失败: {}", e.getMessage(), e);
            throw new RuntimeException("token查询失败", e);
        }
    }

    public String phoneByToken(String token) {
        log.debug("[Auth] phoneByToken token={}...", token.length() > 8 ? token.substring(0, 8) : token);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                SELECT u.phone FROM auth_tokens t
                JOIN users u ON t.user_id = u.id
                WHERE t.token = ? AND t.expires_at > NOW()
                """)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                String phone = rs.next() ? rs.getString("phone") : null;
                if (phone == null) log.warn("[Auth] token 无效或已过期 token={}...", token.substring(0, 8));
                else log.debug("[Auth] token 有效 phone={}", phone);
                return phone;
            }
        } catch (SQLException e) {
            log.error("[Auth] token 查询失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean hasAccess(String phone) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT has_access FROM users WHERE phone = ?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                boolean access = rs.next() && rs.getBoolean("has_access");
                log.debug("[Auth] hasAccess phone={} result={}", phone, access);
                return access;
            }
        } catch (SQLException e) {
            log.error("[Auth] hasAccess 查询失败 phone={} error={}", phone, e.getMessage(), e);
            return false;
        }
    }

    public void grantAccess(String phone) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO users (phone, has_access) VALUES (?, TRUE)
                ON CONFLICT (phone) DO UPDATE SET has_access = TRUE
                """)) {
            ps.setString(1, phone);
            ps.executeUpdate();
            log.info("[ADMIN] granted access to phone={}", phone);
        } catch (SQLException e) {
            log.error("[Auth] grantAccess 失败: {}", e.getMessage());
        }
    }

    public void logout(String token) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM auth_tokens WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
            log.info("[Auth] token 已注销 token={}...", token.length() > 8 ? token.substring(0, 8) : token);
        } catch (SQLException e) {
            log.error("[Auth] logout 失败: {}", e.getMessage());
        }
    }

    public void addCredits(String phone, int amount) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO users (phone, credits) VALUES (?, ?)
                ON CONFLICT (phone) DO UPDATE SET credits = users.credits + EXCLUDED.credits
                """)) {
            ps.setString(1, phone);
            ps.setInt(2, amount);
            ps.executeUpdate();
            log.info("[Auth] 积分到账 phone={} amount={}", phone, amount);
        } catch (SQLException e) {
            log.error("[Auth] addCredits 失败: {}", e.getMessage());
        }
    }

    public void revokeAccess(String phone) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET has_access = FALSE WHERE phone = ?")) {
            ps.setString(1, phone);
            ps.executeUpdate();
            log.info("[ADMIN] revoked access from phone={}", phone);
        } catch (SQLException e) {
            log.error("[Auth] revokeAccess 失败: {}", e.getMessage());
        }
    }
}
