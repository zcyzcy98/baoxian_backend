package com.insurance.agent.controller;

import com.insurance.agent.dto.XiangshengRequest;
import com.insurance.agent.dto.XiangshengResponse;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import com.insurance.agent.service.GeneratedContentService;
import com.insurance.agent.service.XiangshengPromptTemplates;
import com.insurance.agent.service.XiangshengService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/xiangsheng")
public class XiangshengController {

    private final XiangshengService xiangshengService;
    private final GeneratedContentService generatedContentService;
    private final CreditsService creditsService;
    private final AuthService authService;

    public XiangshengController(XiangshengService xiangshengService,
                                 GeneratedContentService generatedContentService,
                                 CreditsService creditsService,
                                 AuthService authService) {
        this.xiangshengService = xiangshengService;
        this.generatedContentService = generatedContentService;
        this.creditsService = creditsService;
        this.authService = authService;
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }

    private ResponseEntity<?> checkCredits(long userId, int cost) {
        int balance = creditsService.getBalance(userId);
        if (balance < cost) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", "积分不足（当前 " + balance + " 积分，需要 " + cost + " 积分）"));
        }
        return null;
    }

    /**
     * 获取所有维度选项
     */
    @GetMapping("/dimensions")
    public ResponseEntity<?> getDimensions() {
        return ResponseEntity.ok(XiangshengPromptTemplates.dimensionOptions());
    }

    /**
     * 一键全流程：台词 → 分镜 → 分组提示词
     */
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody XiangshengRequest req,
                                     @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getTopic() == null || req.getTopic().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入选题"));
        }

        long userId = resolveUserId(auth);
        ResponseEntity<?> creditCheck = checkCredits(userId, 15);
        if (creditCheck != null) return creditCheck;

        XiangshengResponse result = xiangshengService.fullPipeline(
                req.getTopic(),
                null, null, null, null, null,
                req.getToneStyle(),
                req.getDuration(),
                req.getModel());

        Long contentId = generatedContentService.save("xiangsheng_script",
                result.getStyleName() + " | " + req.getTopic(),
                result.getGroupPrompts(),
                null, null, null, result.getModel());
        creditsService.deduct(userId, 15, "xiangsheng_create", result.getStyleName(), contentId);

        return ResponseEntity.ok(result);
    }

    /**
     * 阶段一：仅生成台词
     */
    @PostMapping("/stage1")
    public ResponseEntity<?> stage1(@RequestBody XiangshengRequest req,
                                     @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getTopic() == null || req.getTopic().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入选题"));
        }

        long userId = resolveUserId(auth);
        ResponseEntity<?> creditCheck = checkCredits(userId, 5);
        if (creditCheck != null) return creditCheck;

        var result = xiangshengService.stage1(
                req.getTopic(),
                null, null, null, null, null,
                req.getToneStyle(),
                req.getDuration(),
                req.getModel());

        Long contentId = generatedContentService.save("xiangsheng_dialogue",
                result.styleName() + " | " + req.getTopic(),
                result.dialogue(),
                null, null, null, req.getModel());
        creditsService.deduct(userId, 5, "xiangsheng_stage1", result.styleName(), contentId);

        return ResponseEntity.ok(Map.of(
                "styleName", result.styleName(),
                "dialogue", result.dialogue()));
    }

    /**
     * 阶段二：从台词生成分镜
     */
    @PostMapping("/stage2")
    public ResponseEntity<?> stage2(@RequestBody XiangshengRequest req,
                                     @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getDialogue() == null || req.getDialogue().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供台词内容"));
        }

        long userId = resolveUserId(auth);
        ResponseEntity<?> creditCheck = checkCredits(userId, 5);
        if (creditCheck != null) return creditCheck;

        String storyboard = xiangshengService.stage2(req.getDialogue(), req.getDuration(), req.getModel());

        Long contentId = generatedContentService.save("xiangsheng_storyboard",
                "分镜剧本",
                storyboard,
                null, null, null, req.getModel());
        creditsService.deduct(userId, 5, "xiangsheng_stage2", "分镜剧本", contentId);

        return ResponseEntity.ok(Map.of("storyboard", storyboard));
    }

    /**
     * 阶段三：从分镜生成分组提示词
     */
    @PostMapping("/stage3")
    public ResponseEntity<?> stage3(@RequestBody XiangshengRequest req,
                                     @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.getStoryboard() == null || req.getStoryboard().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供分镜剧本"));
        }

        long userId = resolveUserId(auth);
        ResponseEntity<?> creditCheck = checkCredits(userId, 5);
        if (creditCheck != null) return creditCheck;

        String groupPrompts = xiangshengService.stage3(req.getStoryboard(), req.getModel());

        Long contentId = generatedContentService.save("xiangsheng_group_prompts",
                "分组提示词",
                groupPrompts,
                null, null, null, req.getModel());
        creditsService.deduct(userId, 5, "xiangsheng_stage3", "分组提示词", contentId);

        return ResponseEntity.ok(Map.of("groupPrompts", groupPrompts));
    }
}
