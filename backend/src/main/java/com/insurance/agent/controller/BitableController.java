package com.insurance.agent.controller;

import com.insurance.agent.service.FeishuBitableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bitable")
public class BitableController {

    private final FeishuBitableService bitableService;

    public BitableController(FeishuBitableService bitableService) {
        this.bitableService = bitableService;
    }

    @PostMapping("/info")
    public ResponseEntity<?> getBitableInfo(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        String appToken = request.get("appToken");

        if (appToken == null || appToken.isBlank()) {
            appToken = bitableService.extractAppToken(request.get("appTokenUrl"));
        }

        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "appToken 不能为空"));
        }

        Map<String, Object> result = bitableService.getBitableInfo(accessToken, appToken);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tables")
    public ResponseEntity<?> getTables(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        String appToken = bitableService.extractAppToken(request.get("appToken"));

        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "appToken 不能为空"));
        }

        var tables = bitableService.getTables(accessToken, appToken);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "appToken", appToken,
                "tables", tables,
                "tablesCount", tables.size()
        ));
    }

    @PostMapping("/records")
    public ResponseEntity<?> getRecords(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        String appToken = bitableService.extractAppToken(request.get("appToken"));
        String tableId = request.get("tableId");
        int pageSize = request.containsKey("pageSize") ?
                Integer.parseInt(request.get("pageSize")) : 100;

        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "appToken 不能为空"));
        }
        if (tableId == null || tableId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "tableId 不能为空"));
        }

        Map<String, Object> result = bitableService.getTableRecords(accessToken, appToken, tableId, pageSize, null);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/records/all")
    public ResponseEntity<?> getAllRecords(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        String appToken = bitableService.extractAppToken(request.get("appToken"));
        String tableId = request.get("tableId");

        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "appToken 不能为空"));
        }
        if (tableId == null || tableId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "tableId 不能为空"));
        }

        Map<String, Object> result = bitableService.getAllRecords(accessToken, appToken, tableId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchRecords(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        String appToken = bitableService.extractAppToken(request.get("appToken"));
        String tableId = request.get("tableId");
        String fieldName = request.get("fieldName");
        String searchValue = request.get("searchValue");

        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "appToken 不能为空"));
        }
        if (tableId == null || tableId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "tableId 不能为空"));
        }
        if (searchValue == null || searchValue.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "searchValue 不能为空"));
        }

        Map<String, Object> result = bitableService.searchRecords(
                accessToken, appToken, tableId, fieldName, searchValue);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/cache")
    public ResponseEntity<?> clearCache() {
        bitableService.clearCache();
        return ResponseEntity.ok(Map.of("success", true, "message", "缓存已清除"));
    }
}
