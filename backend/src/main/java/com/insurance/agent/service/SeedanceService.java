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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AtlasCloud Seedance 2.0 Fast (reference-to-video) 视频生成服务。
 *
 * 就啊时间的话
 * 流程：
 *  1. DeepSeek 把口播脚本拆成 ≤10 秒的多段，并为每段生成英文提示词
 *  2. 对每段串行调用 AtlasCloud POST /api/v1/model/generateVideo
 *  3. 轮询 GET /api/v1/model/prediction/{id} 直到完成，返回所有片段 URL
 */
@Service
public class SeedanceService {

    private static final Logger log = LoggerFactory.getLogger(SeedanceService.class);

    private static final int SPEECH_CHARS_PER_SECOND = 5;
    private static final int MAX_SEGMENT_SECONDS     = 10;
    private static final int MAX_CHARS_PER_SEGMENT   = SPEECH_CHARS_PER_SECOND * MAX_SEGMENT_SECONDS;

    private final DeepSeekService deepSeek;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${seedance.api.key:}")
    private String apiKey;

    @Value("${seedance.api.base-url:https://api.atlascloud.ai}")
    private String baseUrl;

    @Value("${seedance.api.model:bytedance/seedance-2.0-fast/reference-to-video}")
    private String model;

    @Value("${seedance.api.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${seedance.api.poll-interval-seconds:8}")
    private int pollIntervalSeconds;

    @Value("${seedance.api.poll-timeout-seconds:900}")
    private int pollTimeoutSeconds;

    @Value("${avatar.upload-dir:uploads/avatars}")
    private String avatarUploadDir;

    public SeedanceService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    // ─── 公共入口 ───────────────────────────────────────────────────

    /**
     * 生成口播视频：脚本 → 分段 → 逐段调用 AtlasCloud → 返回片段 URL 列表。
     *
     * @param script             口播脚本全文
     * @param characterImageUrl  人物参考图 URL（必须）
     * @param backgroundImageUrl 背景图 URL（可选）
     * @param style              风格补充说明（可选）
     */
    public List<SegmentResult> generateSegments(
            String script,
            String characterImageUrl,
            String backgroundImageUrl,
            String style) {

        if (isBlank(script)) throw new IllegalArgumentException("口播脚本不能为空");

        List<Segment> segments = splitScript(script, style);
        log.info("[Seedance] 脚本已拆分为 {} 段", segments.size());
        return submitAndPollSequential(segments, characterImageUrl, backgroundImageUrl);
    }

    /**
     * 直接使用前端已编辑好的分镜段生成视频，跳过 DeepSeek 拆分。
     */
    public List<SegmentResult> generateSegmentsDirect(
            List<Segment> segments,
            String characterImageUrl,
            String backgroundImageUrl) {

        if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("分镜段列表不能为空");
        log.info("[Seedance] 直接使用前端分镜，共 {} 段", segments.size());
        return submitAndPollSequential(segments, characterImageUrl, backgroundImageUrl);
    }

