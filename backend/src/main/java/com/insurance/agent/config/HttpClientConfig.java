package com.insurance.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 统一创建支持系统代理的 HttpClient。
 * Java 的 HttpClient 默认不读取 HTTPS_PROXY / https_proxy 环境变量，
 * 需要手动解析并注入 ProxySelector，否则在需要代理的网络环境中会 SSL handshake 失败。
 */
@Configuration
public class HttpClientConfig {
    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    @Bean
    public HttpClient sharedHttpClient() {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        ProxySelector proxy = detectProxy();
        if (proxy != null) {
            builder.proxy(proxy);
        }

        return builder.build();
    }

    private ProxySelector detectProxy() {
        // 按优先级依次尝试环境变量
        for (String key : new String[]{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) {
                try {
                    URI uri = URI.create(val);
                    int port = uri.getPort() > 0 ? uri.getPort() : 7890;
                    log.info("[HttpClient] 检测到代理配置 {}={} → {}:{}", key, val, uri.getHost(), port);
                    return ProxySelector.of(new InetSocketAddress(uri.getHost(), port));
                } catch (Exception e) {
                    log.warn("[HttpClient] 解析代理地址失败: {} = {} error={}", key, val, e.getMessage());
                }
            }
        }
        log.debug("[HttpClient] 未检测到代理环境变量，直连模式");
        return null;
    }
}
