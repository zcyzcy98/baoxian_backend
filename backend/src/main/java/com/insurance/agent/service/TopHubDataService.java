package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.TopicCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TopHubDataService {
    private static final Logger log = LoggerFactory.getLogger(TopHubDataService.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public TopHubDataService(HttpClient sharedHttpClient) {
        this.http = sharedHttpClient;
    }

    @Value("${tophubdata.api.key:}")
    private String apiKey;

    @Value("${tophubdata.api.base-url:https://api.tophubdata.com}")
    private String baseUrl;

    @Value("${tophubdata.api.timeout-seconds:15}")
    private int timeoutSeconds;

    private List<NodeInfo> nodesCache = null;
    private long nodesCacheTs = 0;
    private static final long NODES_CACHE_TTL = 3600_000; // 1 hour

    private static final Pattern HEAT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(万)?"
    );

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private static final List<String> INSURANCE_KEYWORDS = Arrays.asList(
            "保险", "医保", "社保", "医疗", "健康", "养老", "重疾", "理赔",
            "理财", "经济", "政策", "新规", "孩子", "家庭", "风险", "意外",
            "救命", "住院", "手术", "癌症", "肿瘤", "慢性病", "高血压", "糖尿病",
            "退休", "养老金", "公积金", "失业", "工伤", "生育", "补贴",
            "利率", "降息", "加息", "GDP", "CPI", "消费", "投资",
            "疫苗", "药品", "医院", "看病", "挂号", "体检", "养生",
            "房产", "房价", "房贷", "落户", "教育", "学区", "培训",
            "维权", "投诉", "赔偿", "纠纷", "官司",
            "裁员", "失业", "降薪", "延迟退休", "老龄化", "生育率", "DRG", "惠民保"
    );

    // 明确无关的噪音关键词：命中任意一个则直接过滤（AI 不再二次处理）
    private static final List<String> NOISE_KEYWORDS = Arrays.asList(
            // 娱乐明星
            "出轨", "离婚", "结婚", "颜值", "综艺", "演唱会", "爱豆", "饭圈",
            "选手", "流量明星", "恋情", "分手", "绯闻", "人设", "塌房",
            // 纯游戏
            "英雄联盟", "王者荣耀", "原神", "游戏更新", "赛季", "段位",
            // 纯竞技体育（与健康无关的）
            "夺冠", "进球", "世界杯", "奥运金牌", "赛程", "球队",
            // 明显低价值娱乐
            "八卦", "吃瓜", "路透", "粉丝"
    );

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<TopicCandidate> searchByKeyword(String keyword, int limit, String hashid) {
        if (!isConfigured()) {
            log.warn("TopHubData API key not configured, skip search");
            return List.of();
        }
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            String encoded = java.net.URLEncoder.encode(keyword.trim(), "UTF-8");
            String url = baseUrl + "/search?q=" + encoded + "&p=1";
            if (hashid != null && !hashid.isBlank()) {
                url += "&hashid=" + hashid;
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("TopHubData search API returned status {}", resp.statusCode());
                return List.of();
            }

            JsonNode root = mapper.readTree(resp.body());
            if (!root.path("error").isMissingNode() && root.path("error").asBoolean()) {
                log.warn("TopHubData search API returned error: {}", root);
                return List.of();
            }

            JsonNode data = root.path("data");
            if (data.isNull() || data.isMissingNode()) return List.of();

            JsonNode items = data.path("items");
            if (!items.isArray()) return List.of();

            List<TopicCandidate> candidates = new ArrayList<>();
            long maxHeat = 0;

            for (JsonNode item : items) {
                String extra = item.path("extra").asText("");
                long heat = parseHeat(extra);
                if (heat > maxHeat) maxHeat = heat;
            }

            for (JsonNode item : items) {
                TopicCandidate c = convertSearchItem(item, maxHeat, keyword);
                if (c != null) {
                    candidates.add(c);
                }
            }

            candidates.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

            if (candidates.size() > limit) {
                candidates = candidates.subList(0, limit);
            }

            return candidates;
        } catch (Exception e) {
            log.error("Failed to search hot topics from TopHubData", e);
            return List.of();
        }
    }

    public List<NodeInfo> fetchNodes() {
        if (!isConfigured()) return List.of();

        long now = System.currentTimeMillis();
        if (nodesCache != null && (now - nodesCacheTs) < NODES_CACHE_TTL) {
            return nodesCache;
        }

        try {
            String url = baseUrl + "/nodes?p=1";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();

            JsonNode root = mapper.readTree(resp.body());
            if (root.path("error").asBoolean()) return List.of();
            JsonNode data = root.path("data");
            if (!data.isArray()) return List.of();

            List<NodeInfo> nodes = new ArrayList<>();
            for (JsonNode n : data) {
                String hashid = n.path("hashid").asText("");
                String name = n.path("name").asText("");
                String display = n.path("display").asText("");
                String domain = n.path("domain").asText("");
                String cid = n.path("cid").asText("");
                if (!hashid.isBlank()) {
                    nodes.add(new NodeInfo(hashid, name, display, domain, cid));
                }
            }

            nodesCache = nodes;
            nodesCacheTs = now;
            return nodes;
        } catch (Exception e) {
            log.error("Failed to fetch nodes from TopHubData", e);
            return List.of();
        }
    }

    public List<TopicCandidate> fetchHotTopics(int limit) {
        return fetchHotTopics(limit, null);
    }

    public List<TopicCandidate> fetchHotTopics(int limit, List<String> hashids) {
        if (!isConfigured()) {
            log.warn("TopHubData API key not configured, skip fetching hot topics");
            return List.of();
        }

        if (hashids == null || hashids.isEmpty()) {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = baseUrl + "/hot?date=" + today;
            List<TopicCandidate> result = fetchHotTopicsFromUrl(limit, url);
            log.info("[TopHub] 全网热点返回 {} 条", result.size());
            return result;
        }

        List<TopicCandidate> all = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        int totalFromApi = 0;

        for (String hashid : hashids) {
            try {
                String url = baseUrl + "/nodes/" + hashid;
                List<TopicCandidate> fromSource = fetchSingleNode(limit, url, hashid);
                totalFromApi += fromSource.size();
                int added = 0;
                for (TopicCandidate c : fromSource) {
                    if (c.getTitle() != null && seenTitles.add(c.getTitle())) {
                        all.add(c);
                        added++;
                    }
                }
                log.info("[TopHub] hashid={} 返回{}条 新增{}条", hashid, fromSource.size(), added);
            } catch (Exception e) {
                log.error("来源 hashid={} 请求失败", hashid, e);
            }
        }

        all.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        log.info("[TopHub] {} 个来源共返回 {} 条，合并去重后 {} 条", hashids.size(), totalFromApi, all.size());
        return all;
    }

    private List<TopicCandidate> fetchSingleNode(int limit, String url, String hashid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("TopHubData /nodes/{} API returned status {}", hashid, resp.statusCode());
                return List.of();
            }

            JsonNode root = mapper.readTree(resp.body());
            if (!root.path("error").isMissingNode() && root.path("error").asBoolean()) {
                log.warn("TopHubData /nodes/{} API returned error: {}", hashid, root);
                return List.of();
            }

            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                log.warn("TopHubData /nodes/{} API returned no data", hashid);
                return List.of();
            }

            String sitename = data.path("name").asText("热点");
            JsonNode items = data.path("items");
            if (!items.isArray()) {
                log.warn("TopHubData /nodes/{} API returned no items array", hashid);
                return List.of();
            }

            List<TopicCandidate> candidates = new ArrayList<>();
            long maxHeat = 0;

            for (JsonNode item : items) {
                String extra = item.path("extra").asText("");
                long heat = parseHeat(extra);
                if (heat > maxHeat) maxHeat = heat;
            }

            for (JsonNode item : items) {
                TopicCandidate c = convertNodeItem(item, sitename, maxHeat);
                if (c != null) {
                    candidates.add(c);
                }
            }

            candidates.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

            if (candidates.size() > limit) {
                candidates = candidates.subList(0, limit);
            }

            return candidates;
        } catch (Exception e) {
            log.error("Failed to fetch node {} from TopHubData: {}", hashid, url, e);
            return List.of();
        }
    }

    private TopicCandidate convertNodeItem(JsonNode item, String sitename, long maxHeat) {
        String title = item.path("title").asText("");
        if (title.isBlank()) return null;

        if (isNoise(title)) return null;

        String description = item.path("description").asText("");
        String url = item.path("url").asText("");
        String extra = item.path("extra").asText("");

        long heat = parseHeat(extra);
        int insuranceRelevance = computeInsuranceRelevance(title, description);

        TopicCandidate c = buildCandidate(title, url, sitename, description, extra, heat, maxHeat, insuranceRelevance);

        List<String> tags = new ArrayList<>();
        tags.add(sitename);
        if (insuranceRelevance > 0) tags.add("保险相关");
        c.setTags(tags);

        return c;
    }

    private List<TopicCandidate> fetchHotTopicsFromUrl(int limit, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.warn("TopHubData API returned status {}", resp.statusCode());
                return List.of();
            }

            JsonNode root = mapper.readTree(resp.body());
            if (!root.path("error").isMissingNode() && root.path("error").asBoolean()) {
                log.warn("TopHubData API returned error: {}", root);
                return List.of();
            }

            JsonNode data = root.path("data");
            if (!data.isArray()) {
                log.warn("TopHubData API returned non-array data");
                return List.of();
            }

            List<TopicCandidate> candidates = new ArrayList<>();
            long maxHeat = 0;

            for (JsonNode item : data) {
                String views = item.path("views").asText("");
                long heat = parseHeat(views);
                if (heat > maxHeat) maxHeat = heat;
            }

            for (JsonNode item : data) {
                TopicCandidate c = convertItem(item, maxHeat);
                if (c != null) {
                    candidates.add(c);
                }
            }

            candidates.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

            if (candidates.size() > limit) {
                candidates = candidates.subList(0, limit);
            }

            return candidates;
        } catch (Exception e) {
            log.error("Failed to fetch hot topics from TopHubData: {}", url, e);
            return List.of();
        }
    }

    private TopicCandidate convertItem(JsonNode item, long maxHeat) {
        String title = item.path("title").asText("");
        if (title.isBlank()) return null;

        // 第一道过滤：明确噪音关键词，直接丢弃（AI 也不需要看）
        if (isNoise(title)) return null;

        String sitename = item.path("sitename").asText("热点");
        String description = item.path("description").asText("");
        String url = item.path("url").asText("");
        String views = item.path("views").asText("");

        long heat = parseHeat(views);
        int insuranceRelevance = computeInsuranceRelevance(title, description);

        TopicCandidate c = buildCandidate(title, url, sitename, description, views, heat, maxHeat, insuranceRelevance);

        List<String> tags = new ArrayList<>();
        tags.add(sitename);
        if (insuranceRelevance > 0) tags.add("保险相关");
        c.setTags(tags);

        return c;
    }

    static boolean isNoise(String title) {
        if (title == null) return true;
        String lower = title.toLowerCase();
        for (String kw : NOISE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private TopicCandidate convertSearchItem(JsonNode item, long maxHeat, String keyword) {
        String title = item.path("title").asText("");
        if (title.isBlank()) return null;

        String description = item.path("description").asText("");
        String url = item.path("url").asText("");
        String extra = item.path("extra").asText("");

        long heat = parseHeat(extra);
        int insuranceRelevance = computeInsuranceRelevance(title, description);

        String sitename = extractSitenameFromUrl(url);
        TopicCandidate c = buildCandidate(title, url, sitename.isBlank() ? keyword : sitename,
                description, extra, heat, maxHeat, insuranceRelevance);
        c.setSourceLabel("热点搜索 · " + keyword);

        List<String> tags = new ArrayList<>();
        tags.add(keyword);
        if (!sitename.isBlank()) tags.add(sitename);
        if (insuranceRelevance > 0) {
            tags.add("保险相关");
        }
        c.setTags(tags);

        return c;
    }

    private TopicCandidate buildCandidate(String title, String url, String sitename,
                                           String description, String heatText,
                                           long heat, long maxHeat, int insuranceRelevance) {
        TopicCandidate c = new TopicCandidate();
        c.setId("tophub-" + Integer.toHexString(title.hashCode()));
        c.setSource(TopicCandidate.Source.TOPHUB);
        c.setTitle(title);
        c.setSourceUrl(url);

        String platformIcon = getPlatformIcon(sitename);
        c.setSourceLabel("今日热榜 · " + platformIcon + sitename);

        StringBuilder angle = new StringBuilder();
        if (!description.isBlank()) {
            angle.append(description);
        }
        if (!heatText.isBlank()) {
            if (!angle.isEmpty()) angle.append(" · ");
            angle.append(heatText);
        }
        c.setAngle(angle.toString());

        // 热度分：0-50，非线性（对数压缩，避免热度一家独大）
        int heatScore;
        if (maxHeat > 0 && heat > 0) {
            double ratio = Math.log1p(heat) / Math.log1p(maxHeat);
            heatScore = (int) Math.round(50.0 * ratio);
        } else {
            heatScore = 20;
        }
        heatScore = Math.min(50, Math.max(10, heatScore));

        // 保险相关性分：0-50（AI 筛选后会覆盖更准确的分数，这里先给规则分）
        int relevanceScore = Math.min(50, insuranceRelevance * 10);

        c.setScore(Math.min(100, Math.max(15, heatScore + relevanceScore)));

        c.setSuggestedAgent("xhs-title");

        return c;
    }

    static long parseHeat(String views) {
        if (views == null || views.isBlank()) return 0;
        try {
            Matcher m = HEAT_PATTERN.matcher(views);
            if (m.find()) {
                double num = Double.parseDouble(m.group(1));
                boolean hasWan = m.group(2) != null && m.group(2).equals("万");
                return hasWan ? (long) (num * 10000) : (long) num;
            }
            Matcher numM = NUMBER_PATTERN.matcher(views);
            if (numM.find()) {
                return Long.parseLong(numM.group());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    static int computeInsuranceRelevance(String title, String description) {
        String text = (title + " " + description).toLowerCase();
        int score = 0;
        for (String kw : INSURANCE_KEYWORDS) {
            if (text.contains(kw)) {
                score++;
            }
        }
        return score;
    }

    static String getPlatformIcon(String sitename) {
        if (sitename == null) return "";
        if (sitename.contains("微博")) return "🔥 ";
        if (sitename.contains("知乎")) return "💡 ";
        if (sitename.contains("微信")) return "📱 ";
        if (sitename.contains("百度")) return "🔍 ";
        if (sitename.contains("抖音")) return "🎵 ";
        if (sitename.contains("头条")) return "📰 ";
        if (sitename.contains("B站") || sitename.contains("bilibili")) return "📺 ";
        if (sitename.contains("虎扑")) return "🏀 ";
        if (sitename.contains("豆瓣")) return "📖 ";
        if (sitename.contains("GitHub")) return "💻 ";
        if (sitename.contains("贴吧")) return "💬 ";
        return "";
    }

    static String extractSitenameFromUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String host = new java.net.URL(url).getHost();
            if (host == null) return "";
            String h = host.toLowerCase();
            if (h.contains("zhihu")) return "知乎";
            if (h.contains("weibo")) return "微博";
            if (h.contains("weixin") || h.contains("qq.com")) return "微信";
            if (h.contains("baidu")) return "百度";
            if (h.contains("douyin") || h.contains("iesdouyin")) return "抖音";
            if (h.contains("bilibili")) return "B站";
            if (h.contains("tieba")) return "贴吧";
            if (h.contains("douban")) return "豆瓣";
            if (h.contains("hupu")) return "虎扑";
            if (h.contains("github")) return "GitHub";
            if (h.contains("toutiao") || h.contains("ixigua")) return "头条";
            if (h.contains("v2ex")) return "V2EX";
            if (h.contains("tianya")) return "天涯";
            if (h.contains("xiaohongshu") || h.contains("xhs")) return "小红书";
            if (h.contains("sina") || h.contains("xinlang")) return "新浪";
            if (h.contains("sohu")) return "搜狐";
            if (h.contains("163") || h.contains("netease")) return "网易";
            if (h.contains("thepaper")) return "澎湃新闻";
            if (h.contains("36kr")) return "36氪";
            if (h.contains("huxiu")) return "虎嗅";
            return host.replace("www.", "").split("\\.")[0];
        } catch (Exception e) {
            return "";
        }
    }

    public static class NodeInfo {
        private final String hashid;
        private final String name;
        private final String display;
        private final String domain;
        private final String cid;

        public NodeInfo(String hashid, String name, String display, String domain, String cid) {
            this.hashid = hashid;
            this.name = name;
            this.display = display;
            this.domain = domain;
            this.cid = cid;
        }

        public String getHashid() { return hashid; }
        public String getName() { return name; }
        public String getDisplay() { return display; }
        public String getDomain() { return domain; }
        public String getCid() { return cid; }
    }
}
