package com.insurance.agent.service;

import org.mp4parser.Container;
import org.mp4parser.muxer.Movie;
import org.mp4parser.muxer.Track;
import org.mp4parser.muxer.builder.DefaultMp4Builder;
import org.mp4parser.muxer.container.mp4.MovieCreator;
import org.mp4parser.muxer.tracks.AppendTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Service
public class VideoMergeService {

    private static final Logger log = LoggerFactory.getLogger(VideoMergeService.class);

    @Value("${video.merge.output-dir:uploads/merged}")
    private String outputDir;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * 下载多段视频 URL，用 mp4parser 无损拼接，返回合并后文件的本地访问路径。
     */
    public String mergeSegments(List<String> videoUrls) throws Exception {
        if (videoUrls == null || videoUrls.isEmpty()) throw new IllegalArgumentException("视频 URL 列表不能为空");
        if (videoUrls.size() == 1) {
            // 只有一段，直接下载保存
            Path out = prepareOutputPath();
            downloadToFile(videoUrls.get(0), out);
            return toAccessUrl(out);
        }

        // 下载所有片段到临时文件
        List<Path> tempFiles = new ArrayList<>();
        try {
            for (int i = 0; i < videoUrls.size(); i++) {
                Path tmp = Files.createTempFile("seg_" + i + "_", ".mp4");
                log.info("[Merge] 下载第 {}/{} 段: {}", i + 1, videoUrls.size(), videoUrls.get(i));
                downloadToFile(videoUrls.get(i), tmp);
                tempFiles.add(tmp);
            }

            // mp4parser 拼接
            log.info("[Merge] 开始拼接 {} 个片段", tempFiles.size());
            List<Movie> movies = new ArrayList<>();
            for (Path p : tempFiles) {
                movies.add(MovieCreator.build(p.toString()));
            }

            // 收集视频轨和音频轨
            List<Track> videoTracks = new ArrayList<>();
            List<Track> audioTracks = new ArrayList<>();
            for (Movie m : movies) {
                for (Track t : m.getTracks()) {
                    if ("vide".equals(t.getHandler())) videoTracks.add(t);
                    else if ("soun".equals(t.getHandler())) audioTracks.add(t);
                }
            }

            Movie merged = new Movie();
            if (!videoTracks.isEmpty()) {
                merged.addTrack(new AppendTrack(videoTracks.toArray(new Track[0])));
            }
            if (!audioTracks.isEmpty()) {
                merged.addTrack(new AppendTrack(audioTracks.toArray(new Track[0])));
            }

            Path outFile = prepareOutputPath();
            Container mp4 = new DefaultMp4Builder().build(merged);
            try (FileChannel fc = new FileOutputStream(outFile.toFile()).getChannel()) {
                mp4.writeContainer(fc);
            }

            log.info("[Merge] 拼接完成，输出: {}", outFile);
            return toAccessUrl(outFile);

        } finally {
            for (Path tmp : tempFiles) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        }
    }

    private void downloadToFile(String url, Path dest) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .GET()
                .build();
        HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("下载视频失败 " + resp.statusCode() + " url=" + url);
        }
    }

    private Path prepareOutputPath() throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        String filename = "merged_" + System.currentTimeMillis() + ".mp4";
        return dir.resolve(filename);
    }

    private String toAccessUrl(Path filePath) {
        // 返回相对于 uploads/ 的 API 路径，前端通过 /api/video/merged/{filename} 访问
        return "/api/video/merged/" + filePath.getFileName().toString();
    }
}
