package com.insurance.agent.service;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 抖音 X-Bogus 请求签名生成器。
 * 算法移植自 DLWangSan/douyin_parse (xbogus.py)。
 *
 * 调用：new XBogus().generate(queryString)
 * 返回 X-Bogus 值，拼到请求 URL 末尾即可。
 */
class XBogus {

    private static final String CHARSET_STR =
            "Dkdpgh4ZKsQB80/Mfvw36XI1R25-WUAlEi7NLboqYTOPuzmFjJnryx9HVGcaStCe=";
    private static final byte[] UA_KEY = {0x00, 0x01, 0x0c};
    private static final Charset ISO = Charset.forName("ISO-8859-1");

    static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private final String userAgent;

    XBogus() { this(null); }
    XBogus(String ua) { this.userAgent = (ua != null && !ua.isBlank()) ? ua : DEFAULT_UA; }

    /**
     * 对查询字符串（已拼好的 param1=v1&param2=v2...）生成 X-Bogus 值。
     */
    String generate(String queryString) throws Exception {
        // array1: RC4(UA_KEY, user_agent) → base64 → 单次 MD5
        // Python: md5_str_to_array(md5(base64_string))  ← 只有一个 md5，不是 md5_encrypt
        byte[] uaEncrypted = rc4(UA_KEY, userAgent.getBytes(ISO));
        String uaBase64 = Base64.getEncoder().encodeToString(uaEncrypted);
        byte[] array1 = hexToBytes(md5Hex(uaBase64.getBytes(ISO)));

        // array2: MD5 of hex-decoded constant (MD5 of empty string)
        // Python: md5_str_to_array(md5(md5_str_to_array("d41d...")))
        //       = hexToBytes(md5Hex(hexToBytes("d41d...")))  ← 单次 MD5
        byte[] array2 = hexToBytes(md5Hex(hexToBytes("d41d8cd98f00b204e9800998ecf8427e")));

        // urlPathArray: 双重 MD5（md5_encrypt 函数）
        // Python: md5_str_to_array(md5(md5_str_to_array(md5(url_path))))
        byte[] urlPathArray = hexToBytes(md5Hex(hexToBytes(md5Hex(queryString.getBytes(ISO)))));

        // 时间戳和固定常量
        int timer = (int) (System.currentTimeMillis() / 1000);
        int ct = 536919696;

        // 18 个基础值（Python 中 0.00390625 → int → 0，对 XOR 无影响）
        int[] v = {
            64, 0, 1, 12,
            urlPathArray[14] & 0xFF, urlPathArray[15] & 0xFF,
            array2[14] & 0xFF, array2[15] & 0xFF,
            array1[14] & 0xFF, array1[15] & 0xFF,
            (timer >> 24) & 255, (timer >> 16) & 255, (timer >> 8) & 255, timer & 255,
            (ct >> 24) & 255, (ct >> 16) & 255, (ct >> 8) & 255, ct & 255
        };

        // XOR 校验和
        int xorResult = v[0];
        for (int i = 1; i < v.length; i++) xorResult ^= v[i];

        // 19 字节 payload
        byte[] payload = new byte[19];
        for (int i = 0; i < v.length; i++) payload[i] = (byte) v[i];
        payload[18] = (byte) xorResult;

        // RC4-encrypt with key [0xFF]，再在头部拼 [2, 255]
        byte[] encrypted = rc4(new byte[]{(byte) 0xFF}, payload);
        byte[] garbled = new byte[2 + encrypted.length];
        garbled[0] = 2;
        garbled[1] = (byte) 255;
        System.arraycopy(encrypted, 0, garbled, 2, encrypted.length);

        // 自定义 base64：3 字节 → 4 字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < garbled.length; i += 3) {
            sb.append(encode3(garbled[i] & 0xFF, garbled[i + 1] & 0xFF, garbled[i + 2] & 0xFF));
        }
        return sb.toString();
    }

    // ─── 私有辅助 ────────────────────────────────────────────────────────────

    private String encode3(int a, int b, int c) {
        int x = (a << 16) | (b << 8) | c;
        return "" + CHARSET_STR.charAt((x >> 18) & 63)
                  + CHARSET_STR.charAt((x >> 12) & 63)
                  + CHARSET_STR.charAt((x >> 6) & 63)
                  + CHARSET_STR.charAt(x & 63);
    }

    private static byte[] rc4(byte[] key, byte[] data) {
        int[] S = new int[256];
        for (int i = 0; i < 256; i++) S[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + S[i] + (key[i % key.length] & 0xFF)) % 256;
            int t = S[i]; S[i] = S[j]; S[j] = t;
        }
        byte[] out = new byte[data.length];
        int i = 0; j = 0;
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) % 256;
            j = (j + S[i]) % 256;
            int t = S[i]; S[i] = S[j]; S[j] = t;
            out[k] = (byte) ((data[k] & 0xFF) ^ S[(S[i] + S[j]) % 256]);
        }
        return out;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String md5Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
