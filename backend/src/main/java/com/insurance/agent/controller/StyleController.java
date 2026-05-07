package com.insurance.agent.controller;

import com.insurance.agent.service.StyleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/style")
public class StyleController {

    private final StyleService styleService;

    public StyleController(StyleService styleService) {
        this.styleService = styleService;
    }

    /** 获取当前风格档案 + 素材列表 */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profile", styleService.getProfile());
        body.put("sources", styleService.listSources());
        return ResponseEntity.ok(body);
    }

    /** 添加素材 */
    @PostMapping("/sources")
    public ResponseEntity<?> addSource(@RequestBody Map<String, String> req) {
        try {
            String type    = req.getOrDefault("type", "text");
            String title   = req.get("title");
            String url     = req.get("url");
            String rawText = req.get("rawText");
            String model   = req.get("model");
            Map<String, Object> source = styleService.addSource(title, type, url, rawText, model);
            return ResponseEntity.ok(source);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除素材 */
    @DeleteMapping("/sources/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable long id) {
        try {
            styleService.deleteSource(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 训练风格 */
    @PostMapping("/train")
    public ResponseEntity<?> train(@RequestBody(required = false) Map<String, String> req) {
        try {
            String model = req != null ? req.get("model") : null;
            Map<String, Object> result = styleService.train(model);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 风格预览——用当前风格生成一段样例 */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody Map<String, String> req) {
        try {
            String topic = req.getOrDefault("topic", "重疾险怎么选");
            String model = req.get("model");
            String content = styleService.preview(topic, model);
            return ResponseEntity.ok(Map.of("content", content));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
