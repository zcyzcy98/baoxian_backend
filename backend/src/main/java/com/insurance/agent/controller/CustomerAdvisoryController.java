package com.insurance.agent.controller;

import com.insurance.agent.dto.AdvisoryRequest;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import com.insurance.agent.service.CustomerAdvisoryService;
import com.insurance.agent.service.GeneratedContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/advisory")
public class CustomerAdvisoryController {

    private final CustomerAdvisoryService service;
    private final AuthService authService;
    private final CreditsService creditsService;
    private final GeneratedContentService generatedContent;
    private final ObjectMapper objectMapper;

    public CustomerAdvisoryController(CustomerAdvisoryService service,
                                      AuthService authService,
                                      CreditsService creditsService,
                                      GeneratedContentService generatedContent,
                                      ObjectMapper objectMapper) {
        this.service = service;
        this.authService = authService;
        this.creditsService = creditsService;
        this.generatedContent = generatedContent;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ResponseEntity.ok(service.getSessions(resolveUserId(auth)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(
            @RequestBody AdvisoryRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户名称不能为空"));
        }
        return ResponseEntity.ok(service.createSession(resolveUserId(auth), req.getName(), req.getSummary()));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return ResponseEntity.ok(service.getSession(resolveUserId(auth), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(
            @PathVariable long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        service.deleteSession(resolveUserId(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/sessions/{id}/analyze")
    public ResponseEntity<?> analyze(
            @PathVariable long id,
            @RequestBody AdvisoryRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户问题不能为空"));
        }
        if (req.getCustomerInfo() == null || req.getCustomerInfo().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "客户基本情况不能为空"));
        }
        long uid = resolveUserId(auth);
        // 基础答疑 2 积分；如果有图片分析额外 +1 积分
        int cost = 2;
        String q = req.getQuestion();
        String desc = q.length() > 50 ? q.substring(0, 50) + "…" : q;
        Map<String, Object> result = service.analyze(uid, id, req.getCustomerInfo(), req.getQuestion(), req.getChannel());
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            Long contentId = generatedContent.save("advisory", desc, resultJson, null, null, null, "deepseek-chat");
            creditsService.deduct(uid, cost, "advisory", desc, contentId);
        } catch (Exception e) {
            creditsService.deduct(uid, cost, "advisory", desc);
        }
        return ResponseEntity.ok(result);
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }
}
