package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long CODE_TTL_MS = 5 * 60 * 1000L;

    // phone -> { code, expiry }
    private final Map<String, long[]> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, String> codeMap = new ConcurrentHashMap<>();
    // token -> phone
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    // phone -> hasAccess
    private final Map<String, Boolean> accessMap = new ConcurrentHashMap<>();

    public String sendCode(String phone) {
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        codeMap.put(phone, code);
        pendingCodes.put(phone, new long[]{System.currentTimeMillis() + CODE_TTL_MS});
        log.info("[MOCK SMS] phone={} code={}", phone, code);
        return code;
    }

    public String verifyCode(String phone, String code) {
        String expected = codeMap.get(phone);
        long[] expiry = pendingCodes.get(phone);
        if (expected == null || !expected.equals(code)) {
            throw new IllegalArgumentException("验证码错误");
        }
        if (expiry == null || System.currentTimeMillis() > expiry[0]) {
            codeMap.remove(phone);
            pendingCodes.remove(phone);
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        codeMap.remove(phone);
        pendingCodes.remove(phone);

        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, phone);
        return token;
    }

    public String phoneByToken(String token) {
        return tokens.get(token);
    }

    public boolean hasAccess(String phone) {
        return Boolean.TRUE.equals(accessMap.get(phone));
    }

    public void grantAccess(String phone) {
        accessMap.put(phone, true);
        log.info("[ADMIN] granted access to phone={}", phone);
    }

    public void revokeAccess(String phone) {
        accessMap.put(phone, false);
        log.info("[ADMIN] revoked access from phone={}", phone);
    }
}
