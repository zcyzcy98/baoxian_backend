package com.insurance.agent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/avatar")
public class AvatarController {

    private static final Logger log = LoggerFactory.getLogger(AvatarController.class);
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Value("${avatar.upload-dir:uploads/avatars}")
    private String uploadDir;

    @Value("${avatar.public-base-url:http://localhost:8888}")
    private String publicBaseUrl;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        if (file.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过 20MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "只支持图片格式（jpg/png/webp 等）"));
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : ".jpg";
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path dest = dir.resolve(filename);
        file.transferTo(dest);

        String url = publicBaseUrl.replaceAll("/$", "") + "/api/avatar/image/" + filename;
        log.info("[Avatar] 上传成功: {} → {}", filename, url);
        return ResponseEntity.ok(Map.of("url", url, "filename", filename));
    }

    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> serve(@PathVariable String filename) throws IOException {
        // 安全检查：不允许路径遍历
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = Paths.get(uploadDir).resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = Files.readAllBytes(file);
        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(bytes);
    }
}
