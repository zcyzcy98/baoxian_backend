package com.insurance.agent.service;

import com.insurance.agent.dto.WechatArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抓取公众号文章 (mp.weixin.qq.com/s?__biz=...&mid=...&idx=...).
 *
 * 思路完全镜像 XhsExtractService: HTTP GET 拿 SSR HTML → 抠出有结构的字段.
 * 提取规则参考自 wechat_spider 的 utils/contentHandler.js.
 *
 * 注意: 阅读量 / 点赞数 / 在看数需要登录态走 ajax /mp/getappmsgext, 这里不取.
 */
@Service
public class WechatExtractService {
    private static final Logger log = LoggerFactory.getLogger(WechatExtractService.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.42(0x18002a2e) "
                    + "NetType/WIFI Language/zh_CN";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 内嵌 JS 变量正则 (来自 wechat_spider/utils/contentHandler.js)
    private static final Pattern P_TITLE      = Pattern.compile("var\\s+msg_title\\s*=\\s*'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern P_CT         = Pattern.compile("var\\s+ct\\s*=\\s*\"(\\d+)\"");
    private static final Pattern P_SOURCE_URL = Pattern.compile("var\\s+msg_source_url\\s*=\\s*'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern P_COVER      = Pattern.compile("var\\s+msg_cdn_url\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_DIGEST     = Pattern.compile("var\\s+msg_desc\\s*=\\s*htmlDecode\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)");
    private static final Pattern P_USERNAME   = Pattern.compile("var\\s+user_name\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_NICKNAME   = Pattern.compile("var\\s+nickname\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_HEADIMG    = Pattern.compile("var\\s+hd_head_img\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_WECHATID   = Pattern.compile("<span class=\"profile_meta_value\">(.+?)</span>");

    // 图片型文章入口
    private static final Pattern P_IMG_TITLE  = Pattern.compile("d\\.title\\s*=\\s*[^']*'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern P_IMG_CT     = Pattern.compile("d\\.ct\\s*=\\s*[^']*'(\\d+)'");
    private static final Pattern P_IMG_USER   = Pattern.compile("user_name:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern P_IMG_NICK   = Pattern.compile("d\\.nick_name\\s*=\\s*[^']*'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern P_IMG_HEAD   = Pattern.compile("d\\.hd_head_img\\s*=\\s*[^']*'((?:[^'\\\\]|\\\\.)*)'");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public WechatArticle extract(String urlInput) {
        return extract(urlInput, null);
    }

    public WechatArticle extract(String urlInput, String cookie) {
        if (urlInput == null || urlInput.isBlank()) {
            throw new IllegalArgumentException("链接不能为空");
        }
        String url = sanitizeUrl(urlInput);
        if (!url.contains("mp.weixin.qq.com") && !url.contains("weixin.qq.com")) {
            throw new IllegalArgumentException("仅支持 mp.weixin.qq.com 公众号文章链接");
        }

        String html = fetchHtml(url, cookie);

        // 失效检测 (源自 contentHandler.js)
        if (html.contains("global_error_msg") || html.contains("icon_msg warn")) {
            WechatArticle fail = new WechatArticle();
            fail.setUrl(url);
            fail.setType("fail");
            fail.setTitle("(文章已失效或被删除)");
            return fail;
        }

        boolean isImageType = html.contains("id=\"img_list\"")
                && extractFirst(html, P_WECHATID) == null;

        WechatArticle a = new WechatArticle();
        a.setUrl(url);
        Map<String, String> ids = parseIdsFromUrl(url);
        a.setMsgBiz(ids.get("__biz"));
        a.setMsgMid(ids.get("mid"));
        a.setMsgIdx(ids.get("idx"));

        if (isImageType) {
            populateImageType(a, html);
        } else {
            populateNormalType(a, html);
        }
        return a;
    }

    private void populateNormalType(WechatArticle a, String html) {
        a.setType("normal");
        a.setTitle(htmlUnescape(extractFirst(html, P_TITLE)));
        a.setDigest(htmlUnescape(extractFirst(html, P_DIGEST)));
        a.setSourceUrl(extractFirst(html, P_SOURCE_URL));
        a.setCover(extractFirst(html, P_COVER));
        a.setAccountName(htmlUnescape(extractFirst(html, P_NICKNAME)));
        a.setAccountAvatar(extractFirst(html, P_HEADIMG));

        String wechatId = extractFirst(html, P_WECHATID);
        String userName = extractFirst(html, P_USERNAME);
        // 如果 wechatId 含中文, 说明没设过自定义 ID, 用 username
        if (wechatId != null && wechatId.matches(".*[一-龥].*")) wechatId = userName;
        a.setAccountId(wechatId == null ? userName : wechatId);

        String ct = extractFirst(html, P_CT);
        if (ct != null) {
            try {
                a.setPublishTime(TIME_FORMAT.format(new Date(Long.parseLong(ct) * 1000L)));
            } catch (NumberFormatException ignored) {}
        }

        // 正文: Jsoup 取 #js_content
        Document doc = Jsoup.parse(html);
        Element content = doc.getElementById("js_content");
        if (content != null) {
            a.setContentHtml(content.html());
            a.setContent(extractTextWithBreaks(content));
            a.setImageUrls(extractImages(content));
        } else {
            a.setContent("");
            a.setContentHtml("");
            a.setImageUrls(new ArrayList<>());
        }
        a.setTags(extractTags(a.getContent()));
    }

    private void populateImageType(WechatArticle a, String html) {
        a.setType("image");
        a.setTitle(htmlUnescape(extractFirst(html, P_IMG_TITLE)));
        a.setAccountId(extractFirst(html, P_IMG_USER));
        a.setAccountName(htmlUnescape(extractFirst(html, P_IMG_NICK)));
        a.setAccountAvatar(extractFirst(html, P_IMG_HEAD));

        String ct = extractFirst(html, P_IMG_CT);
        if (ct != null) {
            try {
                a.setPublishTime(TIME_FORMAT.format(new Date(Long.parseLong(ct) * 1000L)));
            } catch (NumberFormatException ignored) {}
        }

        Document doc = Jsoup.parse(html);
        Elements imgs = doc.select("#img_list img");
        List<String> urls = new ArrayList<>();
        for (Element img : imgs) {
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
            if (src != null && !src.isBlank()) urls.add(src);
        }
        a.setImageUrls(urls);
        a.setCover(urls.isEmpty() ? null : urls.get(0));

        // 图片型也尝试拿一段描述
        Element desc = doc.selectFirst(".rich_media_meta_text, .img_desc");
        a.setContent(desc != null ? desc.text().trim() : "");
        a.setContentHtml(desc != null ? desc.html() : "");
        a.setTags(new ArrayList<>());
    }

    private String sanitizeUrl(String input) {
        String url = input.trim();
        int spaceIdx = url.indexOf(' ');
        if (spaceIdx > 0) url = url.substring(0, spaceIdx);
        if (!url.startsWith("http")) url = "https://" + url;
        return url;
    }

    private String fetchHtml(String url, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://mp.weixin.qq.com/")
                .GET();
        if (cookie != null && !cookie.isBlank()) builder.header("Cookie", cookie);
        try {
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("拉取公众号页失败 HTTP " + resp.statusCode());
            }
            String body = resp.body();
            if (body == null || body.isBlank()) {
                throw new RuntimeException("公众号页响应为空, 链接可能无效");
            }
            // 公众号偶尔返回需要环境校验的中转页
            if (body.contains("environment is abnormal") || body.contains("verify_identity_url")) {
                throw new RuntimeException("公众号判定为异常环境, 暂时无法直接抓取 (链接可能需要在微信内打开)");
            }
            return body;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("拉取公众号页失败: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> parseIdsFromUrl(String url) {
        Map<String, String> out = new HashMap<>();
        int q = url.indexOf('?');
        if (q < 0) return out;
        String query = url.substring(q + 1);
        // 切掉 fragment
        int hash = query.indexOf('#');
        if (hash >= 0) query = query.substring(0, hash);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                out.put(key, val);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static String extractFirst(String text, Pattern p) {
        if (text == null) return null;
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 把 #js_content 里的元素转成保留段落换行的纯文本. */
    private static String extractTextWithBreaks(Element root) {
        StringBuilder sb = new StringBuilder();
        for (Element block : root.select("p, section, div, h1, h2, h3, h4, li, blockquote, br")) {
            String text = block.ownText().trim();
            if (text.isEmpty() && "br".equalsIgnoreCase(block.tagName())) {
                sb.append('\n');
                continue;
            }
            if (!text.isEmpty()) sb.append(text).append('\n');
        }
        String out = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        return out.isEmpty() ? root.text().trim() : out;
    }

    private static List<String> extractImages(Element content) {
        List<String> urls = new ArrayList<>();
        for (Element img : content.select("img")) {
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
            if (src != null && !src.isBlank() && !src.startsWith("data:")) urls.add(src);
        }
        return urls;
    }

    private static List<String> extractTags(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        // 公众号正文里很少有 #tag#, 但还是兼容一下
        Pattern p = Pattern.compile("#([^#\\s]{2,20})#");
        Matcher m = p.matcher(text);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) seen.add(m.group(1).trim());
        return new ArrayList<>(seen);
    }

    private static String htmlUnescape(String s) {
        if (s == null) return null;
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\\"", "\"")
                .replace("\\'", "'");
    }
}
