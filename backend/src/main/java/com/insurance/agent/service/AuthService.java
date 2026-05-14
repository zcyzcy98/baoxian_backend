package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String SMS_CODE_KEY_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_KEY_PREFIX = "sms:limit:";
    private static final String AUTH_TOKEN_KEY_PREFIX = "auth:token:";
    private static final String AUTH_PHONE_KEY_PREFIX = "auth:phone:";

    private static final long SMS_CODE_TTL_SECONDS = 300;
    private static final long SMS_LIMIT_TTL_SECONDS = 60;
    private static final long TOKEN_TTL_SECONDS = 2_592_000L;

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final SmsService smsService;

    public AuthService(DataSource dataSource, StringRedisTemplate redisTemplate, SmsService smsService) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.smsService = smsService;
    }

    public String sendCode(String phone) {
        log.debug("[Auth] sendCode 开始 phone={}", phone);

        // 60秒内不允许重复发送
        String limitKey = SMS_LIMIT_KEY_PREFIX + phone;
        Boolean limited = redisTemplate.hasKey(limitKey);
        if (Boolean.TRUE.equals(limited)) {
            throw new IllegalStateException("发送太频繁，请60秒后再试");
        }

        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        try {
            redisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + phone, code, SMS_CODE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(limitKey, "1", SMS_LIMIT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[Auth] 存储验证码失败 phone={} error={}", phone, e.getMessage(), e);
            throw new RuntimeException("发送验证码失败", e);
        }

        boolean sent = smsService.sendCode(phone, code);
        if (!sent) {
            throw new RuntimeException("短信发送失败，请稍后重试");
        }
        return smsService.isConfigured() ? null : code; // 仅 mock 模式返回明文
    }

    public String verifyCode(String phone, String code) {
        log.debug("[Auth] verifyCode 开始 phone={}", phone);
        try {
            // 1. 从 Redis 取验证码
            String expected = redisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + phone);
            if (expected == null) {
                log.warn("[Auth] 验证码不存在或已过期 phone={}", phone);
                throw new IllegalArgumentException("验证码已过期，请重新获取");
            }
            if (!expected.equals(code)) {
                log.warn("[Auth] 验证码错误 phone={} input={}", phone, code);
                throw new IllegalArgumentException("验证码错误");
            }
            log.debug("[Auth] 验证码校验通过 phone={}", phone);

            // 2. 删除已用的验证码
            redisTemplate.delete(SMS_CODE_KEY_PREFIX + phone);

            // 3. 确保 users 表有该手机号
            long userId;
            try (Connection c = dataSource.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO users (phone) VALUES (?)
                        ON CONFLICT (phone) DO NOTHING
                        """)) {
                    ps.setString(1, phone);
                    ps.executeUpdate();
                }

                // 4. 取 user_id
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE phone = ?")) {
                    ps.setString(1, phone);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        userId = rs.getLong("id");
                    }
                }
            }
            log.debug("[Auth] 用户 userId={} phone={}", userId, phone);

            // 5. 生成 token 并写入 Redis，30 天有效
            String token = UUID.randomUUID().toString().replace("-", "");
            redisTemplate.opsForValue().set(AUTH_TOKEN_KEY_PREFIX + token, String.valueOf(userId), TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(AUTH_PHONE_KEY_PREFIX + token, phone, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("[Auth] 登录成功 phone={} userId={} token={}...", phone, userId, token.substring(0, 8));
            return token;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Auth] 验证码校验失败 phone={} error={}", phone, e.getMessage(), e);
            throw new RuntimeException("登录失败", e);
        }
    }

    public long userIdByToken(String token) {
        String userIdStr = redisTemplate.opsForValue().get(AUTH_TOKEN_KEY_PREFIX + token);
        if (userIdStr == null) {
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }
        return Long.parseLong(userIdStr);
    }

    public String phoneByToken(String token) {
        log.debug("[Auth] phoneByToken token={}...", token.length() > 8 ? token.substring(0, 8) : token);
        String phone = redisTemplate.opsForValue().get(AUTH_PHONE_KEY_PREFIX + token);
        if (phone == null) {
            log.warn("[Auth] token 无效或已过期 token={}...", token.substring(0, 8));
        } else {
            log.debug("[Auth] token 有效 phone={}", phone);
        }
        return phone;
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
        redisTemplate.delete(AUTH_TOKEN_KEY_PREFIX + token);
        redisTemplate.delete(AUTH_PHONE_KEY_PREFIX + token);
        log.info("[Auth] token 已注销 token={}...", token.length() > 8 ? token.substring(0, 8) : token);
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
