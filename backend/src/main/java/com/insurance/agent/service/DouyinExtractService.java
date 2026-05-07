package com.insurance.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.DouyinNote;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音作品信息提取。支持两种输入：
 *   1. 纯链接（www.douyin.com 或 v.douyin.com 短链）
 *   2. 从抖音 App 复制的完整分享文本（含文案 + 话题 + 短链）
 *
 * 提取策略（按优先级）：
 *   A. 解析分享文本中的文案和话题（无需网络）
 *   B. 用 iesdouyin JSON API 获取结构化数据和互动数字
 *   C. HTML 页面解析（RENDER_DATA）+ cookie 预热
 */
@Service
public class DouyinExtractService {
    private static final Logger log = LoggerFactory.getLogger(DouyinExtractService.class);

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 匹配抖音分享文本里的"噪音"前缀，如 "5.69 k@p.Du 06/23 III:/"
    private static final Pattern SHARE_NOISE_PREFIX =
            Pattern.compile("^[\\d.,]+\\s*[kKwW]?@\\S+\\s+[\\d/]+[^\\n]*?(?=\\S{2,}\\s|\\S{3,}$|\\n|$)");

    private final ObjectMapper mapper = new ObjectMapper();
    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(cookieManager)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final DouyinSettings douyinSettings;

    public DouyinExtractService(DouyinSettings douyinSettings) {
        this.douyinSettings = douyinSettings;
    }

    // ─── 入口 ─────────────────────────────────────────────────────────────────

    public DouyinNote extract(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("请粘贴抖音作品链接或完整分享文本");
        }

        // 先验证链接格式
        String rawUrl = findDouyinUrl(input);
        if (rawUrl != null && !isValidDouyinWorkUrl(rawUrl)) {
            throw new IllegalArgumentException("""
                链接格式不正确！请使用以下格式之一：
                1. 作品页：https://www.douyin.com/video/xxxxxxxxxx
                2. 短链接：https://v.douyin.com/xxxxx
                3. 完整分享文本（从抖音 App 复制）
                
                你当前输入的似乎是搜索页或其他非作品页面链接。
                """);
        }

        // 策略 A：直接解析分享文本（最可靠，无需网络）
        DouyinNote shareNote = parseShareText(input);
        if (shareNote != null) {
            log.info("[Douyin] 从分享文本提取成功，title='{}'", shareNote.getTitle());
        }

        // 从输入中找到链接，解析 aweme_id
        String awemeId = rawUrl != null ? resolveAwemeId(rawUrl) : null;

        // 填充基础 ID 字段
        DouyinNote result = shareNote != null ? shareNote : new DouyinNote();
        if (awemeId != null) {
            result.setAwemeId(awemeId);
            result.setWorkUrl("https://www.douyin.com/video/" + awemeId);
        }

        // 策略 B：Douyin Web API + X-Bogus 签名（最准确，可获取视频直链）
        if (awemeId != null && isBlank(result.getVideoDownloadUrl())) {
            try {
                DouyinNote apiNote = extractViaWebApi(awemeId);
                if (apiNote != null) {
                    if (isBlank(result.getTitle()) && !isBlank(apiNote.getTitle()))
                        result.setTitle(apiNote.getTitle());
                    if (!isBlank(apiNote.getVideoDownloadUrl()))
                        result.setVideoDownloadUrl(apiNote.getVideoDownloadUrl());
                    if (result.getTopics() == null && apiNote.getTopics() != null)
                        result.setTopics(apiNote.getTopics());
                    if (isBlank(result.getNickname())) result.setNickname(apiNote.getNickname());
                    if (isBlank(result.getLikeCount())) result.setLikeCount(apiNote.getLikeCount());
                    if (isBlank(result.getCommentCount())) result.setCommentCount(apiNote.getCommentCount());
                    if (isBlank(result.getCollectCount())) result.setCollectCount(apiNote.getCollectCount());
                    log.info("[Douyin] Web API 提取成功，videoUrl={}", result.getVideoDownloadUrl() != null ? "有" : "无");
                }
            } catch (Exception e) {
                log.warn("[Douyin] Web API 提取失败: {}", e.getMessage());
            }
        }

