package com.insurance.agent.controller;

import com.insurance.agent.dto.BitableConfig;
import com.insurance.agent.service.BitableConfigService;
import com.insurance.agent.service.FeishuBitableService;
import com.insurance.agent.service.PostgresVectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bitable/config")
public class BitableConfigController {

    private final BitableConfigService configService;
    private final FeishuBitableService bitableService;
    private final PostgresVectorStoreService vectorStore;

    public BitableConfigController(BitableConfigService configService,
                                    FeishuBitableService bitableService,
                                    PostgresVectorStoreService vectorStore) {
        this.configService = configService;
        this.bitableService = bitableService;
        this.vectorStore = vectorStore;
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

    /**
     * 直接用 appToken + tableId 保存配置并立即向量化（wiki URL 解析失败时使用）。
     * Body: { "appToken": "xxx", "tableId": "tbl0Ylg7YIMgwjnD", "name": "短视频爆款样本库", "category": "video" }
     */
    @PostMapping("/setup-direct")
    public ResponseEntity<Map<String, Object>> setupDirect(@RequestBody Map<String, String> body) {
        String appToken = body.get("appToken");
        String name = body.get("name");
        if (appToken == null || appToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "appToken 不能为空"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "name 不能为空"));
        }
        BitableConfig config = new BitableConfig();
        config.setName(name);
        config.setAppToken(appToken.trim());
        config.setTableId(body.getOrDefault("tableId", "").trim());
        config.setCategory(body.getOrDefault("category", "sample"));
        config.setActive(true);
        configService.saveConfig(config);
        try {
            PostgresVectorStoreService.VectorizeResult result = vectorStore.vectorizeBitables(config.getId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configId", config.getId(),
                    "name", name,
                    "vectorized", result.total(),
                    "message", "配置已保存，已向量化 " + result.total() + " 条记录"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configId", config.getId(),
                    "warning", "配置已保存，但向量化失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 从飞书 wiki 或 base URL 自动解析 appToken + tableId，保存配置，并立即向量化。
     * Body: { "url": "...", "name": "短视频爆款样本库", "category": "video" }
     */
    @PostMapping("/setup-from-url")
    public ResponseEntity<Map<String, Object>> setupFromUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String name = body.get("name");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "url 不能为空"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "name 不能为空（将作为向量表名前缀）"));
        }

        Map<String, String> resolved = bitableService.resolveFromUrl(url);
        if (resolved.containsKey("error")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", resolved.get("error")));
        }

        BitableConfig config = new BitableConfig();
        config.setName(name);
        config.setAppToken(resolved.get("appToken"));
        config.setTableId(resolved.get("tableId"));
        config.setCategory(body.getOrDefault("category", "sample"));
        config.setActive(true);
        configService.saveConfig(config);

        try {
            PostgresVectorStoreService.VectorizeResult result = vectorStore.vectorizeBitables(config.getId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configId", config.getId(),
                    "name", name,
                    "appToken", resolved.get("appToken"),
                    "tableId", resolved.getOrDefault("tableId", ""),
                    "vectorized", result.total(),
                    "message", "配置已保存，已向量化 " + result.total() + " 条记录。向量表：" + name + "_向量"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "configId", config.getId(),
                    "warning", "配置已保存，但向量化失败：" + e.getMessage() + "。请稍后手动同步。"
            ));
        }
    }

    /**
     * 手动同步：重新向量化某个配置的数据（增量更新）。
     * Body: { "configId": "..." } 或 { "name": "短视频爆款样本库" }
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@RequestBody Map<String, String> body) {
        String configId = body.get("configId");
        if (configId == null || configId.isBlank()) {
            String name = body.get("name");
            if (name != null && !name.isBlank()) {
                BitableConfig cfg = configService.getConfigByName(name);
                if (cfg != null) configId = cfg.getId();
            }
        }
        if (configId == null || configId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请提供 configId 或 name"));
        }
        try {
            PostgresVectorStoreService.VectorizeResult result = vectorStore.vectorizeBitables(configId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "vectorized", result.total(),
                    "message", "同步完成，更新了 " + result.total() + " 条记录"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
