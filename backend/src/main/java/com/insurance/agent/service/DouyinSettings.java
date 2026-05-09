package com.insurance.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 抖音运行时配置（内存持有）。
 * 启动时从 application.yml / application-local.yml 读取初始值；
 * 前端可通过 /api/douyin/cookie 接口在运行时更新，无需重启服务。
 */
@Component
public class DouyinSettings {

    private volatile String cookie;
    private final String jlwzApiId;
    private final String jlwzApiKey;

    public DouyinSettings(@Value("${douyin.cookie:}") String initialCookie,
                          @Value("${douyin.jlwz.api-id:}") String jlwzApiId,
                          @Value("${douyin.jlwz.api-key:}") String jlwzApiKey) {
        this.cookie = initialCookie != null ? initialCookie.trim() : "";
        this.jlwzApiId = jlwzApiId != null ? jlwzApiId.trim() : "";
        this.jlwzApiKey = jlwzApiKey != null ? jlwzApiKey.trim() : "";
    }

    public String getCookie() { return cookie; }

    public void setCookie(String cookie) {
        this.cookie = cookie != null ? cookie.trim() : "";
    }

    public boolean hasCookie() {
        return cookie != null && !cookie.isBlank();
    }

    public String getJlwzApiId() { return jlwzApiId; }

    public String getJlwzApiKey() { return jlwzApiKey; }

    public boolean hasJlwzApi() {
        return jlwzApiId != null && !jlwzApiId.isBlank()
                && jlwzApiKey != null && !jlwzApiKey.isBlank();
    }
}
