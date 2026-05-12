package com.insurance.agent.controller;

import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.StyleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/style")
public class StyleController {

    private final StyleService styleService;
    private final AuthService authService;

    public StyleController(StyleService styleService, AuthService authService) {
        this.styleService = styleService;
        this.authService = authService;
    }

    /** 获取当前风格档案 + 素材列表 */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        long uid = resolveUserId(auth);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profile", styleService.getProfile(uid));
        body.put("sources", styleService.listSources(uid));
        return ResponseEntity.ok(body);
    }

    /** 添加素材 */
    @PostMapping("/sources")
    public ResponseEntity<?> addSource(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> req) {
        try {
            long uid    = resolveUserId(auth);
            String type    = req.getOrDefault("type", "text");
            String title   = req.get("title");
            String url     = req.get("url");
            String rawText = req.get("rawText");
            String model   = req.get("model");
            Map<String, Object> source = styleService.addSource(uid, title, type, url, rawText, model);
            return ResponseEntity.ok(source);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 上传文件素材（PDF/DOCX） */
    @PostMapping("/sources/upload")
    public ResponseEntity<?> uploadSource(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
            }
            long uid = resolveUserId(auth);
            Map<String, Object> source = styleService.addSourceFromFile(uid, title, file);
            return ResponseEntity.ok(source);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除素材 */
    @DeleteMapping("/sources/{id}")
    public ResponseEntity<?> deleteSource(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable long id) {
        try {
            styleService.deleteSource(resolveUserId(auth), id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 训练风格 */
    @PostMapping("/train")
    public ResponseEntity<?> train(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> req) {
        try {
            String model = req != null ? req.get("model") : null;
            Map<String, Object> result = styleService.train(resolveUserId(auth), model);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 风格预览——用当前风格生成一段样例 */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> req) {
        try {
            String topic = req.getOrDefault("topic", "重疾险怎么选");
            String model = req.get("model");
            String content = styleService.preview(resolveUserId(auth), topic, model);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未登录");
        }
        return authService.userIdByToken(auth.substring(7));
    }
}