        // 策略 C：HTML 页面解析——两种情况下尝试：
        //   1. 还没有视频标题（文案来源失败）
        //   2. 已有标题但还没有视频直链（需要给 Ark API 做视频理解）
        if (awemeId != null && (isBlank(result.getTitle()) || isBlank(result.getVideoDownloadUrl()))) {
            try {
                warmUpCookies();
                String html = fetchHtml("https://www.douyin.com/video/" + awemeId);
                DouyinNote htmlNote = parseRenderData(html, awemeId);
                if (htmlNote != null) {
                    if (isBlank(result.getTitle()) && !isBlank(htmlNote.getTitle())) result.setTitle(htmlNote.getTitle());
                    if (isBlank(result.getVideoDownloadUrl()) && !isBlank(htmlNote.getVideoDownloadUrl()))
                        result.setVideoDownloadUrl(htmlNote.getVideoDownloadUrl());
                    if (result.getTopics() == null && htmlNote.getTopics() != null) result.setTopics(htmlNote.getTopics());
                    if (isBlank(result.getNickname())) result.setNickname(htmlNote.getNickname());
                    log.info("[Douyin] HTML 解析成功，videoUrl={}", result.getVideoDownloadUrl() != null ? "有" : "无");
                }
            } catch (Exception e) {
                log.warn("[Douyin] HTML 解析失败: {}", e.getMessage());
            }
        }

        // 策略 D：若仍无视频直链，尝试 iesdouyin play 重定向接口（302 → 真实 CDN URL）
        if (awemeId != null && isBlank(result.getVideoDownloadUrl())) {
            String cdnUrl = tryIesdouyinPlayUrl(awemeId);
            if (cdnUrl != null) result.setVideoDownloadUrl(cdnUrl);
        }

        // 策略 E：yt-dlp 兜底（若已安装，可以突破 Douyin 反爬）
        if (awemeId != null && isBlank(result.getVideoDownloadUrl())) {
            String ytUrl = tryYtDlp("https://www.douyin.com/video/" + awemeId);
            if (ytUrl != null) result.setVideoDownloadUrl(ytUrl);
        }

        if (!isBlank(result.getTitle())) return result;

