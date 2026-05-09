package com.insurance.agent.controller;

import com.insurance.agent.dto.RewriteRequest;
import com.insurance.agent.dto.RewriteResponse;
import com.insurance.agent.dto.WechatArticle;
import com.insurance.agent.dto.XhsNote;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import com.insurance.agent.service.NoteRewriteService;
import com.insurance.agent.service.WechatExtractService;
import com.insurance.agent.service.XhsExtractService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final XhsExtractService xhs;
    private final WechatExtractService wechat;
    private final NoteRewriteService rewriteService;
    private final AuthService authService;
    private final CreditsService creditsService;

    public NoteController(XhsExtractService xhs, WechatExtractService wechat,
                          NoteRewriteService rewriteService,
                          AuthService authService, CreditsService creditsService) {
        this.xhs = xhs;
        this.wechat = wechat;
        this.rewriteService = rewriteService;
        this.authService = authService;
        this.creditsService = creditsService;
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }

    public static class ExtractRequest {
        private String url;
        private String cookie;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getCookie() { return cookie; }
        public void setCookie(String cookie) { this.cookie = cookie; }
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestBody ExtractRequest req) {
        if (req == null || req.getUrl() == null || req.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url 不能为空"));
        }
        XhsNote note = xhs.extract(req.getUrl(), req.getCookie());
        return ResponseEntity.ok(note);
    }

    /**
     * 通用提取入口: 自动识别小红书 / 公众号链接,
     * 返回 {source: "xhs" | "wechat", note: ...} 让前端用同一接口走两条路.
     */
    @PostMapping("/extract-auto")
    public ResponseEntity<?> extractAuto(@RequestBody ExtractRequest req) {
        if (req == null || req.getUrl() == null || req.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url 不能为空"));
        }
        String url = extractUrl(req.getUrl());
        String lower = url.toLowerCase();
        if (lower.contains("mp.weixin.qq.com") || lower.contains("weixin.qq.com")) {
            WechatArticle a = wechat.extract(url, req.getCookie());
            return ResponseEntity.ok(Map.of("source", "wechat", "note", a));
        }
        // 默认走小红书
        XhsNote note = xhs.extract(url, req.getCookie());
        return ResponseEntity.ok(Map.of("source", "xhs", "note", note));
    }

    @PostMapping("/wechat/extract")
    public ResponseEntity<?> extractWechat(@RequestBody ExtractRequest req) {
        if (req == null || req.getUrl() == null || req.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "url 不能为空"));
        }
        WechatArticle a = wechat.extract(req.getUrl(), req.getCookie());
        return ResponseEntity.ok(a);
    }

    @PostMapping("/rewrite")
    public ResponseEntity<?> rewrite(@RequestBody RewriteRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req == null || req.getOriginalContent() == null || req.getOriginalContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "originalContent 不能为空"));
        }
        creditsService.deduct(resolveUserId(auth), 12, "xhs_rewrite", "小红书仿写");
        RewriteResponse resp = rewriteService.rewrite(req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/rewrite-modes")
    public ResponseEntity<?> rewriteModes() {
        Map<String, String> modes = rewriteService.listModes();
        List<Map<String, String>> out = new ArrayList<>();
        modes.forEach((id, label) -> out.add(Map.of("id", id, "label", label)));
        return ResponseEntity.ok(out);
    }

    /**
     * 公众号仿写: 输入公众号链接，先提取内容，再按指定模式改写。
     *
     * 流程: mp.weixin.qq.com/s 链接 → WechatExtractService → WechatArticle
     *       → RewriteRequest(从 toRewritableText() 拿内容) → NoteRewriteService → RewriteResponse
     */
    @PostMapping("/wechat/rewrite")
    public ResponseEntity<?> rewriteWechat(@RequestBody WechatRewriteRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req == null || (req.getUrl() == null && req.getContent() == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供公众号链接或文章内容"));
        }

        // 1. 提取公众号内容
        WechatArticle article;
        try {
            if (req.getUrl() != null && !req.getUrl().isBlank()) {
                article = wechat.extract(req.getUrl(), req.getCookie());
            } else {
                // 直接使用提供的内容
                article = new WechatArticle();
                article.setTitle(req.getTitle());
                article.setContent(req.getContent());
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "提取公众号内容失败: " + e.getMessage()));
        }

        // 2. 构建改写请求
        RewriteRequest rewriteReq = new RewriteRequest();
        rewriteReq.setOriginalTitle(article.getTitle());
        rewriteReq.setOriginalContent(article.toRewritableText());
        rewriteReq.setMode(req.getMode() != null ? req.getMode() : NoteRewriteService.MODE_MP_REWRITE);
        rewriteReq.setRequirements(req.getRequirements());
        rewriteReq.setModel(req.getModel());

        // 3. 扣除积分
        creditsService.deduct(resolveUserId(auth), 20, "gzh_rewrite", "公众号仿写");

        // 4. 执行改写
        RewriteResponse result = rewriteService.rewrite(rewriteReq);

        // 5. 返回结果，包含原始文章信息
        return ResponseEntity.ok(Map.of(
                "original", article,
                "rewritten", result
        ));
    }

    /**
     * 公众号仿写请求 DTO
     */
    public static class WechatRewriteRequest {
        private String url;           // 公众号链接
        private String cookie;        // 可选 cookie
        private String title;         // 可选：直接提供标题
        private String content;       // 可选：直接提供内容
        private String mode;          // 改写模式
        private String requirements;  // 附加要求
        private String model;         // 使用的模型

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getCookie() { return cookie; }
        public void setCookie(String cookie) { this.cookie = cookie; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getRequirements() { return requirements; }
        public void setRequirements(String requirements) { this.requirements = requirements; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    /** 从可能混有表情、标题、随机码的分享文本中提取第一个 http(s) URL */
    private static String extractUrl(String text) {
        if (text == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://[^\\s\\u4e00-\\u9fa5\\[\\]【】「」（）()]+")
                .matcher(text.trim());
        if (m.find()) {
            return m.group().replaceAll("[,，。.!！?？]+$", "");
        }
        return text.trim();
    }
}
