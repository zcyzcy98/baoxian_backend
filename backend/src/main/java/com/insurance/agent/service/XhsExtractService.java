package com.insurance.agent.service;

import com.insurance.agent.dto.XhsNote;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class XhsExtractService {
    private static final Logger log = LoggerFactory.getLogger(XhsExtractService.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final String INITIAL_STATE_PREFIX = "window.__INITIAL_STATE__=";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public XhsNote extract(String urlInput) {
        return extract(urlInput, null);
    }

    public XhsNote extract(String urlInput, String cookie) {
        if (urlInput == null || urlInput.isBlank()) {
            throw new IllegalArgumentException("链接不能为空");
        }
        String url = sanitizeUrl(urlInput);
        String html = fetchHtml(url, cookie);
        String scriptText = findInitialStateScript(html);
        if (scriptText == null) {
            throw new RuntimeException("未在页面中找到 window.__INITIAL_STATE__,可能是链接无效或小红书页面结构已变更");
        }
        Map<String, Object> root = parseInitialState(scriptText);
        Map<String, Object> note = locateNote(root);
        if (note == null) {
            throw new RuntimeException("无法从页面数据中定位笔记内容,可能小红书结构调整了。请稍后再试或联系开发者");
        }
        return mapToNote(note);
    }

    private String sanitizeUrl(String input) {
        if (input == null) return "";
        // Extract the first http(s) URL from potentially messy share-text
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("https?://[^\\s\\u4e00-\\u9fa5\\[\\]【】「」（）()]+")
                .matcher(input.trim());
        String url = m.find() ? m.group() : input.trim();
        // Strip trailing punctuation that may have been captured
        url = url.replaceAll("[,，。.!！?？]+$", "");
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
                .header("Referer", "https://www.xiaohongshu.com/explore")
                .GET();
        if (cookie != null && !cookie.isBlank()) builder.header("Cookie", cookie);
        try {
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("请求小红书失败,HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("请求小红书出错: " + e.getMessage(), e);
        }
    }

    private String findInitialStateScript(String html) {
        Document doc = Jsoup.parse(html);
        Elements scripts = doc.select("script");
        for (int i = scripts.size() - 1; i >= 0; i--) {
            Element s = scripts.get(i);
            String text = s.html();
            if (text != null && text.startsWith(INITIAL_STATE_PREFIX)) {
                return text;
            }
            String data = s.data();
            if (data != null && data.startsWith(INITIAL_STATE_PREFIX)) {
                return data;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInitialState(String scriptText) {
        String body = scriptText.substring(INITIAL_STATE_PREFIX.length());
        body = body.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "");
        if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
        body = body.replace(":undefined", ":null").replace(": undefined", ": null");
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(body);
        if (!(parsed instanceof Map)) {
            throw new RuntimeException("INITIAL_STATE 解析结果不是对象");
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> locateNote(Map<String, Object> root) {
        Object pcAttempt = deepGet(root, "note", "noteDetailMap");
        if (pcAttempt instanceof Map<?, ?> map && !map.isEmpty()) {
            Object lastEntry = null;
            for (Object v : ((Map<String, Object>) map).values()) lastEntry = v;
            Object note = deepGet(lastEntry, "note");
            if (note instanceof Map) return (Map<String, Object>) note;
        }
        Object phoneAttempt = deepGet(root, "noteData", "data", "noteData");
        if (phoneAttempt instanceof Map) return (Map<String, Object>) phoneAttempt;
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object deepGet(Object current, String... keys) {
        for (String key : keys) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private XhsNote mapToNote(Map<String, Object> note) {
        XhsNote n = new XhsNote();
        n.setNoteId(asString(note.get("noteId")));
        if (n.getNoteId() != null) {
            n.setUrl("https://www.xiaohongshu.com/explore/" + n.getNoteId());
        }
        String type = asString(note.get("type"));
        List<Object> imageList = asList(note.get("imageList"));
        n.setType(classify(type, imageList));
        n.setTitle(asString(note.get("title")));
        n.setContent(asString(note.get("desc")));

        List<Object> tagList = asList(note.get("tagList"));
        List<String> tags = new ArrayList<>();
        for (Object t : tagList) {
            if (t instanceof Map<?, ?> m) {
                Object name = m.get("name");
                if (name != null) tags.add(name.toString());
            }
        }
        n.setTags(tags);

        List<String> images = new ArrayList<>();
        for (Object item : imageList) {
            if (item instanceof Map<?, ?> m) {
                Object u = m.get("urlDefault");
                if (u == null) u = m.get("url");
                if (u != null) images.add(u.toString());
            }
        }
        n.setImageUrls(images);

        Object video = note.get("video");
        if (video instanceof Map<?, ?> v) {
            Object media = v.get("media");
            if (media instanceof Map<?, ?> mm) {
                Object stream = mm.get("stream");
                if (stream instanceof Map<?, ?> sm) {
                    for (Object streamList : sm.values()) {
                        if (streamList instanceof List<?> sl && !sl.isEmpty()) {
                            Object first = sl.get(0);
                            if (first instanceof Map<?, ?> fm) {
                                Object masterUrl = fm.get("masterUrl");
                                if (masterUrl != null) {
                                    n.setVideoUrl(masterUrl.toString());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        Object time = note.get("time");
        if (time instanceof Number) {
            long ms = ((Number) time).longValue();
            n.setPublishTime(TIME_FORMAT.format(new Date(ms)));
        }

        Map<String, Object> user = asMap(note.get("user"));
        if (user != null) {
            String nickname = asString(user.get("nickname"));
            if (nickname == null) nickname = asString(user.get("nickName"));
            n.setAuthorName(nickname);
            n.setAuthorId(asString(user.get("userId")));
        }

        Map<String, Object> interact = asMap(note.get("interactInfo"));
        if (interact != null) {
            n.setLikedCount(asString(interact.get("likedCount")));
            n.setCollectedCount(asString(interact.get("collectedCount")));
            n.setCommentCount(asString(interact.get("commentCount")));
            n.setShareCount(asString(interact.get("shareCount")));
        }

        return n;
    }

    private String classify(String type, List<Object> imageList) {
        if (!"video".equals(type) && !"normal".equals(type)) return "未知";
        if (imageList.isEmpty()) return "未知";
        if ("video".equals(type)) return imageList.size() == 1 ? "视频" : "图集";
        return "图文";
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object v) {
        if (v instanceof List<?>) return (List<Object>) v;
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?>) return (Map<String, Object>) v;
        return null;
    }
}