    /** 提交一段 → 轮询完成 → 提交下一段，避免批量提交后第二段因等待过久而失效 */
    private List<SegmentResult> submitAndPollSequential(
            List<Segment> segments, String characterImageUrl, String backgroundImageUrl) {

        List<SegmentResult> results = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            log.info("[Seedance] 提交第 {}/{} 段，预估 {} 秒", i + 1, segments.size(), seg.durationEstimate());
            String predictionId = submitTask(seg, characterImageUrl, backgroundImageUrl);
            log.info("[Seedance] 轮询第 {}/{} 段，predictionId={}", i + 1, segments.size(), predictionId);
            String videoUrl = pollTask(predictionId);
            results.add(new SegmentResult(i + 1, seg.script(), seg.durationEstimate(), videoUrl));
        }
        return results;
    }

    // ─── Step 1: DeepSeek 脚本拆分 ────────────────────────────────

    private List<Segment> splitScript(String script, String style) {
        String systemPrompt = """
                # 角色
                你是专业的口播视频脚本拆分助手，专为 AI 视频生成 API（每次最多生成 10 秒）准备输入。

                # 任务
                把用户给的口播脚本拆成多个片段，每片段满足：
                - 口播字数 ≤ 50 字（对应 ≤10 秒，按 5 字/秒估算）
                - 在自然停顿处（句号、问号、感叹号、换行）切分
                - 不得改动任何口播原文

                同时为每个片段生成 AI 视频生成提示词，要求：
                - 专注「真人口播」风格：正脸出镜、自然表情、直视镜头
                - 用英文描述画面（模型要求英文 prompt）
                - 简洁有力，包含：景别、情绪、动作/手势（如有）
                - 不要加入与脚本无关的场景设定

                # 输出格式（严格 JSON，不要加任何注释或代码块符号）
                {
                  "segments": [
                    {
                      "script": "这段的口播原文",
                      "prompt": "English video generation prompt for this segment",
                      "duration_estimate": 6
                    }
                  ]
                }
                """;

        String userPrompt = "请拆分以下口播脚本：\n\n" + script.trim() +
                (isBlank(style) ? "" : "\n\n风格说明：" + style.trim());

        String raw;
        try {
            raw = deepSeek.chat(systemPrompt, userPrompt, null);
        } catch (Exception e) {
            log.warn("[Seedance] DeepSeek 拆分失败，降级为简单按句切分: {}", e.getMessage());
            return fallbackSplit(script);
        }

        try {
            String json = raw.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
            JsonNode root = mapper.readTree(json);
            JsonNode segs = root.path("segments");
            List<Segment> result = new ArrayList<>();
            for (JsonNode s : segs) {
                result.add(new Segment(
                        s.path("script").asText("").trim(),
                        s.path("prompt").asText("").trim(),
                        Math.max(3, Math.min(10, s.path("duration_estimate").asInt(8)))));
            }
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("[Seedance] JSON 解析失败，降级为简单切分: {}", e.getMessage());
        }
        return fallbackSplit(script);
    }

    private List<Segment> fallbackSplit(String script) {
        List<String> parts = new ArrayList<>();
        String[] sentences = script.split("(?<=[。！？\\n])");
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (buf.length() + s.length() > MAX_CHARS_PER_SEGMENT && !buf.isEmpty()) {
                parts.add(buf.toString().trim());
                buf = new StringBuilder();
            }
            buf.append(s);
        }
        if (!buf.isEmpty()) parts.add(buf.toString().trim());

        List<Segment> result = new ArrayList<>();
        for (String part : parts) {
            int dur = Math.max(3, Math.min(10, (int) Math.ceil((double) part.length() / SPEECH_CHARS_PER_SECOND)));
            result.add(new Segment(part,
                    "A professional insurance advisor looking directly at camera, " +
                    "close-up talking head, natural expression, clean background", dur));
        }
        return result;
    }

    // ─── Step 2: 提交 AtlasCloud 任务 ─────────────────────────────
    // POST /api/v1/model/generateVideo
    // 响应：data.id = predictionId

    private String submitTask(Segment seg, String characterImageUrl, String backgroundImageUrl) {
        try {
            // reference_images：人物图可选（测试时可不传），背景图也可选
            ArrayNode refImages = mapper.createArrayNode();
            if (!isBlank(characterImageUrl)) {
                refImages.add(resolveImageRef(characterImageUrl));
            }
            if (!isBlank(backgroundImageUrl)) {
                refImages.add(resolveImageRef(backgroundImageUrl));
            }

            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", buildSegmentPrompt(seg));
            body.set("reference_images", refImages);
            body.set("reference_videos", mapper.createArrayNode());
            body.set("reference_audios", mapper.createArrayNode());
            body.put("duration", seg.durationEstimate());
            body.put("resolution", "720p");
            body.put("ratio", "adaptive");
            body.put("generate_audio", true);
            body.put("watermark", false);
            body.put("return_last_frame", false);

            String payload = mapper.writeValueAsString(body);
            log.debug("[Seedance] submit payload={}", truncate(payload, 400));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + "/api/v1/model/generateVideo"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + requiredApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("[Seedance] submit status={} body={}", resp.statusCode(), truncate(resp.body(), 400));

            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("AtlasCloud 提交任务失败 " + resp.statusCode() + ": " + truncate(resp.body(), 400));
            }

            JsonNode root = mapper.readTree(resp.body());
            // 实际响应格式：顶层直接有 "id"（扁平），兼容旧有 data.id 嵌套格式
            String predictionId = root.path("id").asText(null);
            if (isBlank(predictionId)) predictionId = root.path("data").path("id").asText(null);
            if (!isBlank(predictionId)) return predictionId;

            throw new RuntimeException("AtlasCloud 响应中未找到 id，响应: " + truncate(resp.body(), 300));

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("提交 AtlasCloud 任务异常: " + e.getMessage(), e);
        }
    }

    private String buildSegmentPrompt(Segment seg) {
        return seg.prompt() + ". The character is saying: \"" + seg.script() + "\"";
    }

    // ─── Step 3: 轮询任务 ─────────────────────────────────────────
    // GET /api/v1/model/prediction/{predictionId}
    // 响应：data.status = "completed"/"succeeded"/"failed"，data.outputs[0] = video URL

    private String pollTask(String predictionId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(pollTimeoutSeconds).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                String result = queryTask(predictionId);
                if (result != null) return result;
            } catch (Exception e) {
                log.warn("[Seedance] 查询任务 {} 失败，将重试: {}", predictionId, e.getMessage());
            }
            sleep(pollIntervalSeconds);
        }
        throw new RuntimeException("AtlasCloud 任务 " + predictionId + " 轮询超时（" + pollTimeoutSeconds + "s）");
    }

    private String queryTask(String predictionId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(baseUrl) + "/api/v1/model/prediction/" + predictionId))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + requiredApiKey())
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("查询失败 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }

        JsonNode root = mapper.readTree(resp.body());
        // 实际响应为扁平格式：status / outputs 在顶层；兼容 data.* 嵌套格式
        JsonNode node = root.has("status") ? root : root.path("data");
        String status = node.path("status").asText("");
        log.debug("[Seedance] predictionId={} status={}", predictionId, status);

        if (status.equals("failed")) {
            String error = node.path("error").asText("Generation failed");
            throw new RuntimeException("AtlasCloud 任务失败 predictionId=" + predictionId + " error=" + error);
        }

        if (status.equals("completed") || status.equals("succeeded")) {
            // outputs 是字符串数组，第 0 个元素为视频 URL
            JsonNode outputs = node.path("outputs");
            if (outputs.isArray() && !outputs.isEmpty()) {
                String url = outputs.get(0).asText("");
                if (!isBlank(url)) return url;
            }
            log.warn("[Seedance] 任务已完成但 outputs 为空，resp={}", truncate(resp.body(), 500));
        }
        return null; // 未完成，继续轮询
    }

    // ─── 工具 ──────────────────────────────────────────────────────

    /**
     * 将图片 URL 转为可供 AtlasCloud 外网访问的形式。
     * 如果是 localhost URL，直接读本地文件并转 base64 data URL 发送，
     * 避免 AtlasCloud 服务器因无法访问内网地址而报 400。
     */
    private String resolveImageRef(String imageUrl) {
        if (isBlank(imageUrl)) return imageUrl;
        if (!imageUrl.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?/.*")) {
            return imageUrl; // 公网 URL 直接使用
        }
        try {
            // URL 形如 http://localhost:8888/api/avatar/image/{filename}
            String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            Path filePath = Paths.get(avatarUploadDir).resolve(filename);
            byte[] bytes = Files.readAllBytes(filePath);
            String mime = Files.probeContentType(filePath);
            if (mime == null) mime = "image/jpeg";
            String b64 = Base64.getEncoder().encodeToString(bytes);
            log.info("[Seedance] 本地图片 {} 转 base64（{}字节）", filename, bytes.length);
            return "data:" + mime + ";base64," + b64;
        } catch (Exception e) {
            log.warn("[Seedance] 本地图片转 base64 失败，直接使用 URL: {}", e.getMessage());

            return imageUrl;
        }
    }

    private String requiredApiKey() {
        if (isBlank(apiKey)) throw new IllegalStateException("未配置 SEEDANCE_API_KEY，无法调用 AtlasCloud。");
        return apiKey.trim();
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(Math.max(1, seconds) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("轮询被中断", e);
        }
    }

    private String trimSlash(String url) {
        return (url == null || url.isBlank()) ? "" : url.stripTrailing().replaceAll("/+$", "");
    }

    private String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "...");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ─── 数据类 ────────────────────────────────────────────────────

    public record Segment(String script, String prompt, int durationEstimate) {}

    public record SegmentResult(int index, String script, int durationEstimate, String videoUrl) {}
}
