package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class FeishuBitableService {
    private static final Logger log = LoggerFactory.getLogger(FeishuBitableService.class);
    private static final String FEISHU_BASE_URL = "https://open.feishu.cn/open-apis";

    @Value("${feishu.api.app-id:}")
    private String appId;

    @Value("${feishu.api.app-secret:}")
    private String appSecret;

    private String cachedAccessToken;
    private long accessTokenExpireTime;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final Map<String, CachedBitable> bitableCache = new HashMap<>();

    private static class CachedBitable {
        final String appToken;
        final String name;
        final List<TableInfo> tables;
        final long timestamp;

        CachedBitable(String appToken, String name, List<TableInfo> tables) {
            this.appToken = appToken;
            this.name = name;
            this.tables = tables;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30 * 60 * 1000;
        }
    }

    public static class TableInfo {
        private final String tableId;
        private final String name;

        public TableInfo(String tableId, String name) {
            this.tableId = tableId;
            this.name = name;
        }

        public String getTableId() { return tableId; }
        public String getName() { return name; }
    }

    public String getAccessToken(String providedToken) {
        if (providedToken != null && !providedToken.isBlank()) {
            return providedToken;
        }

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            log.error("未配置飞书应用凭证");
            return null;
        }

        if (cachedAccessToken != null && System.currentTimeMillis() < accessTokenExpireTime) {
            return cachedAccessToken;
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_BASE_URL + "/auth/v3/tenant_access_token/internal"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                if (root.has("tenant_access_token")) {
                    cachedAccessToken = root.get("tenant_access_token").asText();
                    accessTokenExpireTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000);
                    return cachedAccessToken;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取飞书 access token 失败", e);
            return null;
        }
    }

    public String extractAppToken(String input) {
        if (input == null) return null;
        if (input.matches("^[a-zA-Z0-9_-]{10,}$")) {
            return input;
        }
        if (input.contains("bitable")) {
            int idx = input.indexOf("bitable/");
            if (idx >= 0) {
                String remainder = input.substring(idx + 8);
                int slashIdx = remainder.indexOf('/');
                return slashIdx > 0 ? remainder.substring(0, slashIdx) : remainder;
            }
        }
        return null;
    }

    public Map<String, Object> getBitableInfo(String accessToken, String appToken) {
        String actualToken = getAccessToken(accessToken);
        if (actualToken == null) {
            return Map.of("success", false, "error", "无法获取 Access Token");
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_BASE_URL + "/bitable/v1/apps/" + appToken))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + actualToken)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = new HashMap<>();

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode data = root.path("data");
                result.put("success", true);
                result.put("name", data.path("name").asText("未命名多维表格"));
                result.put("appToken", appToken);
                result.put("revision", data.path("revision").asLong(0));

                List<TableInfo> tables = getTables(actualToken, appToken);
                result.put("tables", tables);
                result.put("tablesCount", tables.size());

                bitableCache.put(appToken, new CachedBitable(appToken,
                        data.path("name").asText("未命名多维表格"), tables));

                return result;
            } else {
                result.put("success", false);
                result.put("error", "API 返回错误: " + resp.statusCode());
                result.put("response", resp.body());
                return result;
            }
        } catch (Exception e) {
            log.error("获取多维表格信息失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public List<TableInfo> getTables(String accessToken, String appToken) {
        String actualToken = getAccessToken(accessToken);
        if (actualToken == null) return Collections.emptyList();

        List<TableInfo> tables = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_BASE_URL + "/bitable/v1/apps/" + appToken + "/tables"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + actualToken)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode items = root.path("data").path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        tables.add(new TableInfo(
                                item.path("table_id").asText(),
                                item.path("name").asText()
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取表格列表失败", e);
        }
        return tables;
    }

    public Map<String, Object> getTableRecords(String accessToken, String appToken, String tableId,
                                               int pageSize, String marker) {
        String actualToken = getAccessToken(accessToken);
        if (actualToken == null) {
            return Map.of("success", false, "error", "无法获取 Access Token");
        }

        try {
            StringBuilder urlBuilder = new StringBuilder(FEISHU_BASE_URL)
                    .append("/bitable/v1/apps/").append(appToken)
                    .append("/tables/").append(tableId)
                    .append("/records?page_size=").append(pageSize);

            if (marker != null && !marker.isBlank()) {
                urlBuilder.append("&marker=").append(marker);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + actualToken)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = new HashMap<>();

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode data = root.path("data");

                result.put("success", true);
                result.put("total", data.path("total").asLong(0));
                result.put("hasMore", data.path("has_more").asBoolean(false));
                result.put("marker", data.path("marker").asText(null));

                List<Map<String, Object>> records = new ArrayList<>();
                JsonNode items = data.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("recordId", item.path("record_id").asText());
                        record.put("fields", convertFields(item.path("fields")));
                        records.add(record);
                    }
                }
                result.put("records", records);
                result.put("recordsCount", records.size());

                return result;
            } else {
                result.put("success", false);
                result.put("error", "API 返回错误: " + resp.statusCode());
                result.put("response", resp.body());
                return result;
            }
        } catch (Exception e) {
            log.error("获取表格记录失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> getAllRecords(String accessToken, String appToken, String tableId) {
        String actualToken = getAccessToken(accessToken);
        if (actualToken == null) {
            return Map.of("success", false, "error", "无法获取 Access Token");
        }

        List<Map<String, Object>> allRecords = new ArrayList<>();
        String marker = null;
        int totalCount = 0;

        try {
            do {
                Map<String, Object> page = getTableRecords(actualToken, appToken, tableId, 500, marker);
                if (!(Boolean) page.getOrDefault("success", false)) {
                    return page;
                }

                totalCount = ((Number) page.getOrDefault("total", 0)).intValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pageRecords = (List<Map<String, Object>>) page.get("records");
                allRecords.addAll(pageRecords);

                marker = (String) page.get("marker");
                if (marker == null || marker.isBlank()) {
                    break;
                }

            } while (allRecords.size() < totalCount);

            return Map.of(
                    "success", true,
                    "total", totalCount,
                    "records", allRecords,
                    "recordsCount", allRecords.size()
            );

        } catch (Exception e) {
            log.error("获取所有记录失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> searchRecords(String accessToken, String appToken, String tableId,
                                            String fieldName, String searchValue) {
        Map<String, Object> allData = getAllRecords(accessToken, appToken, tableId);
        if (!(Boolean) allData.getOrDefault("success", false)) {
            return allData;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) allData.get("records");
        List<Map<String, Object>> matched = new ArrayList<>();

        String searchLower = searchValue.toLowerCase();

        for (Map<String, Object> record : records) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) record.get("fields");

            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String fieldValue = String.valueOf(entry.getValue()).toLowerCase();
                if (fieldValue.contains(searchLower)) {
                    matched.add(record);
                    break;
                }
            }
        }

        return Map.of(
                "success", true,
                "matchedCount", matched.size(),
                "matchedRecords", matched
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertFields(JsonNode fieldsNode) {
        Map<String, Object> fields = new HashMap<>();
        if (fieldsNode == null || !fieldsNode.isObject()) {
            return fields;
        }

        Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isTextual()) {
                fields.put(key, value.asText());
            } else if (value.isNumber()) {
                fields.put(key, value.numberValue());
            } else if (value.isBoolean()) {
                fields.put(key, value.asBoolean());
            } else if (value.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : value) {
                    if (item.isTextual()) {
                        list.add(item.asText());
                    } else if (item.isNumber()) {
                        list.add(item.numberValue());
                    } else {
                        list.add(item.toString());
                    }
                }
                fields.put(key, list);
            } else {
                fields.put(key, value.toString());
            }
        }
        return fields;
    }

    public void clearCache() {
        bitableCache.clear();
    }
}
