package com.insurance.agent.controller;

import com.insurance.agent.service.DouyinSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/douyin")
public class DouyinController {

    private final DouyinSettings douyinSettings;

    public DouyinController(DouyinSettings douyinSettings) {
        this.douyinSettings = douyinSettings;
    }

    /** 查询当前 cookie 状态（不返回值，只返回是否已设置） */
    @GetMapping("/cookie/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "hasCookie", douyinSettings.hasCookie(),
                "preview", douyinSettings.hasCookie()
                        ? douyinSettings.getCookie().substring(0, Math.min(20, douyinSettings.getCookie().length())) + "..."
                        : ""
        ));
    }

    /** 运行时更新 cookie，无需重启服务 */
    @PostMapping("/cookie")
    public ResponseEntity<Map<String, Object>> setCookie(@RequestBody Map<String, String> body) {
        String cookie = body.getOrDefault("cookie", "").trim();
        douyinSettings.setCookie(cookie);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "hasCookie", douyinSettings.hasCookie()
        ));
    }

    /** 清除 cookie */
    @DeleteMapping("/cookie")
    public ResponseEntity<Map<String, Object>> clearCookie() {
        douyinSettings.setCookie("");
        return ResponseEntity.ok(Map.of("success", true, "hasCookie", false));
    }
}
