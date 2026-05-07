package com.insurance.agent.controller;

import com.insurance.agent.dto.WechatDraftRequest;
import com.insurance.agent.service.WechatDraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wechat")
public class WechatDraftController {

    private final WechatDraftService draftService;

    public WechatDraftController(WechatDraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping("/draft")
    public ResponseEntity<?> createDraft(@RequestBody WechatDraftRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "请求体不能为空"));
        }
        return ResponseEntity.ok(draftService.create(req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleRuntime(IllegalStateException e) {
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
}
