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
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DeepSeekService {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekService.class);

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.api.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.api.chat-model:deepseek-chat}")
    private String chatModel;

    @Value("${deepseek.api.reasoner-model:deepseek-reasoner}")
    private String reasonerModel;

    @Value("${deepseek.api.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${deepseek.api.fallback-key:}")
    private String fallbackApiKey;

    @Value("${deepseek.api.fallback-base-url:https://api.deepseek.com}")
    private String fallbackBaseUrl;

    @Value("${deepseek.api.fallback-chat-model:deepseek-chat}")
    private String fallbackChatModel;

    @Value("${deepseek.api.fallback-reasoner-model:deepseek-reasoner}")
    private String fallbackReasonerModel;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public DeepSeekService(HttpClient sharedHttpClient) {
        this.http = sharedHttpClient;
    }

    public String chat(String systemPrompt, String userPrompt, String requestedModel) {
        try {
            return doChat(systemPrompt, userPrompt, requestedModel, false);
        } catch (RuntimeException e) {
            if (isRetryable(e) && hasFallbackConfig()) {
                log.warn("""

                        ╔══════════════════════════════════════╗
                        ║  AI 兜底：主模型失败 → DeepSeek  ║
                        ║  原因: {}                          ║
                        ╚══════════════════════════════════════╝
                        """, e.getMessage());
                return doChat(systemPrompt, userPrompt, requestedModel, true);
            }
            if (isRetryable(e) && !hasFallbackConfig()) {
                log.warn("[AI] 主模型失败且未配置兜底 API Key，直接报错。原因: {}", e.getMessage());
            }
            throw e;
        }
    }

    private String doChat(String systemPrompt, String userPrompt, String requestedModel, boolean useFallback) {
        String effectiveApiKey = useFallback ? fallbackApiKey : apiKey;
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置 AI_API_KEY 或 DEEPSEEK_API_KEY 环境变量，后端无法调用 AI 模型。");
        }
        String model = resolveModel(requestedModel, useFallback);
        String effectiveBaseUrl = useFallback ? fallbackBaseUrl : baseUrl;
        log.info("[AI 请求] model={} fallback={} system={} user={}",
                model, useFallback, truncate(systemPrompt, 200), truncate(userPrompt, 500));
        writePromptToFile(model, systemPrompt, userPrompt);
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
            }
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            String payload = mapper.writeValueAsString(body);
            String endpoint = trimSlash(effectiveBaseUrl) + "/chat/completions";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effectiveApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[AI 异常] model={} status={} body={}", model, resp.statusCode(), truncate(resp.body(), 500));
                throw new RuntimeException("AI API 返回 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode choice = root.path("choices").path(0).path("message").path("content");
            if (choice.isMissingNode() || choice.isNull()) {
                log.warn("[AI 异常] 响应格式异常 model={} body={}", model, truncate(resp.body(), 500));
                throw new RuntimeException("AI 响应格式异常: " + truncate(resp.body(), 300));
            }
            String result = choice.asText();
            log.info("[AI 响应] model={} 长度={} 内容={}", model, result.length(), truncate(result, 500));
            return result;
        } catch (RuntimeException e) {
            log.error("[AI 异常] model={} fallback={} 错误={}", model, useFallback, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[AI 异常] model={} fallback={} 错误={}", model, useFallback, e.getMessage());
            throw new RuntimeException("调用 AI 模型失败: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(Throwable e) {
        if (isTimeoutOr5xx(e)) return true;
        Throwable cause = e.getCause();
        while (cause != null) {
            if (isTimeoutOr5xx(cause)) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean isTimeoutOr5xx(Throwable e) {
        if (e instanceof HttpTimeoutException) return true;
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.matches(".*\\b[45]\\d{2}\\b.*");
    }

    private boolean hasFallbackConfig() {
        return fallbackApiKey != null && !fallbackApiKey.isBlank();
    }

    public String resolveModel(String requested, boolean useFallback) {
        if (requested == null || requested.isBlank()) {
            return useFallback ? fallbackChatModel : chatModel;
        }
        return switch (requested.trim().toLowerCase()) {
            case "reasoner", "deepseek-reasoner", "增强", "enhanced" ->
                    useFallback ? fallbackReasonerModel : reasonerModel;
            case "chat", "deepseek-chat" ->
                    useFallback ? fallbackChatModel : chatModel;
            default -> requested.trim();
        };
    }

    public String resolveModel(String requested) {
        return resolveModel(requested, false);
    }

    private String trimSlash(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private void writePromptToFile(String model, String systemPrompt, String userPrompt) {
        try {
            Path dir = Paths.get("prompts");
            Files.createDirectories(dir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String safeModel = model.replaceAll("[^a-zA-Z0-9_-]", "_");
            Path file = dir.resolve("prompt_" + timestamp + "_" + safeModel + ".txt");
            String content = "=== System Prompt ===\n" + (systemPrompt != null ? systemPrompt : "")
                    + "\n\n=== User Prompt ===\n" + (userPrompt != null ? userPrompt : "")
                    + "\n";
            Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
            log.info("[Prompt 已保存] {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[Prompt 保存失败] {}", e.getMessage());
        }
    }
}
