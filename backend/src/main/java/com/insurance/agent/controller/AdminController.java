package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AuthService authService;

    @Value("${admin.secret-key:chengzhi-admin-2024}")
    private String adminKey;

    @PostMapping("/grant")
    public ResponseEntity<Map<String, Object>> grant(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!adminKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无权限"));
        }
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone 不能为空"));
        }
        authService.grantAccess(phone);
        return ResponseEntity.ok(Map.of("ok", true, "phone", phone, "action", "granted"));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!adminKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "无权限"));
        }
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone 不能为空"));
        }
        authService.revokeAccess(phone);
        return ResponseEntity.ok(Map.of("ok", true, "phone", phone, "action", "revoked"));
    }
}
