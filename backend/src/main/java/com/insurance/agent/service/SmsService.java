package com.insurance.agent.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${aliyun.sms.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name:}")
    private String signName;

    @Value("${aliyun.sms.template-code:}")
    private String templateCode;

    private Client client;

    @PostConstruct
    public void init() {
        if (isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            log.warn("[SMS] 阿里云短信未配置，将使用 mock 模式");
            return;
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("dysmsapi.aliyuncs.com");
            client = new Client(config);
            log.info("[SMS] 阿里云短信初始化成功");
        } catch (Exception e) {
            log.error("[SMS] 初始化失败: {}", e.getMessage(), e);
        }
    }

    public boolean isConfigured() {
        return client != null && !isBlank(signName) && !isBlank(templateCode)
                && !signName.equals("待填写") && !templateCode.equals("待填写");
    }

    /**
     * 发送验证码短信，返回是否成功。
     * 未配置时降级为 mock 模式（仅打印日志）。
     */
    public boolean sendCode(String phone, String code) {
        if (!isConfigured()) {
            log.info("[SMS][MOCK] phone={} code={}", phone, code);
            return true;
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(request);
            String resultCode = response.getBody().getCode();

            if ("OK".equals(resultCode)) {
                log.info("[SMS] 发送成功 phone={}", phone);
                return true;
            } else {
                log.error("[SMS] 发送失败 phone={} code={} message={}", phone, resultCode, response.getBody().getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("[SMS] 发送异常 phone={} error={}", phone, e.getMessage(), e);
            return false;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
