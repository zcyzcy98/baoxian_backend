package com.insurance.agent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * 图片反代：浏览器直接访问微信 CDN 图片会被防盗链拦截（"此图来自微信公众平台，未经允许不可引用"）。
 * 通过后端加正确的 Referer 头转发，规避限制。
 *
 * 只允许代理白名单域名，防止 SSRF。
 */
@RestController
@RequestMapping("/api/image-proxy")
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "mmbiz.qpic.cn",
            "mmbiz.qlogo.cn",
            "wx.qlogo.cn",
            "res.wx.qq.com",
            "img.xhscdn.com",
            "ci.xiaohongshu.com",
            "sns-img-hw.xhscdn.com",
            "sns-img-bd.xhscdn.com",
            "sns-avatar-hw.xhscdn.com"
    );

    private static final int MAX_BYTES = 20 * 1024 * 1024; // 20 MB

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam("url") String rawUrl) {
        String url;
        try {
            url = URLDecoder.decode(rawUrl, StandardCharsets.UTF_8).trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return ResponseEntity.badRequest().build();
            }
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || ALLOWED_HOSTS.stream().noneMatch(host::endsWith)) {
                log.warn("[ImageProxy] 域名不在白名单: {}", host);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String referer = url.contains("weixin") || url.contains("qpic") || url.contains("qlogo")
                    ? "https://mp.weixin.qq.com/"
                    : "https://www.xiaohongshu.com/";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() / 100 != 2) {
                log.warn("[ImageProxy] 上游返回 {}: {}", resp.statusCode(), url);
                return ResponseEntity.status(resp.statusCode()).build();
            }

            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                return ResponseEntity.noContent().build();
            }
            if (body.length > MAX_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }

            String contentType = resp.headers().firstValue("Content-Type")
                    .orElse("image/jpeg");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
            return new ResponseEntity<>(body, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.warn("[ImageProxy] 代理失败 {}: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