        throw new RuntimeException(
                "提取失败。建议：直接从抖音 App 点「分享→复制链接」，然后把弹出的完整文字（含文案和短链）一起粘贴进来，识别率最高。");
    }

    // ─── 策略 A：解析分享文本 ────────────────────────────────────────────────

    /**
     * 解析抖音 App 分享文字，格式示例：
     * "5.69 k@p.Du 06/23 III:/ 回复 @xxx的评论 比较重要的三款保险... # 内容过于真实 # 每日分享 https://v.douyin.com/xxx/ 复制此链接..."
     */
    private DouyinNote parseShareText(String input) {
        // 必须包含抖音域名才尝试
        if (!input.contains("douyin.com")) return null;

        // 去除用户误带的字段标签前缀（可能有多层，如 "抖音链接 / 分享文本: 抖音链接 / 分享文本: ..."）
        // 循环剥掉每一层"非数字/非#字符 + 冒号 + 空白"前缀，直到不再匹配
        String stripped;
        do {
            stripped = input;
            input = input.replaceFirst("^[^#\\d\\n]{2,60}[：:]\\s*", "");
        } while (!input.equals(stripped));

        // 找到 URL 的起始位置（作为文案结束的边界）
        Matcher urlMatcher = Pattern.compile("https://\\S*douyin\\S*").matcher(input);
        int urlStart = urlMatcher.find() ? urlMatcher.start() : input.length();

        // 提取话题标签（从全文）
        List<String> topics = new ArrayList<>();
        Matcher tagMatcher = Pattern.compile("#\\s*([^#\\s，。！？、\\r\\n]{1,30})").matcher(input);
        int firstTagPos = Integer.MAX_VALUE;
        while (tagMatcher.find()) {
            String tag = tagMatcher.group(1).trim();
            if (!tag.isBlank()) {
                topics.add(tag);
                firstTagPos = Math.min(firstTagPos, tagMatcher.start());
            }
        }

        // 文案取"第一个话题标签之前"和"URL 之前"的较小值
        int descEnd = Math.min(firstTagPos < Integer.MAX_VALUE ? firstTagPos : urlStart, urlStart);
        String rawDesc = input.substring(0, descEnd).trim();

        // 去除分享文本特有噪声：
        // 1. "5.69 k@p.Du 06/23 III:/" 或 "5.69 k 06/23 III:/"（@xxx 可选）
        rawDesc = rawDesc.replaceAll(
                "^[\\d.,]+\\s*[kKwWmM]?(?:@\\S+)?(?:\\s+[\\d/]+){0,2}[^\\n\\u4e00-\\u9fa5]*", "");
        // 2. "回复 @xxx的评论"
        rawDesc = rawDesc.replaceAll("回复\\s+@\\S+的评论\\s*", "");
        // 3. 剩余 @mention
        rawDesc = rawDesc.replaceAll("@\\S+", "");
        // 4. 多余空白
        rawDesc = rawDesc.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();

        if (rawDesc.isBlank()) return null;

        DouyinNote note = new DouyinNote();
        note.setTitle(rawDesc);
        if (!topics.isEmpty()) note.setTopics(topics);
        note.setWorkType("视频");
        return note;
    }

    // ─── 策略 B：Douyin Web API + X-Bogus 签名 ──────────────────────────────

    /**
     * 调用 https://www.douyin.com/aweme/v1/web/aweme/detail/ (附 X-Bogus 签名)。
     * 这是 Python 库 DLWangSan/douyin_parse 同款思路，签名算法已移植至 XBogus.java。
     */
    @SuppressWarnings("unchecked")
    private DouyinNote extractViaWebApi(String awemeId) throws Exception {
        // 构造参数（顺序与 Python 代码一致，签名依赖参数顺序）
        String params =
                "device_platform=webapp" +
                "&aid=6383" +
                "&channel=channel_pc_web" +
                "&aweme_id=" + awemeId +
                "&pc_client_type=1" +
                "&version_code=290100" +
                "&version_name=29.1.0" +
                "&cookie_enabled=true" +
                "&browser_language=zh-CN" +
                "&browser_platform=Win32" +
                "&browser_name=Chrome" +
                "&browser_version=130.0.0.0" +
                "&browser_online=true" +
                "&engine_name=Blink" +
                "&engine_version=130.0.0.0" +
                "&os_name=Windows" +
                "&os_version=10" +
                "&platform=PC" +
                "&msToken=";

        // 预热：访问首页拿 ttwid 等基础 cookie（HttpClient 会自动带上）
        warmUpCookies();

        String xbogus = new XBogus(UA).generate(params);
        String url = "https://www.douyin.com/aweme/v1/web/aweme/detail/?" + params + "&X-Bogus=" + xbogus;
        log.debug("[Douyin] Web API URL (前80字)={}", url.substring(0, Math.min(80, url.length())));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", UA)
                .header("Referer", "https://www.douyin.com/video/" + awemeId)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cookie", buildCookieHeader())
                .GET()
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        String rawBody = resp.body();
        log.debug("[Douyin] Web API HTTP={} body_len={} body_preview={}",
                resp.statusCode(), rawBody == null ? 0 : rawBody.length(),
                rawBody == null ? "null" : rawBody.substring(0, Math.min(200, rawBody.length())));

        if (resp.statusCode() != 200) {
            throw new RuntimeException("Web API HTTP " + resp.statusCode() + ": " +
                    (rawBody != null && rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody));
        }
        if (rawBody == null || rawBody.isBlank()) {
            throw new RuntimeException("Web API 返回空 body（X-Bogus 签名可能仍有误）");
        }

        Map<String, Object> body = mapper.readValue(rawBody, new TypeReference<>() {});

        // 打印 status_code 便于排查
        Object statusCode = body.get("status_code");
        if (statusCode instanceof Number && ((Number) statusCode).intValue() != 0) {
            log.warn("[Douyin] Web API status_code={} msg={}", statusCode, body.get("status_msg"));
        }

        Object awemeDetailObj = body.get("aweme_detail");
        if (!(awemeDetailObj instanceof Map)) {
            throw new RuntimeException("aweme_detail 不存在，status_code=" + statusCode);
        }
        Map<String, Object> awemeDetail = (Map<String, Object>) awemeDetailObj;
        return buildFromWebApiDetail(awemeDetail, awemeId);
    }

    @SuppressWarnings("unchecked")
    private DouyinNote buildFromWebApiDetail(Map<String, Object> awemeDetail, String awemeId) {
        DouyinNote note = new DouyinNote();
        note.setAwemeId(awemeId);
        note.setWorkUrl("https://www.douyin.com/video/" + awemeId);
        note.setTitle(str(awemeDetail.get("desc")));

        Object awemeType = awemeDetail.get("aweme_type");
        if (awemeType instanceof Number)
            note.setWorkType(((Number) awemeType).intValue() == 68 ? "图集" : "视频");

        // 互动数据
        Object stats = awemeDetail.get("statistics");
        if (stats instanceof Map) {
            Map<String, Object> s = (Map<String, Object>) stats;
            note.setLikeCount(fmtCount(s.get("digg_count")));
            note.setCommentCount(fmtCount(s.get("comment_count")));
            note.setCollectCount(fmtCount(s.get("collect_count")));
            note.setShareCount(fmtCount(s.get("share_count")));
        }

        // 作者信息
        Object author = awemeDetail.get("author");
        if (author instanceof Map) {
            Map<String, Object> a = (Map<String, Object>) author;
            note.setNickname(str(a.get("nickname")));
            note.setUserId(str(a.get("unique_id")));
            note.setUserDesc(str(a.get("signature")));
            note.setFollowerCount(fmtCount(a.get("follower_count")));
        }

        // 话题标签
        Object textExtra = awemeDetail.get("text_extra");
        if (textExtra instanceof List) {
            List<String> topics = new ArrayList<>();
            for (Object t : (List<?>) textExtra) {
                if (t instanceof Map) {
                    Object name = ((Map<?, ?>) t).get("hashtag_name");
                    if (name != null && !name.toString().isBlank()) topics.add(name.toString());
                }
            }
            if (!topics.isEmpty()) note.setTopics(topics);
        }

        Object ct = awemeDetail.get("create_time");
        if (ct instanceof Number)
            note.setPublishTime(TIME_FMT.format(new Date(((Number) ct).longValue() * 1000)));

        // 视频直链提取（优先 bit_rate 列表，其次 play_addr）
        extractVideoUrlFromWebApi(note, awemeDetail.get("video"));
        return note;
    }

    @SuppressWarnings("unchecked")
    private void extractVideoUrlFromWebApi(DouyinNote note, Object videoObj) {
        if (!(videoObj instanceof Map)) return;
        Map<String, Object> video = (Map<String, Object>) videoObj;

        // 方法1：bit_rate 列表（最完整，可选画质）
        Object bitRateList = video.get("bit_rate");
        if (bitRateList instanceof List) {
            for (Object br : (List<?>) bitRateList) {
                if (br instanceof Map) {
                    Object pa = ((Map<?, ?>) br).get("play_addr");
                    if (pa instanceof Map) {
                        Object ul = ((Map<?, ?>) pa).get("url_list");
                        if (ul instanceof List && !((List<?>) ul).isEmpty()) {
                            String u = str(((List<?>) ul).get(0));
                            if (!isBlank(u)) {
                                note.setVideoDownloadUrl(u.replace("playwm", "play"));
                                return;
                            }
                        }
                    }
                }
            }
        }

        // 方法2：play_addr 直接拿
        for (String addrKey : new String[]{"play_addr", "download_addr"}) {
            Object addr = video.get(addrKey);
            if (addr instanceof Map) {
                Object urlList = ((Map<?, ?>) addr).get("url_list");
                if (urlList instanceof List && !((List<?>) urlList).isEmpty()) {
                    String u = str(((List<?>) urlList).get(0));
                    if (!isBlank(u)) {
                        note.setVideoDownloadUrl(u.replace("playwm", "play"));
                        return;
                    }
                }
            }
        }
    }

    // ─── 策略 C：HTML 页面解析 ───────────────────────────────────────────────

    private void warmUpCookies() {
        try {
            boolean hasTtwid = cookieManager.getCookieStore().getCookies()
                    .stream().anyMatch(c -> "ttwid".equals(c.getName()));
            if (hasTtwid) return;
            http.send(HttpRequest.newBuilder().uri(URI.create("https://www.douyin.com/"))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", UA).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            log.info("[Douyin] Cookie 预热完成");
        } catch (Exception e) {
            log.warn("[Douyin] Cookie 预热失败: {}", e.getMessage());
        }
    }

    private String fetchHtml(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://www.douyin.com/")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    }

    @SuppressWarnings("unchecked")
    private DouyinNote parseRenderData(String html, String awemeId) {
        Document doc = Jsoup.parse(html);
        for (String sel : new String[]{"script#RENDER_DATA", "script#__NEXT_DATA__"}) {
            Element el = doc.selectFirst(sel);
            if (el == null) continue;
            try {
                String raw = el.html();
                String decoded = sel.contains("RENDER_DATA")
                        ? URLDecoder.decode(raw, StandardCharsets.UTF_8) : raw;
                Map<String, Object> data = mapper.readValue(decoded, new TypeReference<>() {});
                Map<String, Object> detail = findAwemeDetail(data);
                if (detail == null) continue;
                DouyinNote note = new DouyinNote();
                note.setAwemeId(awemeId);
                note.setWorkUrl("https://www.douyin.com/video/" + awemeId);
                fillFromDetail(note, detail);
                if (!isBlank(note.getTitle())) return note;
            } catch (Exception e) {
                log.warn("[Douyin] {} 解析失败: {}", sel, e.getMessage());
            }
        }
        // OG tags fallback
        String ogTitle = ogContent(doc, "og:title");
        if (!isBlank(ogTitle)) {
            DouyinNote note = new DouyinNote();
            note.setAwemeId(awemeId);
            note.setWorkUrl("https://www.douyin.com/video/" + awemeId);
            note.setTitle(ogTitle);
            note.setUserDesc(ogContent(doc, "og:description"));
            return note;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findAwemeDetail(Map<String, Object> root) {
        String[][] paths = {
            {"app", "initialState", "aweme", "detailInfo", "awemeDetail"},
            {"app", "initialState", "videoDetail", "awemeDetail"},
            {"props", "pageProps", "awemeDetail"},
        };
        for (String[] path : paths) {
            Object found = deepGet(root, path);
            if (found instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) found;
                if (m.containsKey("desc") || m.containsKey("statistics")) return m;
            }
        }
        return deepSearch(root, "desc", "statistics");
    }

    @SuppressWarnings("unchecked")
    private void fillFromDetail(DouyinNote note, Map<String, Object> d) {
        note.setTitle(str(d.get("desc")));
        Object t = d.get("awemeType");
        if (t instanceof Number) note.setWorkType(((Number) t).intValue() == 68 ? "图集" : "视频");
        Object stats = d.get("statistics");
        if (stats instanceof Map) {
            Map<String, Object> s = (Map<String, Object>) stats;
            note.setLikeCount(fmtCount(s.get("diggCount")));
            note.setCommentCount(fmtCount(s.get("commentCount")));
            note.setCollectCount(fmtCount(s.get("collectCount")));
            note.setShareCount(fmtCount(s.get("shareCount")));
        }
        Object author = d.get("author");
        if (author instanceof Map) {
            Map<String, Object> a = (Map<String, Object>) author;
            note.setNickname(str(a.get("nickname")));
            note.setUserId(str(a.get("uniqueId")));
            note.setUserDesc(str(a.get("signature")));
            note.setFollowerCount(fmtCount(a.get("followerCount")));
        }
        Object textExtra = d.get("textExtra");
        if (textExtra instanceof List) {
            List<String> topics = new ArrayList<>();
            for (Object item : (List<?>) textExtra) {
                if (item instanceof Map) {
                    Object name = ((Map<?, ?>) item).get("hashtagName");
                    if (name != null && !name.toString().isBlank()) topics.add(name.toString());
                }
            }
            if (!topics.isEmpty()) note.setTopics(topics);
        }
        Object ct = d.get("createTime");
        if (ct instanceof Number)
            note.setPublishTime(TIME_FMT.format(new Date(((Number) ct).longValue() * 1000)));

        // 从 RENDER_DATA 里提取视频直链（供 Ark API 视频理解使用）
        if (isBlank(note.getVideoDownloadUrl())) {
            extractVideoUrl(note, d.get("video"));
        }
    }

    @SuppressWarnings("unchecked")
    private void extractVideoUrl(DouyinNote note, Object videoObj) {
        if (!(videoObj instanceof Map)) return;
        Map<String, Object> video = (Map<String, Object>) videoObj;
        // RENDER_DATA 结构：video.playAddr → [{src:"url"}] 或 video.playAddr.urlList
        for (String key : new String[]{"playAddr", "play_addr", "downloadAddr", "download_addr"}) {
            Object addr = video.get(key);
            if (addr instanceof List) {
                for (Object item : (List<?>) addr) {
                    if (item instanceof Map) {
                        String src = str(((Map<?, ?>) item).get("src"));
                        if (!isBlank(src)) { note.setVideoDownloadUrl(src); return; }
                    }
                }
            } else if (addr instanceof Map) {
                Object urlList = ((Map<String, Object>) addr).get("url_list");
                if (urlList instanceof List && !((List<?>) urlList).isEmpty()) {
                    String u = str(((List<?>) urlList).get(0));
                    if (!isBlank(u)) { note.setVideoDownloadUrl(u); return; }
                }
            }
        }
        // bitrateInfo[0].PlayAddr.UrlList[0]
        Object bitrateInfo = video.get("bitrateInfo");
        if (bitrateInfo instanceof List && !((List<?>) bitrateInfo).isEmpty()) {
            Object first = ((List<?>) bitrateInfo).get(0);
            if (first instanceof Map) {
                Object pa = ((Map<?, ?>) first).get("PlayAddr");
                if (pa instanceof Map) {
                    Object ul = ((Map<?, ?>) pa).get("UrlList");
                    if (ul instanceof List && !((List<?>) ul).isEmpty()) {
                        String u = str(((List<?>) ul).get(0));
                        if (!isBlank(u)) { note.setVideoDownloadUrl(u); return; }
                    }
                }
            }
        }
    }

    // ─── URL / aweme_id 解析 ─────────────────────────────────────────────────

    private String findDouyinUrl(String input) {
        Matcher m = Pattern.compile("https://\\S*douyin[^\\s）】》）\"']+").matcher(input);
        return m.find() ? m.group().replaceAll("[）】》》\"']+$", "") : null;
    }

    private String resolveAwemeId(String url) {
        String id = extractAwemeId(url);
        if (id != null) return id;
        // 短链：跟随重定向
        if (url.contains("v.douyin.com") || url.contains("iesdouyin.com")) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("User-Agent", UA).GET().build();
                HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                id = extractAwemeId(resp.uri().toString());
                if (id != null) return id;
            } catch (Exception e) {
                log.warn("[Douyin] 短链解析失败: {}", e.getMessage());
            }
        }
        return null;
    }

    private boolean isValidDouyinWorkUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        // 有效的链接格式
        return lower.contains("/video/") || 
               lower.contains("/note/") || 
               lower.contains("v.douyin.com") ||
               lower.contains("iesdouyin.com");
    }

    private String extractAwemeId(String url) {
        Matcher m;
        m = Pattern.compile("/video/(\\d{15,19})").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/note/(\\d{15,19})").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("[?&]modal_id=(\\d{15,19})").matcher(url);
        if (m.find()) return m.group(1);
        m = Pattern.compile("item_ids=(\\d{15,19})").matcher(url);
        if (m.find()) return m.group(1);
        return null;
    }

    // ─── 工具 ────────────────────────────────────────────────────────────────

    private void mergeStats(DouyinNote target, DouyinNote source) {
        if (!isBlank(source.getLikeCount())) target.setLikeCount(source.getLikeCount());
        if (!isBlank(source.getCommentCount())) target.setCommentCount(source.getCommentCount());
        if (!isBlank(source.getCollectCount())) target.setCollectCount(source.getCollectCount());
        if (!isBlank(source.getShareCount())) target.setShareCount(source.getShareCount());
        if (!isBlank(source.getFollowerCount())) target.setFollowerCount(source.getFollowerCount());
        if (target.getTopics() == null && source.getTopics() != null) target.setTopics(source.getTopics());
        if (isBlank(target.getPublishTime())) target.setPublishTime(source.getPublishTime());
    }

    @SuppressWarnings("unchecked")
    private Object deepGet(Object obj, String... keys) {
        for (String key : keys) {
            if (!(obj instanceof Map)) return null;
            obj = ((Map<String, Object>) obj).get(key);
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepSearch(Map<String, Object> map, String... required) {
        if (Arrays.stream(required).allMatch(map::containsKey)) return map;
        for (Object v : map.values()) {
            if (v instanceof Map) {
                Map<String, Object> found = deepSearch((Map<String, Object>) v, required);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String ogContent(Document doc, String property) {
        Element el = doc.selectFirst("meta[property=" + property + "]");
        return el != null ? el.attr("content") : null;
    }

    /**
     * 拼 Cookie 请求头。优先使用用户手动配置的 cookie（包含 ttwid/msToken 等会话凭证），
     * 再追加 CookieManager 从首页自动获取的 cookie。手动 cookie 优先以避免被覆盖。
     */
    private String buildCookieHeader() {
        // 用户手动配置的 cookie（高优先级）
        String manual = douyinSettings.getCookie();

        // CookieManager 自动采集的 cookie（低优先级，补充 ttwid 等）
        StringBuilder sb = new StringBuilder();
        for (java.net.HttpCookie c : cookieManager.getCookieStore().getCookies()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getName()).append("=").append(c.getValue());
        }

        if (manual != null && !manual.isBlank()) {
            // 手动 cookie 放前面，auto cookie 补充在后
            if (sb.length() > 0) {
                return manual + "; " + sb;
            }
            return manual;
        }
        return sb.toString();
    }

    // ─── 策略 D：iesdouyin play 重定向 ──────────────────────────────────────
    /**
     * GET https://www.iesdouyin.com/aweme/v1/play/?video_id={id}&ratio=720p&line=0
     * 该接口会 302 跳转到真实 CDN 直链，不需要登录，适合公开视频。
     */
    private String tryIesdouyinPlayUrl(String awemeId) {
        String playUrl = "https://www.iesdouyin.com/aweme/v1/play/?video_id=" + awemeId
                + "&ratio=720p&line=0&media_type=4&vr_type=0";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(playUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "com.ss.android.ugc.aweme/230501 (Android 11)")
                    .header("Referer", "https://www.iesdouyin.com/")
                    .GET().build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            String finalUrl = resp.uri().toString();
            // 跳转后 URL 包含 CDN 域名
            if (!finalUrl.equals(playUrl)
                    && (finalUrl.contains("douyinvod.com") || finalUrl.contains("snssdk.com")
                        || finalUrl.contains("bytecdn.cn") || finalUrl.endsWith(".mp4")
                        || finalUrl.contains(".mp4?"))) {
                log.info("[Douyin] iesdouyin play 重定向成功，CDN URL 已获取");
                return finalUrl;
            }
            log.debug("[Douyin] iesdouyin play 重定向目标非 CDN: {}", finalUrl.substring(0, Math.min(80, finalUrl.length())));
        } catch (Exception e) {
            log.debug("[Douyin] iesdouyin play 重定向失败: {}", e.getMessage());
        }
        return null;
    }

    // ─── 策略 E：yt-dlp 命令行兜底 ──────────────────────────────────────────
    /**
     * 若服务器安装了 yt-dlp，调用命令行提取视频直链，成功率最高。
     * 未安装时静默跳过。
     */
    private String tryYtDlp(String workUrl) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp", "--get-url",
                    "-f", "best[ext=mp4]/best[vcodec!=none]/best",
                    "--no-playlist", "--quiet", "--no-warnings",
                    workUrl);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String line = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream())).readLine();
            boolean done = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }
            if (line != null && line.startsWith("http")) {
                log.info("[Douyin] yt-dlp 提取 CDN URL 成功");
                return line.trim();
            }
        } catch (Exception e) {
            log.debug("[Douyin] yt-dlp 不可用: {}", e.getMessage());
        }
        return null;
    }

    private String str(Object v) { return v == null ? null : v.toString().trim(); }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String fmtCount(Object v) {
        if (v == null) return null;
        long n = ((Number) v).longValue();
        if (n >= 10000) return String.format("%.1fw", n / 10000.0);
        return String.valueOf(n);
    }
}
