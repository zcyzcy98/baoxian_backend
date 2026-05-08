package com.insurance.agent.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Date;

@Service
public class OssService {

    private static final Logger log = LoggerFactory.getLogger(OssService.class);
    // 签名 URL 有效期：24 小时，足够 AtlasCloud 下载使用
    private static final long SIGNED_URL_EXPIRES_MS = 24 * 3600 * 1000L;

    @Value("${oss.enabled:false}")
    private boolean enabled;

    @Value("${oss.endpoint:}")
    private String endpoint;

    @Value("${oss.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${oss.bucket-name:}")
    private String bucketName;

    @Value("${oss.dir:avatars}")
    private String dir;

    public boolean isEnabled() {
        return enabled && !endpoint.isBlank() && !accessKeyId.isBlank()
                && !accessKeySecret.isBlank() && !bucketName.isBlank();
    }

    /**
     * 上传文件到 OSS，返回带签名的临时公网 URL（24小时有效）。
     * Bucket 无需设置公共读，AtlasCloud 可直接通过签名 URL 下载图片。
     */
    public String upload(String filename, InputStream inputStream, String contentType, long size) {
        String key = dir.replaceAll("/$", "") + "/" + filename;
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(size);
            client.putObject(bucketName, key, inputStream, meta);

            // 生成带签名的临时 URL，AtlasCloud 无需鉴权即可访问
            Date expiration = new Date(System.currentTimeMillis() + SIGNED_URL_EXPIRES_MS);
            String signedUrl = client.generatePresignedUrl(bucketName, key, expiration).toString();
            log.info("[OSS] 上传成功，签名 URL（24h）: {}", signedUrl);
            return signedUrl;
        } finally {
            client.shutdown();
        }
    }
}
