package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {
    @Value("${embedding.api.key:}")
    private String apiKey;

    @Value("${embedding.api.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${embedding.api.model:text-embedding-v3}")
    private String model;

    @Value("${embedding.api.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public List<Double> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 embedding.api.key，无法生成向量");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待向量化文本不能为空");
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            ArrayNode input = body.putArray("input");
            input.add(text);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Embedding API 返回 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode vectorNode = root.path("data").path(0).path("embedding");
            if (!vectorNode.isArray() || vectorNode.isEmpty()) {
                throw new RuntimeException("Embedding 响应格式异常: " + truncate(resp.body(), 300));
            }

            List<Double> vector = new ArrayList<>(vectorNode.size());
            for (JsonNode item : vectorNode) {
                vector.add(item.asDouble());
            }
            return vector;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("生成向量失败: " + e.getMessage(), e);
        }
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
