package com.insurance.agent.controller;

import com.insurance.agent.service.VideoMergeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoMergeService mergeService;

    @Value("${video.merge.output-dir:uploads/merged}")
    private String outputDir;

    public VideoController(VideoMergeService mergeService) {
        this.mergeService = mergeService;
    }

    /** 合并多段视频，返回合并后的访问 URL */
    @PostMapping("/merge")
    public ResponseEntity<?> mergeVideos(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) body.get("urls");
        if (urls == null || urls.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "urls 不能为空"));
        }
        try {
            String mergedUrl = mergeService.mergeSegments(urls);
            return ResponseEntity.ok(Map.of("url", mergedUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 下载/播放合并后的视频文件 */
    @GetMapping("/merged/{filename}")
    public ResponseEntity<Resource> serveMergedVideo(@PathVariable String filename) {
        // 安全校验：只允许访问 merged 目录下的 .mp4 文件
        if (!filename.matches("[\\w\\-]+\\.mp4")) {
            return ResponseEntity.badRequest().build();
        }
        var file = Paths.get(outputDir).resolve(filename).toFile();
        if (!file.exists()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(new FileSystemResource(file));
    }
}
