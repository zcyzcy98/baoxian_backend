package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.WechatDraftRequest;
import com.insurance.agent.dto.WechatDraftResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WechatDraftService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int TITLE_LIMIT = 32;
    private static final int AUTHOR_LIMIT = 16;
    private static final int DIGEST_LIMIT = 120;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path outputDir;
    private final String md2wechatBin;

    public WechatDraftService(
            @Value("${wechat.draft.output-dir:generated/wechat-drafts}") String outputDir,
            @Value("${MD2WECHAT_BIN:md2wechat}") String md2wechatBin) {
        this.outputDir = Paths.get(outputDir);
        this.md2wechatBin = md2wechatBin;
    }

    public WechatDraftResponse create(WechatDraftRequest req) {
        String title = trimToLimit(required(req.getTitle(), "title 不能为空"), TITLE_LIMIT);
        String content = required(req.getContent(), "content 不能为空");
        String author = trimToLimit(blankToDefault(req.getAuthor(), "保险助手"), AUTHOR_LIMIT);
        String digest = trimToLimit(blankToDefault(req.getDigest(), buildDigest(content)), DIGEST_LIMIT);
        String theme = blankToDefault(req.getTheme(), "default");

        Path markdownPath = writeMarkdown(title, author, digest, content);
        List<String> command = buildCommand(req, markdownPath, title, author, digest, theme);

        WechatDraftResponse resp = new WechatDraftResponse();
        resp.setPublished(false);
        resp.setMarkdownPath(markdownPath.toAbsolutePath().toString());
        resp.setCommandPreview(maskCommand(command));

        if (!req.isPublish()) {
            resp.setMessage("已生成公众号 Markdown，当前为预览模式，未调用微信草稿箱接口。");
            return resp;
        }

        validatePublishConfig(req);
        ProcessResult result = runCommand(command, req);
        resp.setStdout(result.stdout());
        resp.setStderr(result.stderr());
        if (result.exitCode() != 0) {
            throw new IllegalStateException("md2wechat 执行失败(" + result.exitCode() + "): " + firstNonBlank(result.stderr(), result.stdout()));
        }
        resp.setPublished(true);
        resp.setMediaId(extractMediaId(result.stdout()));
        resp.setMessage(resp.getMediaId() == null || resp.getMediaId().isBlank()
                ? "已调用 md2wechat，未能从输出中解析 media_id，请查看 stdout。"
                : "已创建微信公众号草稿。");
        return resp;
    }

    private Path writeMarkdown(String title, String author, String digest, String content) {
        try {
            Files.createDirectories(outputDir);
            String slug = sanitizeFileName(title);
            Path path = outputDir.resolve(FILE_TS.format(LocalDateTime.now()) + "-" + slug + ".md");
            String markdown = """
                    ---
                    title: "%s"
                    author: "%s"
                    digest: "%s"
                    ---

                    # %s

                    %s
                    """.formatted(yamlEscape(title), yamlEscape(author), yamlEscape(digest), title, content.trim());
            Files.writeString(path, markdown, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("生成公众号 Markdown 失败: " + e.getMessage(), e);
        }
    }

    private List<String> buildCommand(WechatDraftRequest req, Path markdownPath, String title, String author, String digest, String theme) {
        List<String> command = new ArrayList<>();
        command.add(md2wechatBin);
        command.add("convert");
        command.add(markdownPath.toAbsolutePath().toString());
        command.add("--draft");
        if (!isBlank(req.getCoverImagePath()) || isBlank(req.getCoverMediaId())) {
            command.add("--cover");
            command.add(blankToDefault(req.getCoverImagePath(), "<COVER_IMAGE_PATH>"));
        } else {
            command.add("--cover-media-id");
            command.add(blankToDefault(req.getCoverMediaId(), "<COVER_MEDIA_ID>"));
        }
        command.add("--title");
        command.add(title);
        command.add("--author");
        command.add(author);
        command.add("--digest");
        command.add(digest);
        command.add("--theme");
        command.add(theme);
        command.add("--json");
        return command;
    }

    private void validatePublishConfig(WechatDraftRequest req) {
        required(req.getAppId(), "AppID 不能为空");
        required(req.getAppSecret(), "AppSecret 不能为空");
        if (isBlank(req.getCoverImagePath()) && isBlank(req.getCoverMediaId())) {
            throw new IllegalArgumentException("封面不能为空。当前本机 md2wechat 2.0.4 支持 --cover 本地路径；升级新版后也可用 cover media_id。");
        }
    }

    private ProcessResult runCommand(List<String> command, WechatDraftRequest req) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            env.put("WECHAT_APPID", req.getAppId().trim());
            env.put("WECHAT_SECRET", req.getAppSecret().trim());
            if (!isBlank(req.getMd2wechatApiKey())) {
                env.put("MD2WECHAT_API_KEY", req.getMd2wechatApiKey().trim());
            }
            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, stdout, stderr);
        } catch (IOException e) {
            throw new IllegalStateException("无法启动 md2wechat，请确认已安装 CLI 或配置 MD2WECHAT_BIN: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("md2wechat 执行被中断", e);
        }
    }

    private String extractMediaId(String stdout) {
        if (isBlank(stdout)) return null;
        try {
            JsonNode root = objectMapper.readTree(stdout);
            for (String field : List.of("media_id", "draft_media_id", "draft_id")) {
                JsonNode node = root.findValue(field);
                if (node != null && node.isTextual()) return node.asText();
            }
        } catch (Exception ignored) {
            // Some versions may print non-JSON text even when the command succeeds.
        }
        return null;
    }

    private String buildDigest(String content) {
        String plain = content
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[*_`>\\-#]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return trimToLimit(plain, DIGEST_LIMIT);
    }

    private String maskCommand(List<String> command) {
        return String.join(" ", command);
    }

    private String sanitizeFileName(String text) {
        String value = text == null ? "article" : text.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        value = value.replaceAll("^-+|-+$", "");
        if (value.isBlank()) return "article";
        return value.length() > 24 ? value.substring(0, 24) : value;
    }

    private String yamlEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimToLimit(String value, int limit) {
        if (value == null) return "";
        String trimmed = value.trim();
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count <= limit) return trimmed;
        return new String(trimmed.codePoints().limit(limit).toArray(), 0, limit);
    }

    private String required(String value, String message) {
        if (isBlank(value)) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {}
}
