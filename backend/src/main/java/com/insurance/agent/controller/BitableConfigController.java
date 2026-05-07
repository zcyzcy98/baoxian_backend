package com.insurance.agent.controller;

import com.insurance.agent.dto.BitableConfig;
import com.insurance.agent.service.BitableConfigService;
import com.insurance.agent.service.FeishuBitableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bitable/config")
public class BitableConfigController {

    private final BitableConfigService configService;
    private final FeishuBitableService bitableService;

    public BitableConfigController(BitableConfigService configService,
                                    FeishuBitableService bitableService) {
        this.configService = configService;
        this.bitableService = bitableService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<BitableConfig>> getAllConfigs(
            @RequestParam(required = false) String category) {
        if (category == null || category.isBlank()) {
            return ResponseEntity.ok(configService.getAllConfigs());
        }
        return ResponseEntity.ok(configService.getConfigsByCategory(category));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(configService.getCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BitableConfig> getConfig(@PathVariable String id) {
        BitableConfig config = configService.getConfig(id);
        if (config != null) {
            return ResponseEntity.ok(config);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<BitableConfig> getConfigByName(@PathVariable String name) {
        BitableConfig config = configService.getConfigByName(name);
        if (config != null) {
            return ResponseEntity.ok(config);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addConfig(@RequestBody BitableConfig config) {
        boolean success = configService.saveConfig(config);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "config", config
        ));
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody BitableConfig config) {
        if (config.getId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "id 不能为空"));
        }
        boolean success = configService.saveConfig(config);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "config", config
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String id) {
        boolean success = configService.deleteConfig(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "删除成功" : "配置不存在"
        ));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable String id) {
        boolean success = configService.toggleActive(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "切换成功" : "配置不存在"
        ));
    }

    @PostMapping("/query/{configId}")
    public ResponseEntity<Map<String, Object>> queryByConfig(@PathVariable String configId,
                                                              @RequestBody Map<String, String> request) {
        BitableConfig config = configService.getConfig(configId);
        if (config == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "配置不存在"));
        }
        if (config.getAppToken() == null || config.getAppToken().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "配置的 appToken 为空"));
        }

        String accessToken = request.get("accessToken");
        String searchValue = request.get("searchValue");
        String tableId = request.getOrDefault("tableId", config.getTableId());

        if (searchValue != null && !searchValue.isBlank() && tableId != null) {
            Map<String, Object> result = bitableService.searchRecords(
                    accessToken, config.getAppToken(), tableId, null, searchValue);
            result.put("configId", configId);
            result.put("configName", config.getName());
            return ResponseEntity.ok(result);
        }

        if (tableId != null) {
            Map<String, Object> result = bitableService.getAllRecords(
                    accessToken, config.getAppToken(), tableId);
            result.put("configId", configId);
            result.put("configName", config.getName());
            return ResponseEntity.ok(result);
        }

        Map<String, Object> result = bitableService.getBitableInfo(
                accessToken, config.getAppToken());
        result.put("configId", configId);
        result.put("configName", config.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/query/name/{configName}")
    public ResponseEntity<Map<String, Object>> queryByConfigName(@PathVariable String configName,
                                                                  @RequestBody Map<String, String> request) {
        BitableConfig config = configService.getConfigByName(configName);
        if (config == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "配置不存在: " + configName));
        }
        request.put("configId", config.getId());
        return queryByConfig(config.getId(), request);
    }
}
