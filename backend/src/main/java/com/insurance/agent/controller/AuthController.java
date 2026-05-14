package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/send-code")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "手机号格式不正确"));
        }
        try {
            String devCode = authService.sendCode(phone);
            Map<String, Object> res = new HashMap<>();
            res.put("ok", true);
            if (devCode != null) {
                res.put("_devCode", devCode);
            }
            return ResponseEntity.ok(res);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        if (phone == null || code == null) {
            throw new IllegalArgumentException("手机号和验证码不能为空");
        }
        String token = authService.verifyCode(phone, code);
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("phone", phone);
        res.put("hasAccess", authService.hasAccess(phone));
        return res;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring(7));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        return res;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        String phone = resolvePhone(auth);
        Map<String, Object> res = new HashMap<>();
        res.put("phone", phone);
        res.put("hasAccess", authService.hasAccess(phone));
        return res;
    }

    private String resolvePhone(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        String token = auth.substring(7);
        String phone = authService.phoneByToken(token);
        if (phone == null) throw new IllegalArgumentException("登录已过期，请重新登录");
        return phone;
    }
}
