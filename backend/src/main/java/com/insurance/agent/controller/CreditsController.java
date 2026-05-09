package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/credits")
public class CreditsController {

    private final CreditsService creditsService;
    private final AuthService authService;

    public CreditsController(CreditsService creditsService, AuthService authService) {
        this.creditsService = creditsService;
        this.authService = authService;
    }

    @GetMapping("/balance")
    public Map<String, Object> balance(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        long uid = resolveUserId(auth);
        int bal = creditsService.getBalance(uid);
        return Map.of("balance", bal, "total", 8000);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return creditsService.getSummary(resolveUserId(auth));
    }

    @GetMapping("/records")
    public List<Map<String, Object>> records(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return creditsService.getRecords(resolveUserId(auth), filter, page, size);
    }

    @GetMapping("/records/{id}/content")
    public Map<String, Object> recordContent(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long id) {
        long uid = resolveUserId(auth);
        Map<String, Object> raw = creditsService.getRecordContent(uid, id);
        if (raw == null) throw new RuntimeException("记录不存在或未关联内容");
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("id", raw.get("id"));
        mapped.put("content_type", raw.get("type"));
        mapped.put("topic", raw.get("title"));
        mapped.put("content", raw.get("content"));
        mapped.put("model", raw.get("model"));
        mapped.put("created_at", raw.get("createdAt"));
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) raw.get("imageUrls");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            mapped.put("image_url", imageUrls.get(0));
        }
        mapped.put("video_url", raw.get("videoUrl"));
        mapped.put("cover_url", raw.get("coverUrl"));
        return mapped;
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }
}
