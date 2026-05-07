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
import java.util.Locale;

@Service
public class MediaToDocService {
    private static final Logger log = LoggerFactory.getLogger(MediaToDocService.class);

    private final DeepSeekService deepSeek;
    private final XhsExtractService xhsExtractService;
    private final DouyinExtractService douyinExtractService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${media2doc.api.provider:none}")
    private String provider;

    @Value("${media2doc.api.key:}")
    private String apiKey;

    @Value("${media2doc.api.base-url:}")
    private String baseUrl;

    @Value("${media2doc.api.transcribe-path:/transcribe}")
    private String transcribePath;

    @Value("${media2doc.api.timeout-seconds:300}")
    private int timeoutSeconds;

    @Value("${media2doc.api.model:doubao-seed-2-0-mini-260215}")
    private String arkModel;

    @Value("${media2doc.api.fps:1}")
    private double fps;

    public MediaToDocService(DeepSeekService deepSeek,
                             XhsExtractService xhsExtractService,
                             DouyinExtractService douyinExtractService) {
        this.deepSeek = deepSeek;
        this.xhsExtractService = xhsExtractService;
        this.douyinExtractService = douyinExtractService;
    }

    public enum OutputMode {
        XHS_NOTE("xhs-note", "视频转小红书笔记"),
        WECHAT_ARTICLE("wechat-article", "视频转公众号文章"),
        SUMMARY("summary", "视频内容总结"),
        SUBTITLE("subtitle", "影视字幕"),
        DOCUMENT("document", "视频转文档"),
        VIDEO_SCRIPT("video-script", "视频转脚本");

        private final String code;
        private final String label;

        OutputMode(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        public String label() {
            return label;
        }

        public static OutputMode from(String raw) {
            if (raw == null || raw.isBlank()) return DOCUMENT;
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace("_", "-");
            for (OutputMode mode : values()) {
                if (mode.code.equals(normalized) || mode.name().toLowerCase(Locale.ROOT).replace("_", "-").equals(normalized)) {
                    return mode;
                }
            }
            return DOCUMENT;
        }
    }

    public record WorkflowResult(String document, String script, String modelLabel) {}

    public String transcribeVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("视频链接不能为空");
        }
        
        // 智能解析：根据平台提取可供 Ark API 直接下载的视频 CDN 直链
        String effectiveVideoUrl = videoUrl;
        String lowerUrl = videoUrl.toLowerCase();
        if (lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.cn")) {
            // 小红书：用 XhsExtractService 提取 CDN 直链
            try {
                com.insurance.agent.dto.XhsNote note = xhsExtractService.extract(videoUrl);
                if (note != null && note.getVideoUrl() != null && !note.getVideoUrl().isBlank()) {
                    effectiveVideoUrl = note.getVideoUrl();
                }
            } catch (Exception e) {
                // 解析失败时继续尝试使用原始链接
            }
        } else if (lowerUrl.contains("douyin.com") || lowerUrl.contains("iesdouyin.com")) {
            // 抖音：优先提取 CDN 直链；失败则直传 workUrl 给 Ark（字节系 API 可能支持原生解析抖音作品页）
            // 短路：若传入的已是干净的 /video/{id} 作品页 URL，直接跳过二次提取
            boolean isCleanWorkUrl = lowerUrl.matches(".*douyin\\.com/video/\\d+/?.*");
            if (!isCleanWorkUrl) {
                try {
                    com.insurance.agent.dto.DouyinNote note = douyinExtractService.extract(videoUrl);
                    if (note != null && !isBlank(note.getVideoDownloadUrl())) {
                        effectiveVideoUrl = note.getVideoDownloadUrl();
                        log.info("[MediaToDoc] 抖音 CDN 直链提取成功");
                    } else {
                        String workUrl = note != null && !isBlank(note.getWorkUrl()) ? note.getWorkUrl() : videoUrl;
                        log.info("[MediaToDoc] 无法提取抖音 CDN 直链，降级传 workUrl 给 Ark: {}", workUrl);
                        effectiveVideoUrl = workUrl;
                    }
                } catch (Exception e) {
                    log.warn("[MediaToDoc] 抖音提取失败: {}，直传原始 URL 尝试", e.getMessage());
                    effectiveVideoUrl = videoUrl;
                }
            } else {
                log.info("[MediaToDoc] 抖音 workUrl 直传 Ark: {}", videoUrl);
            }
        }
        
        if ("ark-responses".equalsIgnoreCase(provider)) {
            return transcribeWithArkResponses(effectiveVideoUrl);
        }

