package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.agent.dto.AgentRequest;
import com.insurance.agent.dto.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LibTvService {
    private static final Logger log = LoggerFactory.getLogger(LibTvService.class);
    private static final String PROJECT_CANVAS_BASE = "https://www.liblib.tv/canvas?projectId=";
    private static final Pattern RESULT_URL_PATTERN = Pattern.compile(
            "https://libtv-res\\.liblib\\.art/[^\\s\"'<>]+?\\.(?:png|jpg|jpeg|webp|mp4|mov|webm)(?:\\?[^\\s\"'<>]+)?",
            Pattern.CASE_INSENSITIVE);

    // 占位符便于未来替换为用户上传的角色描述
    private static final String KOUBO_CHARACTER_PLACEHOLDER =
            "（角色：系统默认 AI 主播；如需绑定自定义角色，请上传角色图片后由系统自动替换此处）";

    private final DeepSeekService deepSeek;

    public LibTvService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    @Value("${libtv.api.key:}")
    private String apiKey;

    @Value("${libtv.api.base-url:https://im.liblib.tv}")
    private String baseUrl;

    @Value("${libtv.api.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${libtv.api.poll-interval-seconds:25}")
    private int pollIntervalSeconds;

    @Value("${libtv.api.poll-timeout-seconds:3600}")
    private int pollTimeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public AgentResponse generateVideo(AgentRequest req) {
        String message;
        if ("koubo".equalsIgnoreCase(req == null ? null : req.getTargetMode())) {
            String rawScript = req != null && !isBlank(req.getScript()) ? req.getScript().trim()
                    : (req != null && !isBlank(req.getTopic()) ? req.getTopic().trim() : "");
            if (isBlank(rawScript)) throw new IllegalArgumentException("口播脚本不能为空");
            log.info("[LibTV-Koubo] 开始 DeepSeek 口播脚本预处理，原始长度={}", rawScript.length());
            message = optimizeForKoubo(rawScript, req.getStyle());
            // log.info("[LibTV-Koubo] 预处理完成，优化后长度={}", m);
            //message = buildKouboMessage(optimized, req);
        } else {
            message = buildMessage(req);
        }
        if (isBlank(message)) {
            throw new IllegalArgumentException("视频脚本不能为空");
        }

        JsonNode session = createSession(null, message);
        String sessionId = session.path("sessionId").asText("");
        String projectUuid = session.path("projectUuid").asText("");
        log.info(sessionId);
        if (isBlank(sessionId)) {
            throw new IllegalStateException("LibTV 未返回 sessionId");
        }

        long afterSeq = resolveSubmissionSeq(sessionId, message);
        AgentResponse resp = new AgentResponse("LibTV 视频任务已提交，系统会每 25 秒同步一次新生成的视频，最长自动同步约 2 小时。你也可以打开项目画布查看 LibTV 后台是否还在生成。", "libtv-agent-im");
        resp.setSessionId(sessionId);
        resp.setProjectUuid(projectUuid);
        resp.setProjectUrl(buildProjectUrl(projectUuid));
        resp.setResultUrls(List.of());
        resp.setNextSeq(afterSeq);
        resp.setStatusText("任务已提交，等待 LibTV 开始生成。");
        resp.setFinalResultReady(false);
        resp.setCurrentStep(0);
        resp.setTotalSteps(5);
        resp.setProgressPercent(5);
        resp.setStepName("等待开始");
        resp.setSteps(List.of("等待开始", "排队中", "AI 理解", "模型生成", "生成完成"));
        return resp;
    }

    public AgentResponse changeProject() {
        JsonNode data = post("/openapi/session/change-project", mapper.createObjectNode());
        String projectUuid = data.path("projectUuid").asText("");
        if (isBlank(projectUuid)) {
            throw new IllegalStateException("LibTV 未返回 projectUuid");
        }
        AgentResponse resp = new AgentResponse("已新建 LibTV 项目，后续视频会在新的项目会话中生成。", "libtv-agent-im");
        resp.setProjectUuid(projectUuid);
        resp.setProjectUrl(buildProjectUrl(projectUuid));
        return resp;
    }

    public AgentResponse mergeVideos(AgentRequest req) {
        List<String> videoUrls = sanitizeVideoUrls(req == null ? null : req.getVideoUrls());
        if (videoUrls.size() < 2) {
            throw new IllegalArgumentException("至少需要选择 2 个视频才能拼接");
        }

        JsonNode newProject = post("/openapi/session/change-project", mapper.createObjectNode());
        String changedProjectUuid = newProject.path("projectUuid").asText("");
        String message = buildMergeMessage(videoUrls, req == null ? "" : req.getScript());
        JsonNode session = createSession(null, message);
        String sessionId = session.path("sessionId").asText("");
        String projectUuid = session.path("projectUuid").asText("");
        if (isBlank(projectUuid)) projectUuid = changedProjectUuid;
        if (isBlank(sessionId)) {
            throw new IllegalStateException("LibTV 未返回 sessionId");
        }

        long afterSeq = resolveSubmissionSeq(sessionId, message);
        List<String> urls = keepPrimaryVideo(pollResultUrls(sessionId, afterSeq, new LinkedHashSet<>(videoUrls)));
        AgentResponse resp = new AgentResponse(buildContent(sessionId, projectUuid, urls), "libtv-agent-im");
        resp.setSessionId(sessionId);
        resp.setProjectUuid(projectUuid);
        resp.setProjectUrl(buildProjectUrl(projectUuid));
        resp.setResultUrls(urls);
        // 拼接结果也按"最长"挑, 而不是第一个
        resp.setVideoUrl(pickLongestVideoUrl(urls));
        return resp;
    }

    public AgentResponse listSessionVideos(AgentRequest req) {
        String sessionId = req == null ? "" : req.getSessionId();
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        long afterSeq = req.getAfterSeq() == null ? 0 : Math.max(0, req.getAfterSeq());
        boolean finalOnly = Boolean.TRUE.equals(req.getFinalOnly());
        JsonNode data = querySession(sessionId.trim(), finalOnly ? 0 : afterSeq);
        JsonNode messages = data.path("messages");
        List<String> allUrls = keepVideoUrls(extractAllResultUrls(messages));
        boolean finalReady = isFinalResultReady(messages, allUrls);
        List<String> urls = finalOnly
                ? finalResultUrls(allUrls, finalReady)
                : allUrls;
        AgentResponse resp = new AgentResponse("已加载该 LibTV session 中的视频: " + urls.size() + " 个", "libtv-agent-im");
        resp.setSessionId(sessionId.trim());
        resp.setResultUrls(urls);
        // videoUrl 字段总是给"最长(=最终成片候选)"的那一个, 让前端可以直接拿
        resp.setVideoUrl(pickLongestVideoUrl(allUrls));
        resp.setNextSeq(Math.max(afterSeq, findMaxSeq(messages)));
        resp.setStatusText(summarizeSessionStatus(messages, allUrls, finalOnly, finalReady));
        resp.setFinalResultReady(finalReady);

        String[] statusDetails = extractDetailedStatus(messages, allUrls, finalReady);
        resp.setStepName(statusDetails[0]);
        resp.setCurrentStep(Integer.parseInt(statusDetails[1]));
        resp.setTotalSteps(Integer.parseInt(statusDetails[2]));
        resp.setProgressPercent(Integer.parseInt(statusDetails[3]));
        resp.setSteps(List.of("等待开始", "排队中", "AI 理解", "模型生成", "生成完成"));

        String projectUuid = data.path("projectUuid").asText("");
        if (!isBlank(projectUuid)) {
            resp.setProjectUuid(projectUuid);
            resp.setProjectUrl(buildProjectUrl(projectUuid));
        }
        return resp;
    }

    private JsonNode createSession(String sessionId, String message) {
        ObjectNode body = mapper.createObjectNode();
        if (!isBlank(sessionId)) body.put("sessionId", sessionId);
        if (!isBlank(message)) body.put("message", message);
        return post("/openapi/session", body);
    }

    private long resolveSubmissionSeq(String sessionId, String message) {
        try {
            JsonNode data = get("/openapi/session/" + urlEncode(sessionId));
            JsonNode messages = data.path("messages");
            long userSeq = findLatestUserSeq(messages, message);
            if (userSeq > 0) return userSeq;
            return findMaxSeq(messages);
        } catch (RuntimeException e) {
            log.warn("LibTV baseline query failed, polling from fresh session only may be less precise: {}", e.getMessage());
            return 0;
        }
    }

    private List<String> pollResultUrls(String sessionId, long afterSeq) {
        return pollResultUrls(sessionId, afterSeq, Set.of());
    }

    private List<String> pollResultUrls(String sessionId, long afterSeq, Set<String> excludedUrls) {
        return pollResultUrls(sessionId, afterSeq, excludedUrls, false);
    }

    private List<String> pollResultUrls(String sessionId, long afterSeq, Set<String> excludedUrls, boolean collectUntilTimeout) {
        long deadline = System.nanoTime() + Duration.ofSeconds(pollTimeoutSeconds).toNanos();
        RuntimeException lastError = null;
        long cursor = afterSeq;
        Set<String> collected = new LinkedHashSet<>();

        while (System.nanoTime() < deadline) {
            try {
                JsonNode data = querySession(sessionId, cursor);
                JsonNode messages = data.path("messages");
                List<String> urls = extractResultUrls(messages, excludedUrls);
                if (!urls.isEmpty()) {
                    if (!collectUntilTimeout) return urls;
                    collected.addAll(urls);
                }
                cursor = Math.max(cursor, findMaxSeq(messages));
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("LibTV query failed, will retry: {}", e.getMessage());
            }
            sleepPollInterval();
        }

        if (lastError != null) {
            log.warn("LibTV polling timed out after query errors: {}", lastError.getMessage());
        }
        return new ArrayList<>(collected);
    }

    private JsonNode querySession(String sessionId, long afterSeq) {
        String path = "/openapi/session/" + urlEncode(sessionId);
        if (afterSeq > 0) {
            path += "?afterSeq=" + afterSeq;
        }
        return get(path);
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            String payload = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + requiredApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return readData(http.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 LibTV 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + requiredApiKey())
                    .GET()
                    .build();
            return readData(http.send(req, HttpResponse.BodyHandlers.ofString()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("查询 LibTV 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode readData(HttpResponse<String> resp) throws Exception {
        if (resp.statusCode() / 100 != 2) {
            log.warn("LibTV non-2xx: {} body={}", resp.statusCode(), resp.body());
            throw new RuntimeException("LibTV 返回 " + resp.statusCode() + ": " + truncate(resp.body(), 500));
        }
        JsonNode root = mapper.readTree(resp.body());
        JsonNode data = root.path("data");
        return data.isMissingNode() || data.isNull() ? root : data;
    }

    private List<String> extractResultUrls(JsonNode messages) {
        return extractResultUrls(messages, Set.of());
    }

    private List<String> extractResultUrls(JsonNode messages, Set<String> excludedUrls) {
        if (messages == null || !messages.isArray()) return List.of();
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            Set<String> urls = new LinkedHashSet<>();
            String role = msg.path("role").asText("");
            JsonNode content = msg.path("content");
            if ("tool".equals(role)) {
                collectToolUrls(content, urls);
            }
            if ("assistant".equals(role) && content.isTextual()) {
                collectTextUrls(content.asText(""), urls);
            }
            urls.removeIf(url -> isExcludedResultUrl(url, excludedUrls));
            if (!urls.isEmpty()) return new ArrayList<>(urls);
        }
        return List.of();
    }

    private List<String> extractAllResultUrls(JsonNode messages) {
        Set<String> urls = new LinkedHashSet<>();
        if (messages == null || !messages.isArray()) return List.of();
        for (JsonNode msg : messages) {
            String role = msg.path("role").asText("");
            JsonNode content = msg.path("content");
            if ("tool".equals(role)) {
                collectToolUrls(content, urls);
            }
            if ("assistant".equals(role) && content.isTextual()) {
                collectTextUrls(content.asText(""), urls);
            }
        }
        return new ArrayList<>(urls);
    }

    private String summarizeSessionStatus(JsonNode messages, List<String> urls) {
        return summarizeSessionStatus(messages, urls, false, false);
    }

    private String summarizeSessionStatus(JsonNode messages, List<String> urls, boolean finalOnly, boolean finalReady) {
        if (urls != null && !urls.isEmpty()) {
            if (finalOnly && !finalReady) {
                return "已检测到阶段视频 " + urls.size() + " 个，继续等待 LibTV 输出最终合成成片。";
            }
            if (finalOnly) {
                return "最终合成视频已生成。";
            }
            return "检测到新视频结果 " + urls.size() + " 个。";
        }
        if (messages == null || !messages.isArray() || messages.isEmpty()) {
            return "暂无新消息，LibTV 任务可能仍在排队或处理中。";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            String role = msg.path("role").asText("");
            if (!"assistant".equals(role) && !"tool".equals(role)) continue;
            JsonNode content = msg.path("content");
            if ("assistant".equals(role) && content.isTextual() && !isBlank(content.asText())) {
                return "LibTV: " + truncate(stripResultUrls(content.asText()), 180);
            }
            if ("tool".equals(role)) {
                String toolStatus = summarizeToolContent(content);
                if (!isBlank(toolStatus)) return toolStatus;
            }
        }
        return "LibTV 已有新消息，但暂未返回视频结果。";
    }

    private String[] extractDetailedStatus(JsonNode messages, List<String> urls) {
        return extractDetailedStatus(messages, urls, false);
    }

    /**
     * 给前端进度条用. 核心思路: 进度永远跟"已生成视频数"和"消息数"挂钩, 不会卡死.
     *  - finalReady=true → 100%
     *  - 已有 N 个视频 → 60% + N*5% (60-90%)
     *  - 没视频但有消息 → 15% + msgCount*5% (15-55%)
     *  - 啥都没有 → 5%
     */
    private String[] extractDetailedStatus(JsonNode messages, List<String> urls, boolean finalReady) {
        int totalSteps = 5;

        if (finalReady) {
            return new String[]{"生成完成", "5", "5", "100"};
        }

        int videoCount = urls == null ? 0 : urls.size();
        int msgCount = messages == null || !messages.isArray() ? 0 : messages.size();

        // 找最近一条 tool 状态文本 / assistant 文本, 当 stepName 用
        String latestStatus = "";
        String latestAssistantText = "";
        if (msgCount > 0) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonNode msg = messages.get(i);
                String role = msg.path("role").asText("");
                JsonNode content = msg.path("content");
                if ("tool".equals(role) && latestStatus.isEmpty()) {
                    String s = extractToolStatus(content);
                    if (!isBlank(s)) latestStatus = s;
                }
                if ("assistant".equals(role) && content.isTextual() && latestAssistantText.isEmpty()) {
                    latestAssistantText = content.asText("").trim();
                }
                if (!latestStatus.isEmpty() && !latestAssistantText.isEmpty()) break;
            }
        }

        // 阶段 1: 已经在合成成片 (有视频, 还没确认完成)
        if (videoCount > 0) {
            int percent = Math.min(90, 60 + videoCount * 5);
            String name = latestStatus.isEmpty()
                    ? "已生成 " + videoCount + " 个片段, 等待合成"
                    : truncate(latestStatus, 60);
            return new String[]{name, "4", "5", String.valueOf(percent)};
        }

        // 阶段 0: 还没有视频
        if (msgCount == 0) {
            return new String[]{"等待开始", "0", "5", "5"};
        }

        // 根据最近状态文本/assistant 文本判定步骤
        String all = (latestStatus + " " + latestAssistantText).toLowerCase();
        String fullChinese = latestStatus + " " + latestAssistantText;
        int step;
        int percent;
        String name;
        if (fullChinese.contains("排队")) {
            step = 1; percent = 25; name = "排队中";
        } else if (fullChinese.contains("理解") || fullChinese.contains("规划") || fullChinese.contains("分析")
                   || all.contains("planning") || all.contains("understand")) {
            step = 2; percent = 40; name = "AI 理解任务中";
        } else if (fullChinese.contains("渲染") || fullChinese.contains("合成")
                   || all.contains("render") || all.contains("merging")) {
            step = 3; percent = 70; name = "渲染合成中";
        } else if (fullChinese.contains("生成") || fullChinese.contains("处理")
                   || all.contains("generating") || all.contains("processing")) {
            step = 3; percent = 55; name = "模型生成中";
        } else {
            // fallback: 进度按消息数推进, 永远不会卡在同一个值
            percent = Math.min(55, 15 + msgCount * 5);
            step = Math.max(1, Math.min(3, msgCount / 2));
            name = isBlank(latestStatus)
                    ? (isBlank(latestAssistantText) ? "处理中" : truncate(latestAssistantText, 60))
                    : truncate(latestStatus, 60);
        }
        return new String[]{name, String.valueOf(step), String.valueOf(totalSteps), String.valueOf(percent)};
    }

    private String extractToolStatus(JsonNode content) {
        JsonNode data = content;
        if (content != null && content.isTextual()) {
            try {
                data = mapper.readTree(content.asText(""));
            } catch (Exception ignored) {
                return "";
            }
        }
        if (data == null || data.isMissingNode() || data.isNull()) return "";

        for (String key : List.of("status", "state", "message", "msg", "progress", "task_status", "stage", "phase")) {
            JsonNode node = data.findValue(key);
            if (node != null && node.isValueNode() && !isBlank(node.asText())) {
                return node.asText();
            }
        }

        JsonNode dataNode = data.path("data");
        if (dataNode != null && !dataNode.isMissingNode()) {
            for (String key : List.of("status", "state", "message", "msg", "stage", "phase")) {
                JsonNode node = dataNode.findValue(key);
                if (node != null && node.isValueNode() && !isBlank(node.asText())) {
                    return node.asText();
                }
            }
        }

        return "";
    }

    private boolean isFinalResultReady(JsonNode messages, List<String> urls) {
        if (urls == null || urls.isEmpty() || messages == null || !messages.isArray()) return false;

        // Path 1: 显式完成关键词 (LibTV 偶尔会输出"已完成"/"done")
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode msg = messages.get(i);
            String role = msg.path("role").asText("");
            JsonNode content = msg.path("content");
            String text = "";
            if (content != null && content.isTextual()) {
                text = content.asText("");
            } else if (content != null && !content.isMissingNode() && !content.isNull()) {
                text = content.toString();
            }
            String status = "tool".equals(role) ? extractToolStatus(content) : "";
            if (looksLikeFinalStatus(text) || looksLikeFinalStatus(status)) {
                return true;
            }
        }

        // Path 2: 反信号检测. 已经有视频 URL, 且最后 3 条消息中没有任何"还在处理"的语义,
        // 我们就认为 LibTV 已经停下了 → 当前最后一个视频就是最终成片.
        int checked = 0;
        for (int i = messages.size() - 1; i >= 0 && checked < 3; i--, checked++) {
            JsonNode msg = messages.get(i);
            String role = msg.path("role").asText("");
            JsonNode content = msg.path("content");
            String text = "";
            if (content != null && content.isTextual()) {
                text = content.asText("");
            } else if (content != null && !content.isMissingNode() && !content.isNull()) {
                text = content.toString();
            }
            String status = "tool".equals(role) ? extractToolStatus(content) : "";
            if (containsInProgressSignal(text) || containsInProgressSignal(status)) {
                return false; // 显式还在处理中
            }
        }
        // 没看到任何进行中信号 + 已经有视频 → 视为已完成
        return true;
    }

    /** 检测"还在处理中"类的反信号. */
    private static boolean containsInProgressSignal(String value) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase();
        return value.contains("处理中")
                || value.contains("生成中")
                || value.contains("排队中")
                || value.contains("进行中")
                || value.contains("正在生成")
                || value.contains("正在处理")
                || value.contains("正在渲染")
                || value.contains("正在合成")
                || value.contains("等待")
                || lower.contains("processing")
                || lower.contains("queued")
                || lower.contains("queueing")
                || lower.contains("running")
                || lower.contains("pending")
                || lower.contains("in progress");
    }

    private boolean looksLikeFinalStatus(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase();
        if (value.contains("未完成")
                || value.contains("暂未完成")
                || value.contains("尚未完成")
                || value.contains("等待完成")
                || lower.contains("not completed")
                || lower.contains("not finished")) {
            return false;
        }
        return value.contains("最终视频已生成")
                || value.contains("最终成片已生成")
                || value.contains("最终合成")
                || value.contains("合成完成")
                || value.contains("生成完成")
                || value.contains("已完成")
                || value.contains("完成")
                || lower.contains("final")
                || lower.contains("finished")
                || lower.contains("completed")
                || lower.contains("succeeded")
                || lower.contains("success")
                || lower.contains("done");
    }

    /**
     * finalOnly=true 模式: 挑文件最大 (= 时长最长 = 合成成片) 的那个 URL 返回.
     * 不再依赖 finalReady, 也不再用"最后一个" — 最后一个有可能只是又一段中间 5s 片段,
     * 真正的合成成片是 30s 的, 文件大小数倍于片段, 用 HEAD Content-Length 排序更准.
     */
    private List<String> finalResultUrls(List<String> urls, boolean finalReady) {
        String video = pickLongestVideoUrl(urls);
        return isBlank(video) ? List.of() : List.of(video);
    }

    private String summarizeToolContent(JsonNode content) {
        JsonNode data = content;
        if (content != null && content.isTextual()) {
            try {
                data = mapper.readTree(content.asText(""));
            } catch (Exception ignored) {
                return "LibTV 工具处理中。";
            }
        }
        if (data == null || data.isMissingNode() || data.isNull()) return "";
        for (String key : List.of("status", "state", "message", "msg", "progress", "task_status")) {
            JsonNode node = data.findValue(key);
            if (node != null && node.isValueNode() && !isBlank(node.asText())) {
                return "LibTV: " + truncate(node.asText(), 180);
            }
        }
        return "LibTV 工具处理中，暂未返回视频结果。";
    }

    private boolean isExcludedResultUrl(String url, Set<String> excludedUrls) {
        if (isBlank(url) || excludedUrls == null || excludedUrls.isEmpty()) return false;
        String normalized = normalizeUrl(url);
        for (String excluded : excludedUrls) {
            if (normalized.equals(normalizeUrl(excluded))) return true;
        }
        return false;
    }

    private long findLatestUserSeq(JsonNode messages, String submittedMessage) {
        if (messages == null || !messages.isArray()) return 0;
        long found = 0;
        String submitted = normalizeMessage(submittedMessage);
        for (JsonNode msg : messages) {
            if (!"user".equals(msg.path("role").asText(""))) continue;
            String content = msg.path("content").asText("");
            if (!submitted.equals(normalizeMessage(content))) continue;
            long seq = readSeq(msg);
            if (seq > found) found = seq;
        }
        return found;
    }

    private long findMaxSeq(JsonNode messages) {
        if (messages == null || !messages.isArray()) return 0;
        long max = 0;
        for (JsonNode msg : messages) {
            max = Math.max(max, readSeq(msg));
        }
        return max;
    }

    private long readSeq(JsonNode msg) {
        if (msg == null || msg.isMissingNode() || msg.isNull()) return 0;
        for (String key : List.of("seq", "sequence", "messageSeq")) {
            JsonNode node = msg.path(key);
            if (node.canConvertToLong()) return node.asLong();
            if (node.isTextual()) {
                try {
                    return Long.parseLong(node.asText("").trim());
                } catch (NumberFormatException ignored) {
                    // Try the next known sequence field.
                }
            }
        }
        return 0;
    }

    private void collectToolUrls(JsonNode content, Set<String> urls) {
        JsonNode data = content;
        if (content.isTextual()) {
            try {
                data = mapper.readTree(content.asText(""));
            } catch (Exception ignored) {
                collectTextUrls(content.asText(""), urls);
                return;
            }
        }
        JsonNode taskResult = data.path("task_result");
        collectPreviewPaths(taskResult.path("images"), urls);
        collectPreviewPaths(taskResult.path("videos"), urls);
    }

    private void collectPreviewPaths(JsonNode items, Set<String> urls) {
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            String preview = item.path("previewPath").asText("");
            String url = isBlank(preview) ? item.path("url").asText("") : preview;
            if (looksLikeResultUrl(url)) urls.add(url);
        }
    }

    private void collectTextUrls(String text, Set<String> urls) {
        Matcher m = RESULT_URL_PATTERN.matcher(text == null ? "" : text);
        while (m.find()) {
            urls.add(m.group());
        }
    }

    private String optimizeForKoubo(String rawScript, String style) {
        if (isBlank(rawScript)) return rawScript;
        String systemPrompt =
                "# 角色\n" +
                        "\n" +
                        "你是一名专业的 AI 视频脚本结构化助手,擅长把『分镜画面描述 + 口播文案』改写成 AI 视频生成模型可直接使用的连续长句提示词,并精准估算视频总时长。\n" +
                        "\n" +
                        "# 任务\n" +
                        "\n" +
                        "用户会提供:\n" +
                        "1. 一组分镜:每个分镜包含「画面描述」「口播文案」(可能还包含「字幕」「时长」「备注」等其他字段)\n" +
                        "2. 全局参数:角色名(如『职业感AI主播』)、视频风格(如『现代写实商务短视频风』)、画幅(如『竖版9:16』)\n" +
                        "\n" +
                        "你需要把这些分镜按出场顺序融合,改写成**一段连贯的、用逗号连接的长句**,作为 AI 视频生成模型的输入提示词,并在末尾给出视频总时长。\n" +
                        "\n" +
                        "# 时长计算规则\n" +
                        "\n" +
                        "## 基础公式\n" +
                        "\n" +
                        "```\n" +
                        "单镜头时长(秒) = 口播秒数 + 开场缓冲(0.5秒) + 结尾余韵(0.5秒) + 运镜复杂度修正\n" +
                        "```\n" +
                        "\n" +
                        "其中:\n" +
                        "- **口播秒数 = 中文口播字数 ÷ 4.5**(AI 主播标准语速,每秒约 4-5 字)\n" +
                        "- 字数计算时,标点符号不计入,但停顿明显的逗号/句号每个加 0.2 秒\n" +
                        "\n" +
                        "## 运镜复杂度修正表\n" +
                        "\n" +
                        "| 动作类型 | 加时 | 触发关键词 |\n" +
                        "|---------|------|-----------|\n" +
                        "| 静态镜头 | +0 秒 | 站在/坐在/拿着,无运镜 |\n" +
                        "| 单次运镜 | +0.5 秒 | 拉远/推近/慢摇/跟拍/虚化 |\n" +
                        "| 复杂动效 | +1~1.5 秒 | 分屏/对比构图/前后对比/动画特效/数字闪烁 |\n" +
                        "| 道具展示 | +0.5~1 秒 | 手写卡片/手机屏幕展示/产品特写 |\n" +
                        "| CTA 停留 | +1 秒 | 关注/点赞/二维码/按钮 |\n" +
                        "\n" +
                        "## 时长来源优先级\n" +
                        "\n" +
                        "1. 用户明确给出镜头时长 → **使用用户给的值**(不要覆盖)\n" +
                        "2. 用户未给时长 → **自动按公式计算**\n" +
                        "3. 单镜头时长四舍五入到整数秒,**不得低于 3 秒,不超过 12 秒**\n" +
                        "\n" +
                        "## 总时长\n" +
                        "\n" +
                        "把所有镜头时长相加,得到最终总时长(整数秒)。**只在末尾输出总时长一个值,不要列出每个镜头的时长**。\n" +
                        "\n" +
                        "# 输出公式\n" +
                        "\n" +
                        "每个分镜在长句中按以下要素顺序组织:\n" +
                        "\n" +
                        "```\n" +
                        "[角色锚定] + [出镜状态(正面出镜/侧面/背影)] + [景别(近景/中景/远景/特写)] \n" +
                        "+ [场景与位置] + [动作与道具] + [运镜方式(拉远/推近/手持晃动/慢摇)] \n" +
                        "+ [互动方式(直视镜头/与观众互动)] + [情绪与表情] \n" +
                        "+ [引导短语(自然说出/友好说出/认真说出):口播原文]\n" +
                        "```\n" +
                        "\n" +
                        "分镜之间用『**硬切至{风格}**』『**转场到{新场景}**』『**镜头切换到**』等转场词连接。\n" +
                        "\n" +
                        "# 引导短语对应情绪\n" +
                        "\n" +
                        "- 惊讶/反问 → 『质疑地说道』『带着惊讶问出』\n" +
                        "- 严肃/警示 → 『认真说出』『郑重提醒』\n" +
                        "- 友好/总结 → 『友好地说出』『微笑着说』\n" +
                        "- 感叹/无奈 → 『无奈地说』『摇头叹气说』\n" +
                        "- 引导/CTA → 『真诚地邀请』『主动召唤』\n" +
                        "\n" +
                        "# 转场词选择\n" +
                        "\n" +
                        "- 同场景内动作切换 → 『镜头切换到』『画面拉到』\n" +
                        "- 不同场景跳切 → 『硬切至{风格}』『转场到{新场景}』\n" +
                        "- 时空对比/分屏 → 『画面分屏呈现』『左右对比展现』\n" +
                        "- 情绪反转 → 『前后对比切换』\n" +
                        "\n" +
                        "# 改写规则\n" +
                        "\n" +
                        "## 必须保留\n" +
                        "- **口播原文一字不改**,放在引号内\n" +
                        "- **关键数字/产品名/金额**,原样保留\n" +
                        "- **场景核心信息**(车旁/车内/停车场等)\n" +
                        "\n" +
                        "## 必须忽略\n" +
                        "- **不要保留「字幕」字段**(屏幕显示文字),即使用户给了字幕字段也只用作语义参考,不写入长句\n" +
                        "- **不要保留「备注」字段**(BGM/特效说明等导演备注),除非备注里包含画面要素\n" +
                        "\n" +
                        "## 改写策略\n" +
                        "- 用户给的画面描述里的中文标点(顿号/分号)统一改成逗号\n" +
                        "- 拆分长描述:把『主角站在车旁,一手拿手机,另一手比划6手势』拆成『角色名+正面出镜+近景+在车旁+一手拿手机+另一手比划6手势』\n" +
                        "- 表情和情绪要保留,如『表情惊讶/无奈/微笑』要明确写出\n" +
                        "- 道具和画面动效要保留,如『手机屏幕显示数字闪烁』『手中道具卡片』\n" +
                        "\n" +
                        "## 不要做的事\n" +
                        "- ❌ 不要在分镜之间用换行/序号/项目符号(除了末尾的总时长行)\n" +
                        "- ❌ 不要改写口播文案的内容\n" +
                        "- ❌ 不要解释你做了什么改写\n" +
                        "- ❌ 不要把字幕、BGM、备注等导演侧信息写进长句\n" +
                        "- ❌ 不要在长句内部标注时长\n" +
                        "- ❌ 不要列出每个镜头的单独时长\n" +
                        "- ❌ 不要省略画面描述里的画面细节(分屏/特效/道具都要保留)\n" +
                        "\n" +
                        "# 输出格式\n" +
                        "\n" +
                        "整段输出由以下三部分组成:\n" +
                        "\n" +
                        "**第一部分:开篇角色定义**\n" +
                        "```\n" +
                        "为{角色名}\n" +
                        "```\n" +
                        "\n" +
                        "**第二部分:连贯长句**(所有分镜融合,无换行)\n" +
                        "```\n" +
                        "{分镜1长句}。{转场词}{分镜2长句}。{转场词}{分镜3长句}。...{画幅}。\n" +
                        "```\n" +
                        "\n" +
                        "**第三部分:固定内容+总时长**(用换行与正文隔开)\n" +
                        "```\n" +
                        "\n" +
                        "---\n" +
                        "固定内容是：\"无需询问确认，禁止等待用户回复，直接开始执行全部步骤，自动完成。\"直接制作视频并自动拼接\""+
                        "总时长约 {总秒数} 秒\n" +

                        "```\n" +
                        "\n" +
                        "# 完整示例\n" +
                        "\n" +
                        "## 输入\n" +
                        "\n" +
                        "全局参数:\n" +
                        "- 角色名:专业感AI保险博主\n" +
                        "- 视频风格:现代写实生活类短视频风\n" +
                        "- 画幅:竖版9:16\n" +
                        "\n" +
                        "分镜清单:\n" +
                        "- 镜号01 / 时长5秒 / 中景 / 画面:主角站在车旁,一手拿手机,另一手比划\"6\"手势,表情惊讶,背景是停车场 / 口播:你以为车险只买交强险就够了吗?\n" +
                        "- 镜号02 / 时长6秒 / 近景 / 画面:主角特写,手指在手机屏幕上滑动,显示交强险保费金额(动画特效),背景虚化 / 口播:交强险最高只赔20万,撞到豪车或者人,分分钟倾家荡产。\n" +
                        "\n" +
                        "## 输出\n" +
                        "\n" +
                        "```\n" +
                        "为专业感AI保险博主\n" +
                        "专业感AI保险博主正面出镜,中景,站在停车场的车旁,一手拿着手机,另一手比划\"6\"的手势,表情带着惊讶,与观众直视互动,质疑地问出:'你以为车险只买交强险就够了吗?'。镜头切换到主角特写近景,手指在手机屏幕上滑动,屏幕出现交强险保费金额的动画特效,背景虚化突出主体,认真说出:'交强险最高只赔20万,撞到豪车或者人,分分钟倾家荡产'。竖版9:16。\n" +
                        "\n" +
                        "---\n" +
                        "无需询问确认，禁止等待用户回复，直接开始执行全部步骤，自动完成。\"直接制作视频并自动拼接"+
                        "总时长约 11 秒\n" +
                        "```";
        String userPrompt = "请把以下脚本优化为口播格式：\n\n" + rawScript.trim() +
                (isBlank(style) ? "" : "\n\n风格偏好：" + style.trim());
        try {
            return deepSeek.chat(systemPrompt, userPrompt, null);
        } catch (Exception e) {
            log.warn("[LibTV-Koubo] DeepSeek 口播预处理失败，降级使用原始脚本: {}", e.getMessage());
            return rawScript;
        }
    }

    private String buildKouboMessage(String optimizedScript, AgentRequest req) {
        StringBuilder out = new StringBuilder();
        out.append("【口播视频生成任务】\n\n");

        out.append("【主播角色设定】\n");
        if (!isBlank(req.getCharacterImageUrl())) {
            out.append("参考人物图片：").append(req.getCharacterImageUrl().trim()).append("\n");
            out.append("请以图片中人物的外貌、面部特征和形象为基础生成主播角色，全程保持人物一致性。\n");
        } else {
            out.append(KOUBO_CHARACTER_PLACEHOLDER).append("\n");
            out.append("形象：职业感 AI 主播\n");
        }
        out.append("口播风格：专业、亲切、语速适中\n\n");

        out.append("【生成要求】\n");
        out.append("模式：真人口播（talking head）\n");
        out.append("- 主播正面出镜，面部表情自然，与观众直视互动\n");
        if (!isBlank(req.getBackgroundImageUrl())) {
            out.append("- 参考背景图片：").append(req.getBackgroundImageUrl().trim()).append("\n");
            out.append("- 请以上方背景图片的风格和环境渲染视频背景，保持背景一致性\n");
        } else {
            out.append("- 背景：简洁商务风或轻量办公场景\n");
        }
        out.append("- 口型与台词完全同步，不要做成纯 B-roll 加字幕叠加\n\n");

        out.append("【台词与字幕要求】\n");
        out.append("- 严格按脚本顺序进行普通话配音\n");
        out.append("- 中文字幕同步烧录到画面下方，字体清晰可读\n");
        out.append("- 语速适中，每句话之间保留自然停顿，遇到【停顿】标记时延长停顿约 0.5 秒\n\n");

        out.append("【视频参数】\n");
        if (!isBlank(req.getDuration())) out.append("- 时长：").append(req.getDuration().trim()).append("\n");
        if (!isBlank(req.getStyle())) out.append("- 补充风格：").append(req.getStyle().trim()).append("\n");
        out.append("- 画面比例：竖版 9:16（适配手机短视频 / 朋友圈）\n");
        out.append("- 如内容较长，先内部分段生成，最终自动拼接为一个完整 MP4 成片，只输出最终合成视频\n\n");

        out.append("【口播脚本】\n");
        out.append(optimizedScript.trim());

        return out.toString();
    }

    private String buildMessage(AgentRequest req) {
        if (req == null) return "";
        String main = !isBlank(req.getScript()) ? req.getScript().trim()
                : isBlank(req.getTopic()) ? "" : req.getTopic().trim();
        if (isBlank(main)) return "";
        StringBuilder out = new StringBuilder();
        if (looksLikeStoryboard(main)) {
            out.append("请根据下面的分镜表直接生成视频。")
                    .append("\n要求：每个镜号按表格里的时长、景别、画面描述、口播文案、字幕和备注生成对应视频内容；" +
                            "如果总时长较长，可以先内部分段生成，但最后必须自动拼接成一个完整 MP4 成片，并只输出最终合成视频；不要只创建空项目，不要只复述分镜表。")
                    .append("\n声音与字幕要求：口播文案/台词必须生成普通话配音或人物口播音频，并同步烧录中文字幕；台词不能只作为画面参考，也不要生成无声视频。")
                    .append("\n\n[分镜表]\n");
        } else {
            out.append("请根据下面的脚本或生成要求直接生成视频。")
                    .append("\n要求：如果内容较长，可以先按语义和时长内部分段生成，但最后必须自动拼接成一个完整 MP4 成片，并只输出最终合成视频；不要只创建空项目，不要只复述脚本。")
                    .append("\n声音与字幕要求：脚本中的台词/口播必须生成普通话配音或人物口播音频，并同步烧录中文字幕；台词不能只作为画面参考，也不要生成无声视频。")
                    .append("\n\n[脚本/要求]\n");
        }
        out.append(main);
        if (!isBlank(req.getStyle())) out.append("\n风格: ").append(req.getStyle().trim());
        if (!isBlank(req.getDuration())) out.append("\n时长: ").append(req.getDuration().trim());
        return out.toString();
    }

    private boolean looksLikeStoryboard(String text) {
        if (text == null) return false;
        return text.contains("镜号")
                && text.contains("画面描述")
                && text.contains("口播文案")
                && text.contains("字幕");
    }

    private String buildMergeMessage(List<String> videoUrls, String instruction) {
        StringBuilder out = new StringBuilder("请按下面顺序把这些视频拼接成一个完整 MP4 视频。")
                .append("\n要求：保持原视频内容和顺序，不要重新改写剧情，不要替换素材，片段之间使用自然衔接。");
        if (!isBlank(instruction)) {
            out.append("\n补充要求：").append(instruction.trim());
        }
        for (int i = 0; i < videoUrls.size(); i++) {
            out.append("\n视频").append(i + 1).append("：").append(videoUrls.get(i));
        }
        return out.toString();
    }

    private List<String> sanitizeVideoUrls(List<String> urls) {
        if (urls == null) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (String url : urls) {
            if (isBlank(url)) continue;
            String trimmed = url.trim();
            String lower = trimmed.toLowerCase();
            if (lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".webm")) {
                out.add(trimmed);
            }
        }
        return new ArrayList<>(out);
    }

    private String normalizeMessage(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeUrl(String value) {
        if (value == null) return "";
        int queryIdx = value.indexOf('?');
        String clean = queryIdx >= 0 ? value.substring(0, queryIdx) : value;
        return clean.trim();
    }

    private String stripResultUrls(String text) {
        if (text == null) return "";
        return RESULT_URL_PATTERN.matcher(text).replaceAll("[视频结果链接]").trim();
    }

    private String buildContent(String sessionId, String projectUuid, List<String> urls) {
        StringBuilder content = new StringBuilder();
        if (urls == null || urls.isEmpty()) {
            content.append("LibTV 已创建生成任务，但在当前轮询时间内还没有返回视频/图片结果。");
        } else {
            content.append("LibTV 已生成结果。");
        }
        content.append("\n\nsessionId: ").append(sessionId);
        if (!isBlank(projectUuid)) {
            content.append("\n项目画布: ").append(buildProjectUrl(projectUuid));
        }
        return content.toString();
    }

    private String firstByExt(List<String> urls, List<String> exts) {
        if (urls == null) return null;
        for (String url : urls) {
            String lower = url == null ? "" : url.toLowerCase();
            for (String ext : exts) {
                if (lower.contains(ext)) return url;
            }
        }
        return null;
    }

    private String lastByExt(List<String> urls, List<String> exts) {
        if (urls == null) return null;
        for (int i = urls.size() - 1; i >= 0; i--) {
            String url = urls.get(i);
            String lower = url == null ? "" : url.toLowerCase();
            for (String ext : exts) {
                if (lower.contains(ext)) return url;
            }
        }
        return null;
    }

    /**
     * 在一组视频 URL 里挑"文件最大"的那一个 (最大文件 ≈ 最长时长 ≈ 合成成片).
     * 用 HEAD 请求拿 Content-Length 当代理. 失败时 fallback 到最后一个 mp4.
     *
     * 适用场景: LibTV 会先生成多个 5s 的小段, 最后合成一个 30s 的成片.
     * 30s 那个体积明显比 5s 大几倍, 拿 size 排序很准.
     */
    private String pickLongestVideoUrl(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        List<String> exts = List.of(".mp4", ".mov", ".webm");
        String fallback = lastByExt(urls, exts);
        String best = null;
        long bestSize = -1L;

        for (String url : urls) {
            if (isBlank(url)) continue;
            String lower = url.toLowerCase();
            boolean isVideo = false;
            for (String ext : exts) { if (lower.contains(ext)) { isVideo = true; break; } }
            if (!isVideo) continue;

            try {
                HttpRequest head = HttpRequest.newBuilder()
                        .uri(URI.create(url.trim()))
                        .version(HttpClient.Version.HTTP_1_1)
                        .timeout(Duration.ofSeconds(10))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();
                HttpResponse<Void> resp = http.send(head, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() / 100 != 2) {
                    log.debug("HEAD non-2xx {} for {}", resp.statusCode(), url);
                    continue;
                }
                long size = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
                log.debug("LibTV final-pick: size={} for {}", size, url);
                if (size > bestSize) {
                    bestSize = size;
                    best = url;
                }
            } catch (Exception e) {
                log.debug("HEAD failed for {}: {}", url, e.getMessage());
            }
        }
        return best != null ? best : fallback;
    }

    private List<String> keepPrimaryVideo(List<String> urls) {
        String video = firstByExt(urls, List.of(".mp4", ".mov", ".webm"));
        if (!isBlank(video)) return List.of(video);
        String image = firstByExt(urls, List.of(".png", ".jpg", ".jpeg", ".webp"));
        return isBlank(image) ? List.of() : List.of(image);
    }

    private List<String> keepVideoUrls(List<String> urls) {
        List<String> out = new ArrayList<>();
        if (urls == null) return out;
        for (String url : urls) {
            String lower = url == null ? "" : url.toLowerCase();
            if (lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".webm")) {
                out.add(url);
            }
        }
        return out;
    }

    private boolean looksLikeResultUrl(String url) {
        return url != null && RESULT_URL_PATTERN.matcher(url).find();
    }

    private String requiredApiKey() {
        if (isBlank(apiKey)) {
            throw new IllegalStateException("未配置 LIBTV_ACCESS_KEY 环境变量, 无法调用 LibTV。");
        }
        return apiKey.trim();
    }

    private String buildProjectUrl(String projectUuid) {
        return isBlank(projectUuid) ? "" : PROJECT_CANVAS_BASE + projectUuid.trim();
    }

    private void sleepPollInterval() {
        try {
            Thread.sleep(Math.max(1, pollIntervalSeconds) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LibTV 轮询被中断", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
