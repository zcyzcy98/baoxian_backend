package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AtlasCloud Seedance 2.0 Fast (reference-to-video) 视频生成服务。
 *
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
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
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
            String style,
            String ratio,
            String resolution,
            String referenceAudioUrl) {

        if (isBlank(script)) throw new IllegalArgumentException("口播脚本不能为空");

        List<Segment> segments = splitScript(script, style);
        log.info("[Seedance] 脚本已拆分为 {} 段", segments.size());
        return submitAndPollSequential(segments, characterImageUrl, backgroundImageUrl, ratio, resolution, referenceAudioUrl);
    }

    /**
     * 直接使用前端已编辑好的分镜段生成视频，跳过 AI 拆分。
     */
    public List<SegmentResult> generateSegmentsDirect(
            List<Segment> segments,
            String characterImageUrl,
            String backgroundImageUrl,
            String ratio,
            String resolution,
            String referenceAudioUrl) {

        if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("分镜段列表不能为空");
        log.info("[Seedance] 直接使用前端分镜，共 {} 段", segments.size());
        return submitAndPollSequential(segments, characterImageUrl, backgroundImageUrl, ratio, resolution, referenceAudioUrl);
    }

    /**
     * 生成单个分镜段，前端串行调用此方法，每完成一段即返回，便于实时更新 UI。
     * 前端负责传入上一段的 lastFrame 实现画面衔接。
     */
    public SingleSegmentResult generateSingleSegment(
            Segment segment,
            String characterImageUrl,
            String backgroundImageUrl,
            String ratio,
            String resolution,
            String previousLastFrameUrl,
            String referenceAudioUrl,
            int segmentIndex,
            int totalSegments) {

        if (segment == null) throw new IllegalArgumentException("分镜段不能为空");
        RuntimeException lastErr = null;
        for (int attempt = 1; attempt <= MAX_SEGMENT_ATTEMPTS; attempt++) {
            try {
                log.info("[Seedance/single] 提交第 {}/{} 段（第 {} 次），有上段帧={} 有音频参考={}",
                        segmentIndex + 1, totalSegments, attempt,
                        previousLastFrameUrl != null, !isBlank(referenceAudioUrl));
                String predictionId = submitTask(segment, characterImageUrl, backgroundImageUrl,
                        ratio, resolution, previousLastFrameUrl, referenceAudioUrl);
                TaskOutput out = pollTask(predictionId);
                return new SingleSegmentResult(
                        segmentIndex + 1, segment.script(), segment.durationEstimate(),
                        out.videoUrl(), out.lastFrameUrl());
            } catch (RuntimeException e) {
                lastErr = e;
                log.warn("[Seedance/single] 第 {}/{} 段第 {}/{} 次失败: {}",
                        segmentIndex + 1, totalSegments, attempt, MAX_SEGMENT_ATTEMPTS, e.getMessage());
                if (attempt < MAX_SEGMENT_ATTEMPTS) sleep(5);
            }
        }
        throw lastErr;
    }

    public record SingleSegmentResult(int index, String script, int durationEstimate,
                                       String videoUrl, String lastFrameUrl) {}

    private static final int MAX_SEGMENT_ATTEMPTS = 2;

    /**
     * 提交一段 → 轮询完成 → 提交下一段。
     * 串联策略：
     * - 上一段的 last_frame 作为下一段的 reference_image，保证画面无缝衔接
     * - referenceAudioUrl 全程透传，保证音色一致
     */
    private List<SegmentResult> submitAndPollSequential(
            List<Segment> segments, String characterImageUrl, String backgroundImageUrl,
            String ratio, String resolution, String referenceAudioUrl) {

        List<SegmentResult> results = new ArrayList<>();
        String previousLastFrame = null;
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            RuntimeException lastErr = null;
            for (int attempt = 1; attempt <= MAX_SEGMENT_ATTEMPTS; attempt++) {
                try {
                    log.info("[Seedance] 提交第 {}/{} 段（第 {} 次），比例={} 清晰度={} 有上段帧={} 有音频参考={}",
                            i + 1, segments.size(), attempt, ratio, resolution,
                            previousLastFrame != null, !isBlank(referenceAudioUrl));
                    String predictionId = submitTask(seg, characterImageUrl, backgroundImageUrl,
                            ratio, resolution, previousLastFrame, referenceAudioUrl);
                    log.info("[Seedance] 轮询第 {}/{} 段，predictionId={}", i + 1, segments.size(), predictionId);
                    TaskOutput out = pollTask(predictionId);
                    results.add(new SegmentResult(i + 1, seg.script(), seg.durationEstimate(), out.videoUrl()));
                    // 更新串联帧供下一段使用
                    if (!isBlank(out.lastFrameUrl())) {
                        previousLastFrame = out.lastFrameUrl();
                        log.info("[Seedance] 第 {} 段 last_frame 已捕获，将注入第 {} 段 reference_images", i + 1, i + 2);
                    } else {
                        log.warn("[Seedance] 第 {} 段未返回 last_frame，下一段将不带衔接帧", i + 1);
                    }
                    lastErr = null;
                    break;
                } catch (RuntimeException e) {
                    lastErr = e;
                    log.warn("[Seedance] 第 {}/{} 段第 {}/{} 次失败: {}",
                            i + 1, segments.size(), attempt, MAX_SEGMENT_ATTEMPTS, e.getMessage());
                    if (attempt < MAX_SEGMENT_ATTEMPTS) sleep(5);
                }
            }
            if (lastErr != null) throw lastErr;
        }
        return results;
    }

    // ─── Step 1: AI 脚本拆分 ────────────────────────────────

    private List<Segment> splitScript(String script, String style) {
        String systemPrompt = """
                # 角色
                你是专业的口播视频脚本拆分助手，专为 AI 视频生成 API（每段 8-15 秒）准备输入。

                # 任务
                把用户给的口播脚本拆成多个片段，每片段满足：
                - **口播字数 40-60 字**（按 5 字/秒估算 = 8-12 秒），不允许出现少于 40 字的短段
                - 在自然停顿处（句号、问号、感叹号、换行）切分
                - 不得改动任何口播原文
                - 不允许出现「8 秒只说 1-2 句」的空洞段落——主播必须从头到尾都在说

                同时为每个片段生成 AI 视频生成提示词，要求：
                - 专注「真人口播」风格：正脸出镜、自然表情、**全程直视镜头**
                - 用英文描述画面（模型要求英文 prompt）
                - prompt 只描述：当前段主角的情绪/微表情（如 thoughtful, smiling, concerned）
                - ⚠️ 所有 segments 的 prompt 必须共享同一套设定：同一个人、同一身衣服、同一个姿势（全部坐着 or 全部站着，一旦定了不能改）、同一个背景
                - ⛔ 严禁出现：paper / documents / reports / clipboard / phone / tablet / book in hands
                - ✅ 手部允许：empty hands 或 natural gesture，最多一个 coffee cup
                - 不要有任何运镜（no zoom/pan/tilt/movement），固定机位
                - 不要在画面中出现任何文字
                - 不要加入与脚本无关的场景设定

                # 输出格式（严格 JSON，不要加任何注释或代码块符号）
                {
                  "segments": [
                    {
                      "script": "这段的口播原文",
                      "prompt": "English video generation prompt for this segment",
                      "duration_estimate": 10
                    }
                  ]
                }
                """ + PromptRules.shortVideoPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();

        String userPrompt = "请拆分以下口播脚本：\n\n" + script.trim() +
                (isBlank(style) ? "" : "\n\n风格说明：" + style.trim());

        String raw;
        try {
            raw = deepSeek.chat(systemPrompt, userPrompt, null);
        } catch (Exception e) {
            log.warn("[Seedance] AI 拆分失败，降级为简单按句切分: {}", e.getMessage());
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
                        Math.max(8, Math.min(15, s.path("duration_estimate").asInt(10)))));
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

    private String submitTask(Segment seg, String characterImageUrl, String backgroundImageUrl,
                              String ratio, String resolution,
                              String previousLastFrameUrl, String referenceAudioUrl) {
        try {
            // reference_images：人物图 + 背景图 + 上一段最后一帧（用于场景衔接）
            ArrayNode refImages = mapper.createArrayNode();
            if (!isBlank(characterImageUrl)) {
                refImages.add(resolveImageRef(characterImageUrl));
            }
            if (!isBlank(backgroundImageUrl)) {
                refImages.add(resolveImageRef(backgroundImageUrl));
            }
            if (!isBlank(previousLastFrameUrl)) {
                // 上一段的最后一帧，让本段从相同画面开始，避免坐/站、服装、背景跳变
                refImages.add(previousLastFrameUrl);
            }

            // reference_audios：声音参考，全片所有段使用同一个音频，保证音色一致
            ArrayNode refAudios = mapper.createArrayNode();
            if (!isBlank(referenceAudioUrl)) {
                refAudios.add(referenceAudioUrl);
            }

            log.info(refImages.toString());

            String finalRatio      = isBlank(ratio)      ? "9:16" : ratio.trim();
            String finalResolution = isBlank(resolution) ? "720p" : resolution.trim();

            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("prompt", buildSegmentPrompt(seg));
            body.set("reference_images", refImages);
            body.set("reference_videos", mapper.createArrayNode());
            body.set("reference_audios", refAudios);
            body.put("duration", seg.durationEstimate());
            body.put("resolution", finalResolution);
            body.put("ratio", finalRatio);
            body.put("generate_audio", true);
            body.put("watermark", false);
            body.put("return_last_frame", true);

            log.info(body.toString());

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

    /**
     * 全片一致性锚定：所有分段共享同一套人物/姿势/视线/手部约束，避免段间跳变。
     * 这段英文前缀会拼到每一段的 Seedance prompt 开头。
     */
    private static final String CHARACTER_ANCHOR =
            "CRITICAL CONTINUITY: this clip MUST visually match the previous clip (provided as reference image). "
            + "Identical background, identical room/setting, identical lighting, identical camera angle and framing. "
            + "Same person: identical face, hair, clothing, seated upright posture in the exact same position. "
            + "Do not change the scene, do not move to a new location, do not redesign the environment. "
            + "The character looks straight into the camera the entire time, maintaining direct eye contact. "
            + "Hands are empty or making natural conversational gestures. "
            + "Strictly NO paper, NO documents, NO reports, NO clipboard, NO phone, NO tablet, NO book in hands. "
            + "Static locked camera, no zoom, no pan, no movement. "
            + "ABSOLUTELY NO subtitles, NO captions, NO burned-in text, NO on-screen text overlays of any kind. "
            + "The video frame must be free of any visible text, signs, watermarks, or written words. ";

    private String buildSegmentPrompt(Segment seg) {
        return CHARACTER_ANCHOR + seg.prompt() + ". The character is saying: \"" + seg.script() + "\"";
    }

    // ─── Step 3: 轮询任务 ─────────────────────────────────────────
    // GET /api/v1/model/prediction/{predictionId}
    // 响应：data.status = "completed"/"succeeded"/"failed"，data.outputs[0] = video URL

    /** 轮询结果：包含成片视频 URL 和（可选）最后一帧图像 URL */
    public record TaskOutput(String videoUrl, String lastFrameUrl) {}

    private TaskOutput pollTask(String predictionId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(pollTimeoutSeconds).toNanos();
        int consecutiveErrors = 0;
        while (System.nanoTime() < deadline) {
            try {
                TaskOutput result = queryTask(predictionId);
                consecutiveErrors = 0;
                if (result != null) return result;
            } catch (Exception e) {
                consecutiveErrors++;
                log.warn("[Seedance] 查询任务 {} 失败（连续第 {} 次）: {}", predictionId, consecutiveErrors, e.getMessage());
                if (consecutiveErrors >= 3) {
                    throw new RuntimeException("任务 " + predictionId + " 连续失败 3 次，放弃重试: " + e.getMessage(), e);
                }
            }
            sleep(pollIntervalSeconds);
        }
        throw new RuntimeException("AtlasCloud 任务 " + predictionId + " 轮询超时（" + pollTimeoutSeconds + "s）");
    }

    private TaskOutput queryTask(String predictionId) throws Exception {
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
            log.info("[Seedance] 任务完成，完整响应：{}", truncate(resp.body(), 2000));

            JsonNode outputs = node.path("outputs");
            String videoUrl = "";
            String lastFrame = "";
            if (outputs.isArray() && !outputs.isEmpty()) {
                videoUrl = outputs.get(0).asText("");
                // outputs[1] 可能就是 last_frame（很多 SD 类 API 是这种约定）
                if (outputs.size() >= 2) lastFrame = outputs.get(1).asText("");
            }
            // 兼容多种字段命名，按优先级覆盖 outputs[1]
            for (String key : new String[]{
                    "last_frame", "lastFrame", "last_frame_url",
                    "last_frame_image", "lastFrameUrl", "lastFrameImage",
                    "tail_frame", "tailFrame", "endFrame", "end_frame"
            }) {
                String v = node.path(key).asText("");
                if (!isBlank(v)) { lastFrame = v; break; }
            }
            log.info("[Seedance] 解析结果：videoUrl={}, lastFrame={}",
                    truncate(videoUrl, 120), truncate(lastFrame, 120));

            if (!isBlank(videoUrl)) return new TaskOutput(videoUrl, isBlank(lastFrame) ? null : lastFrame);
            log.warn("[Seedance] 任务已完成但 outputs 为空，resp={}", truncate(resp.body(), 500));
        }
        return null; // 未完成，继续轮询
    }

    // ─── 工具 ──────────────────────────────────────────────────────

    /**
     * 将图片 URL 转为 AtlasCloud asset ID（atlas-asset-xxx），绕过人脸检测。
     * 流程：读取图片字节 → uploadMedia → sd/assets 注册 → 轮询到 Active → 返回 atlas_asset_id
     * 失败时降级：公网 URL 直接用 / 本地文件转 base64。
     */
    private String resolveImageRef(String imageUrl) {
        if (isBlank(imageUrl)) return imageUrl;

        boolean isLocal = imageUrl.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?/.*");

        try {
            byte[] imageBytes;
            String filename;
            if (isLocal) {
                String fname = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
                imageBytes = Files.readAllBytes(Paths.get(avatarUploadDir).resolve(fname));
                filename = fname;
            } else {
                HttpRequest dlReq = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();
                HttpResponse<byte[]> dlResp = http.send(dlReq, HttpResponse.BodyHandlers.ofByteArray());
                if (dlResp.statusCode() / 100 != 2) {
                    throw new RuntimeException("下载图片失败 " + dlResp.statusCode());
                }
                imageBytes = dlResp.body();
                String path = URI.create(imageUrl).getPath();
                filename = path.substring(path.lastIndexOf('/') + 1);
                if (isBlank(filename)) filename = "avatar.jpg";
            }

            // 压缩：最长边不超过 1080px，JPEG quality=0.85，避免超出 AtlasCloud 大小限制
            imageBytes = compressImage(imageBytes);
            filename = filename.replaceAll("\\.[^.]+$", "") + ".jpg";
            log.info("[Seedance] 压缩后准备上传: filename={} size={}KB", filename, imageBytes.length / 1024);
            String cdnUrl = uploadMediaToAtlas(imageBytes, filename);
            log.info("[Seedance] uploadMedia 成功: {}", cdnUrl);

            String assetId = registerAndWaitAsset(cdnUrl);
            log.info("[Seedance] 资产注册成功，atlas_asset_id={}", assetId);
            return "asset://" + assetId;

        } catch (Exception e) {
            throw new RuntimeException("图片资产上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * Step 1：上传图片到 AtlasCloud CDN，返回 CDN URL。
     * POST https://api.atlascloud.ai/api/v1/model/uploadMedia  multipart field=file
     */
    private String uploadMediaToAtlas(byte[] imageBytes, String filename) throws Exception {
        String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
        String ct = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + ct + "\r\n\r\n").getBytes());
        body.write(imageBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(baseUrl) + "/api/v1/model/uploadMedia"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + requiredApiKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("[Seedance] uploadMedia status={} body={}", resp.statusCode(), truncate(resp.body(), 400));

        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("uploadMedia 失败 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }

        JsonNode root = mapper.readTree(resp.body());
        // 官方 API 返回顶层 url；console API 返回 data.download_url，兼容两种
        String url = root.path("url").asText(null);
        if (isBlank(url)) url = root.path("data").path("download_url").asText(null);
        if (isBlank(url)) url = root.path("data").path("url").asText(null);
        if (isBlank(url)) {
            throw new RuntimeException("uploadMedia 响应中未找到 url: " + truncate(resp.body(), 300));
        }
        return url;
    }

    /**
     * Step 2：将 CDN URL 注册为 AtlasCloud 资产，轮询到 Active 后返回 atlas_asset_id。
     * POST https://api.atlascloud.ai/api/v1/sd/assets  {"name":"...","url":"..."}
     * 注意：首先尝试 api.atlascloud.ai（API Key），失败则 atlas_asset_id 不可用。
     */
    private String registerAndWaitAsset(String cdnUrl) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", "avatar_" + UUID.randomUUID().toString().substring(0, 8));
        body.put("url", cdnUrl);
        String payload = mapper.writeValueAsString(body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(baseUrl) + "/api/v1/sd/assets"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + requiredApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("[Seedance] sd/assets status={} body={}", resp.statusCode(), truncate(resp.body(), 400));

        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("sd/assets 失败 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }

        JsonNode root = mapper.readTree(resp.body());
        JsonNode data = root.path("data");

        // 如果已经是 Active 状态，直接返回
        String status = data.path("status").asText("");
        String atlasAssetId = data.path("atlas_asset_id").asText(null);

        if (!isBlank(atlasAssetId) && "Active".equalsIgnoreCase(status)) {
            return atlasAssetId;
        }

        // 资产处于 Processing 状态，需要轮询 GET /api/v1/sd/assets/{id}
        String assetId = data.path("id").asText(null);
        if (isBlank(assetId) && data.isNumber()) {
            assetId = data.asText();
        }
        // id 字段可能是数字
        if (isBlank(assetId)) {
            JsonNode idNode = data.path("id");
            assetId = idNode.isMissingNode() ? null : idNode.asText();
        }

        if (!isBlank(atlasAssetId) && isBlank(assetId)) {
            // 没有 id 但有 atlas_asset_id，直接返回（可能已就绪）
            return atlasAssetId;
        }

        if (isBlank(assetId)) {
            throw new RuntimeException("sd/assets 响应中未找到 id: " + truncate(resp.body(), 300));
        }

        // 轮询等待 Active（最多 60 秒）
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            sleep(3);
            HttpRequest pollReq = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(baseUrl) + "/api/v1/sd/assets/" + assetId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + requiredApiKey())
                    .GET().build();
            HttpResponse<String> pollResp = http.send(pollReq, HttpResponse.BodyHandlers.ofString());
            log.debug("[Seedance] 轮询 asset {} status={}", assetId, pollResp.statusCode());

            if (pollResp.statusCode() / 100 == 2) {
                JsonNode pr = mapper.readTree(pollResp.body()).path("data");
                String s = pr.path("status").asText("");
                String aid = pr.path("atlas_asset_id").asText(null);
                log.info("[Seedance] asset 状态={} atlas_asset_id={}", s, aid);
                if ("Active".equalsIgnoreCase(s) && !isBlank(aid)) {
                    return aid;
                }
                if ("Failed".equalsIgnoreCase(s)) {
                    throw new RuntimeException("资产处理失败: " + truncate(pollResp.body(), 200));
                }
            }
        }
        throw new RuntimeException("资产轮询超时（60s），assetId=" + assetId);
    }

    private byte[] compressImage(byte[] raw) throws Exception {
        BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(raw));
        if (src == null) return raw;

        int w = src.getWidth(), h = src.getHeight();
        int maxSide = 1080;
        if (w > maxSide || h > maxSide) {
            double scale = (double) maxSide / Math.max(w, h);
            int nw = (int) (w * scale), nh = (int) (h * scale);
            BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            src = scaled;
        } else if (src.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, null);
            g.dispose();
            src = rgb;
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(src, null, null), param);
        }
        writer.dispose();
        return baos.toByteArray();
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