        if (isBlank(baseUrl) || "none".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("""
                    视频链接解析/转写服务尚未配置。
                    请在 application.yml 或 application-local.yml 中配置 media2doc.api.provider=ark-responses，并配置 media2doc.api.key。
                    如果输入的是小红书/抖音等平台分享页链接，还可能需要先解析成可公网访问的视频直链。
                    """);
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("videoUrl", videoUrl.trim());
            body.put("url", videoUrl.trim());
            body.put("provider", provider);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(joinUrl(baseUrl, transcribePath)))
                    .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 30)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!isBlank(apiKey)) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }

            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("视频解析/转写服务返回 " + resp.statusCode() + ": " + truncate(resp.body(), 300));
            }

            String transcript = extractTranscript(resp.body());
            if (isBlank(transcript)) {
                throw new RuntimeException("视频解析/转写服务未返回 transcript/text/content 字段");
            }
            return transcript;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用视频解析/转写服务失败: " + e.getMessage(), e);
        }
    }

    private String transcribeWithArkResponses(String videoUrl) {
        if (isBlank(apiKey)) {
            throw new IllegalStateException("未配置 media2doc.api.key / MEDIA2DOC_API_KEY / ARK_API_KEY，无法调用火山方舟 Responses API");
        }
        String effectiveBaseUrl = isBlank(baseUrl) ? "https://ark.cn-beijing.volces.com/api/v3" : baseUrl;

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", isBlank(arkModel) ? "doubao-seed-2-0-mini-260215" : arkModel.trim());
            body.put("stream", false);
            body.put("max_output_tokens", 12000);

            ArrayNode input = body.putArray("input");
            ObjectNode message = input.addObject();
            message.put("role", "user");
            ArrayNode content = message.putArray("content");

            ObjectNode video = content.addObject();
            video.put("type", "input_video");
            video.put("video_url", videoUrl.trim());
            video.put("fps", fps <= 0 ? 1 : fps);

            ObjectNode text = content.addObject();
            text.put("type", "input_text");
            text.put("text", """
                    请理解这个视频，并整理出一份可复用的视频内容文档。

                    【输出要求】
                    1. 先尽可能还原视频里的口播、字幕和关键信息；如果无法逐字转写，请给出高质量内容纪要。
                    2. 按 Markdown 输出：标题、视频概览、时间线/段落、核心观点、关键台词/字幕、画面要点、可复用素材、待核实信息。
                    3. 不要编造看不到或听不到的信息；无法确定的内容放到“待核实信息”。
                    4. 如果链接不是可访问视频，请直接说明原因。
                    """);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(joinUrl(effectiveBaseUrl, "/responses")))
                    .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 30)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("火山方舟 Responses API 返回 " + resp.statusCode() + ": " + truncate(resp.body(), 500));
            }

            String transcript = extractTranscript(resp.body());
            if (isBlank(transcript)) {
                throw new RuntimeException("火山方舟 Responses API 未返回 output_text: " + truncate(resp.body(), 500));
            }
            return transcript;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用火山方舟 Responses API 失败: " + e.getMessage(), e);
        }
    }

    public String transcriptToDocument(String transcript, String outputFormat, String requestedModel) {
        return transcriptToMode(transcript, OutputMode.DOCUMENT, outputFormat, requestedModel);
    }

    public String transcriptToMode(String transcript, OutputMode mode, String outputFormat, String requestedModel) {
        String format = normalizeFormat(outputFormat);
        String system = buildModePrompt(mode, format);
        String user = """
                【视频转写文本/字幕】
                %s
                """.formatted(transcript.trim());
        return deepSeek.chat(system, user, requestedModel);
    }

    public String documentToVideoScript(String document, String style, String duration, String requestedModel) {
        String system = """
                你是一位短视频编导和保险科普内容策划。请把用户给出的“视频内容文档”二次改编为一份可直接拍摄的视频脚本。

                【改编原则】
                1. 忠于原文信息，不编造原文没有的案例、产品结论、数字和承诺。
                2. 把散乱信息重组为适合短视频的观看节奏：先抓注意力，再讲清价值，最后给行动引导。
                3. 如果原文包含保险、理财、医疗相关内容，避免绝对化承诺、收益保证、疾病治愈暗示等高风险话术。
                4. 口播句子要短，每句尽量不超过 20 字，适合真人口播或数字人口播。

                【输出结构】
                ## 视频脚本
                - 标题:
                - 目标时长:
                - 风格定位:

                | 时间段 | 画面/动作 | 口播 | 屏幕字幕 |
                | --- | --- | --- | --- |

                ## 拍摄提示
                - 给出 3-5 条镜头、道具、节奏或 BGM 建议。

                只输出 Markdown，不要使用代码块包裹。
                """;
        StringBuilder user = new StringBuilder();
        if (style != null && !style.isBlank()) user.append("目标风格: ").append(style.trim()).append("\n");
        if (duration != null && !duration.isBlank()) user.append("目标时长: ").append(duration.trim()).append("\n");
        user.append("\n【视频内容文档】\n").append(document.trim());
        return deepSeek.chat(system, user.toString(), requestedModel);
    }

    public WorkflowResult documentAndScriptFromTranscript(String transcript,
                                                          String outputFormat,
                                                          String style,
                                                          String duration,
                                                          String requestedModel) {
        String document = transcriptToDocument(transcript, outputFormat, requestedModel);
        String script = documentToVideoScript(document, style, duration, requestedModel);
        return new WorkflowResult(document, script, deepSeek.resolveModel(requestedModel));
    }

    public WorkflowResult documentAndScriptFromVideoUrl(String videoUrl,
                                                        String outputFormat,
                                                        String style,
                                                        String duration,
                                                        String requestedModel) {
        String transcript = transcribeVideoUrl(videoUrl);
        return documentAndScriptFromTranscript(transcript, outputFormat, style, duration, requestedModel);
    }

    private String buildModePrompt(OutputMode mode, String outputFormat) {
        return switch (mode) {
            case XHS_NOTE -> """
                    你是一位小红书内容编辑。请把视频转写文本整理成一篇可发布的小红书笔记。

                    【要求】
                    1. 提炼一个强钩子标题，正文口语化、强共鸣、有实用价值。
                    2. 保留原视频的关键信息和观点，不要编造。
                    3. 结构建议：标题、开场钩子、核心要点、可执行建议、互动收尾、Hashtag。
                    4. 输出 Markdown。
                    """;
            case WECHAT_ARTICLE -> """
                    你是一位公众号编辑。请把视频转写文本整理成一篇公众号文章。

                    【要求】
                    1. 文章要有标题、摘要、导语、正文小标题和结尾总结。
                    2. 逻辑清晰，适合长文阅读，避免短视频口头禅。
                    3. 保留原视频的事实、观点和案例，不要编造。
                    4. 输出 Markdown。
                    """;
            case SUMMARY -> """
                    你是一位内容分析师。请把视频转写文本整理成内容总结。

                    【要求】
                    1. 输出：一句话概括、核心观点、关键细节、可复用素材、待核实信息。
                    2. 明确区分“原文确定提到”和“根据上下文推断”。
                    3. 输出 Markdown。
                    """;
            case SUBTITLE -> """
                    你是一位字幕整理师。请把视频转写文本整理成适合影视/短视频字幕的文本。

                    【要求】
                    1. 清理口误、重复、无意义语气词。
                    2. 每条字幕尽量 8-18 个中文字符，保持口播顺序。
                    3. 如果原文有时间戳，尽量保留；没有时间戳则用分行字幕文本输出。
                    4. 不要改写原意，不要扩写。
                    """;
            case VIDEO_SCRIPT -> """
                    你是一位短视频编导。请直接把视频转写文本改编成可拍摄的视频脚本。

                    【要求】
                    1. 输出标题、目标时长、镜头表格、口播、屏幕字幕和拍摄提示。
                    2. 保留原视频的核心信息，不编造事实。
                    3. 输出 Markdown。
                    """;
            case DOCUMENT -> """
                    你是一位专业内容整理编辑。请把视频转写文本整理成一份干净、可复用的%s文档。

                    【整理目标】
                    1. 清理 ASR 常见问题：重复口头禅、断句混乱、明显错别字、无意义语气词。
                    2. 保留原视频的核心事实、观点、案例、步骤和金句，不要编造原文没有的信息。
                    3. 按逻辑重组为：标题、内容摘要、正文结构、关键要点、可复用金句、待核实信息。
                    4. 如果原文信息不足，在“待核实信息”中标注，不要自行补齐。
                    5. 输出格式必须是 %s；如果是 txt，就不要使用 Markdown 表格。

                    只输出整理后的文档内容，不要写前后说明。
                    """.formatted(outputFormat.toUpperCase(Locale.ROOT), outputFormat);
        };
    }

    private String normalizeFormat(String raw) {
        if (raw == null || raw.isBlank()) return "md";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("markdown".equals(value)) return "md";
        if ("text".equals(value)) return "txt";
        return "txt".equals(value) ? "txt" : "md";
    }

    private String extractTranscript(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            String responsesText = outputText(root);
            if (!isBlank(responsesText)) return responsesText;
            for (String key : new String[]{"transcript", "text", "content", "result", "data"}) {
                String value = textAt(root.path(key));
                if (!isBlank(value)) return value;
            }
            JsonNode nested = root.path("data");
            for (String key : new String[]{"transcript", "text", "content", "result"}) {
                String value = textAt(nested.path(key));
                if (!isBlank(value)) return value;
            }
        } catch (Exception ignored) {
            if (!isBlank(body) && !body.trim().startsWith("{")) return body.trim();
        }
        return "";
    }

    private String outputText(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode block : content) {
                String type = block.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = block.path("text").asText("");
                    if (!isBlank(text)) sb.append(text).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String textAt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                String text = textAt(item);
                if (!isBlank(text)) sb.append(text).append("\n");
            }
            return sb.toString().trim();
        }
        if (node.isObject()) {
            for (String key : new String[]{"transcript", "text", "content", "sentence", "utterance"}) {
                String value = textAt(node.path(key));
                if (!isBlank(value)) return value;
            }
        }
        return "";
    }

    private String joinUrl(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null || path.isBlank() ? "/transcribe" : path.trim();
        if (b.endsWith("/") && p.startsWith("/")) return b.substring(0, b.length() - 1) + p;
        if (!b.endsWith("/") && !p.startsWith("/")) return b + "/" + p;
        return b + p;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
