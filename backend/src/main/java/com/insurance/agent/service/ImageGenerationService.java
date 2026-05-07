package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImageGenerationService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenerationService.class);

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\"'<>]+");
    private static final Pattern DATA_IMAGE_PATTERN = Pattern.compile("data:image/[^;\\s)\"'<>]+;base64,[A-Za-z0-9+/=]+");

    @Value("${image.api.provider:hiapi}")
    private String provider;

    @Value("${image.api.key:}")
    private String apiKey;

    @Value("${image.api.base-url:https://api.hiapi.ai/v1}")
    private String baseUrl;

    @Value("${image.api.model:gpt-image-2}")
    private String model;

    @Value("${image.api.size:1:1}")
    private String size;

    @Value("${image.api.quality:medium}")
    private String quality;

    @Value("${image.api.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${image.api.watermark:false}")
    private boolean watermark;

    @Value("${image.seedream.key:${ARK_API_KEY:}}")
    private String seedreamApiKey;

    @Value("${image.seedream.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String seedreamBaseUrl;

    @Value("${image.seedream.model:doubao-seedream-5-0-260128}")
    private String seedreamModel;

    @Value("${image.seedream.size:2K}")
    private String seedreamSize;

    @Value("${image.seedream.watermark:false}")
    private boolean seedreamWatermark;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, String customSize) {
        return generateWithProvider(prompt, customSize, null);
    }

    public String generateSeedream(String prompt) {
        return generateWithProvider(prompt, seedreamSize, "seedream");
    }

    public String generateSeedream(String prompt, String customSize) {
        return generateWithProvider(prompt, isBlank(customSize) ? seedreamSize : customSize, "seedream");
    }

    private String generateWithProvider(String prompt, String customSize, String providerOverride) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("图片提示词不能为空");
        }
        String normalizedProvider = normalizeProvider(providerOverride);
        String effectiveApiKey = "seedream".equals(normalizedProvider) ? seedreamApiKey : apiKey;
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置图片生成 API Key, 后端无法调用 " + normalizedProvider + " 图片生成 API。");
        }

        String effectiveSize = isBlank(customSize) ? size : customSize;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "seedream".equals(normalizedProvider) ? seedreamModel : model);
            String endpoint = "/images/generations";

            if ("hiapi".equals(normalizedProvider)) {
                endpoint = "/chat/completions";
                body.put("stream", false);
                ObjectNode message = mapper.createObjectNode();
                message.put("role", "user");
                message.put("content", prompt.trim());
                body.putArray("messages").add(message);

                ObjectNode imageConfig = mapper.createObjectNode();
                imageConfig.put("aspect_ratio", toAspectRatio(effectiveSize));
                ObjectNode google = mapper.createObjectNode();
                google.set("image_config", imageConfig);
                ObjectNode extraBody = mapper.createObjectNode();
                extraBody.set("google", google);
                body.set("extra_body", extraBody);
            } else if ("seedream".equals(normalizedProvider)) {
                body.put("prompt", prompt.trim());
                body.put("sequential_image_generation", "disabled");
                body.put("response_format", "url");
                if (!isBlank(effectiveSize)) body.put("size", effectiveSize);
                body.put("stream", false);
                body.put("watermark", seedreamWatermark);
            } else if ("volcengine".equals(normalizedProvider) || "ark".equals(normalizedProvider)) {
                body.put("prompt", prompt.trim());
                if (!isBlank(effectiveSize)) body.put("size", effectiveSize);
                body.put("response_format", "url");
                body.put("stream", false);
                body.put("watermark", watermark);
            } else if ("siliconflow".equals(normalizedProvider)) {
                body.put("prompt", prompt.trim());
                if (!isBlank(effectiveSize)) body.put("image_size", effectiveSize);
            } else {
                body.put("prompt", prompt.trim());
                body.put("n", 1);
                if (!isBlank(effectiveSize)) body.put("size", effectiveSize);
                if (!isBlank(quality)) body.put("quality", quality);
                body.put("output_format", "png");
            }

            String effectiveBaseUrl = "seedream".equals(normalizedProvider) ? seedreamBaseUrl : baseUrl;
            String requestUrl = trimTrailingSlash(effectiveBaseUrl) + endpoint;
            log.info("Image API request provider={} url={} model={} size={}",
                    normalizedProvider, requestUrl, body.path("model").asText(), effectiveSize);

            String payload = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effectiveApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Image API non-2xx: {} body={}", resp.statusCode(), resp.body());
                throw new RuntimeException("图片生成 API 返回 " + resp.statusCode() + ": "
                        + truncate(resp.body(), 300));
            }

            JsonNode root = mapper.readTree(resp.body());
            String imageUrl = readText(root.path("images").path(0).path("url"));
            if (!isBlank(imageUrl)) return imageUrl;

            imageUrl = readText(root.path("data").path(0).path("url"));
            if (!isBlank(imageUrl)) return imageUrl;

            String b64 = readText(root.path("data").path(0).path("b64_json"));
            if (!isBlank(b64)) return "data:image/png;base64," + b64;

            imageUrl = readChatCompletionImage(root);
            if (!isBlank(imageUrl)) return imageUrl;

            throw new RuntimeException("图片生成 API 响应格式异常: " + truncate(resp.body(), 300));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用图片生成 API 失败: " + e.getMessage(), e);
        }
    }

    public String modelLabel() {
        return normalizeProvider() + ":" + model;
    }

    public String seedreamModelLabel() {
        return "seedream:" + seedreamModel;
    }

    private String normalizeProvider() {
        return normalizeProvider(null);
    }

    private String normalizeProvider(String providerOverride) {
        String value = isBlank(providerOverride) ? provider : providerOverride;
        if (value == null || value.isBlank()) return "hiapi";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String toAspectRatio(String sizeOrRatio) {
        if (isBlank(sizeOrRatio)) return "1:1";
        String value = sizeOrRatio.trim();
        if (value.matches("\\d+\\s*:\\s*\\d+")) {
            return value.replaceAll("\\s+", "");
        }
        String[] parts = value.toLowerCase(Locale.ROOT).split("x");
        if (parts.length != 2) return "1:1";
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) return "1:1";
            int gcd = gcd(width, height);
            return (width / gcd) + ":" + (height / gcd);
        } catch (NumberFormatException e) {
            return "1:1";
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    private String readChatCompletionImage(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray()) return "";
        for (JsonNode choice : choices) {
            String found = findImageInNode(choice.path("message"));
            if (!isBlank(found)) return found;
        }
        return "";
    }

    private String findImageInNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) {
            return extractUrl(node.asText(""));
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findImageInNode(item);
                if (!isBlank(found)) return found;
            }
            return "";
        }
        String url = readText(node.path("url"));
        if (!isBlank(url)) return url;
        url = readText(node.path("image_url").path("url"));
        if (!isBlank(url)) return url;
        String b64 = readText(node.path("b64_json"));
        if (!isBlank(b64)) return "data:image/png;base64," + b64;
        String contentUrl = findImageInNode(node.path("content"));
        if (!isBlank(contentUrl)) return contentUrl;
        String textUrl = findImageInNode(node.path("text"));
        if (!isBlank(textUrl)) return textUrl;
        return "";
    }

    private static String extractUrl(String text) {
        if (isBlank(text)) return "";
        Matcher dataImageMatcher = DATA_IMAGE_PATTERN.matcher(text);
        if (dataImageMatcher.find()) return dataImageMatcher.group();
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private static String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        return node.asText("");
    }

    private static String trimTrailingSlash(String s) {
        if (s == null || s.isBlank()) return "";
        String out = s.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
