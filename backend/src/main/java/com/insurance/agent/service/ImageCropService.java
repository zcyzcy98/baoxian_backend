package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;

/**
 * 公众号配图后处理：把模型生成的原图按目标比例做中心裁切，保存到本地静态目录后返回新 URL。
 * 仅用于 21:9 这种 HiAPI 原生不支持的比例。其他比例不要走这里，直接用原图。
 */
@Service
public class ImageCropService {

    private static final Logger log = LoggerFactory.getLogger(ImageCropService.class);
    private static final long MAX_BYTES = 30L * 1024 * 1024;

    @Value("${gzh.cover.dir:uploads/gzh-covers}")
    private String saveDir;

    @Value("${gzh.cover.public-base-url:${avatar.public-base-url:http://localhost:8888}}")
    private String publicBaseUrl;

    private final OssService ossService;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ImageCropService(OssService ossService) {
        this.ossService = ossService;
    }

    /**
     * 把 sourceUrl 指向的图片按 targetRatio 做中心裁切，保存后返回新 URL。
     *
     * @param sourceUrl   原图 URL（HiAPI / Seedream 返回的远程图片）
     * @param targetRatio 如 "21:9"
     * @return 裁切后图片的可访问 URL
     */
    public String cropToRatio(String sourceUrl, String targetRatio) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("源图 URL 不能为空");
        }
        double targetAR = parseRatio(targetRatio);
        try {
            byte[] sourceBytes = downloadImage(sourceUrl);
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (src == null) {
                throw new IOException("源图无法解码");
            }
            BufferedImage cropped = centerCrop(src, targetAR);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", out);
            byte[] pngBytes = out.toByteArray();

            String filename = "gzh-" + UUID.randomUUID().toString().replace("-", "") + ".png";

            // OSS 优先
            if (ossService.isEnabled()) {
                try (InputStream in = new ByteArrayInputStream(pngBytes)) {
                    String ossUrl = ossService.upload(filename, in, "image/png", pngBytes.length);
                    log.info("[ImageCrop] 裁切完成 (OSS): {} → {} ({}:{} → {})",
                            sourceUrl.substring(0, Math.min(60, sourceUrl.length())),
                            ossUrl, src.getWidth(), src.getHeight(), targetRatio);
                    return ossUrl;
                } catch (Exception e) {
                    log.warn("[ImageCrop] OSS 上传失败，降级本地: {}", e.getMessage());
                }
            }

            // 本地保存
            Path dir = Paths.get(saveDir);
            Files.createDirectories(dir);
            Path dest = dir.resolve(filename);
            Files.write(dest, pngBytes);

            String url = publicBaseUrl.replaceAll("/$", "") + "/api/gzh-cover/image/" + filename;
            log.info("[ImageCrop] 裁切完成 (本地): {} → {} ({}x{} → {}x{})",
                    sourceUrl.substring(0, Math.min(60, sourceUrl.length())),
                    url, src.getWidth(), src.getHeight(),
                    cropped.getWidth(), cropped.getHeight());
            return url;
        } catch (IOException e) {
            throw new RuntimeException("图片裁切失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("图片下载被中断", e);
        }
    }

    private BufferedImage centerCrop(BufferedImage src, double targetAR) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        double srcAR = (double) sw / sh;

        int cropW, cropH;
        if (srcAR > targetAR) {
            // 源比目标更宽 → 裁宽
            cropH = sh;
            cropW = (int) Math.round(sh * targetAR);
        } else {
            // 源比目标更高（或相等）→ 裁高
            cropW = sw;
            cropH = (int) Math.round(sw / targetAR);
        }
        int x = (sw - cropW) / 2;
        int y = (sh - cropH) / 2;
        return src.getSubimage(Math.max(0, x), Math.max(0, y),
                Math.min(cropW, sw), Math.min(cropH, sh));
    }

    private byte[] downloadImage(String url) throws IOException, InterruptedException {
        // 兼容 data:image/...;base64
        if (url.startsWith("data:image")) {
            int comma = url.indexOf(',');
            if (comma < 0) throw new IOException("非法 data URL");
            String b64 = url.substring(comma + 1);
            return java.util.Base64.getDecoder().decode(b64);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("下载图片失败 HTTP " + resp.statusCode());
        }
        byte[] body = resp.body();
        if (body.length > MAX_BYTES) {
            throw new IOException("源图过大: " + body.length + " bytes");
        }
        return body;
    }

    private static double parseRatio(String ratio) {
        if (ratio == null) throw new IllegalArgumentException("ratio 为空");
        String[] parts = ratio.trim().split(":");
        if (parts.length != 2) throw new IllegalArgumentException("非法 ratio: " + ratio);
        double w = Double.parseDouble(parts[0].trim());
        double h = Double.parseDouble(parts[1].trim());
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("非法 ratio: " + ratio);
        return w / h;
    }
}
