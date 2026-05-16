package com.insurance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.TopicCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 用 DeepSeek 对 TopHub 热点标题做批量语义分析，替代纯关键词匹配。
 *
 * 每次最多批处理 BATCH_SIZE 条，一次 API 调用分析全部，返回：
 *   - 是否与保险内容创作相关
 *   - 对应险种 / 目标人群
 *   - 建议发布平台
 *   - AI 相关性评分 1-5
 *   - "WHY THIS TOPIC" 一句话推荐理由
 */
@Service
public class TopicAiFilterService {
    private static final Logger log = LoggerFactory.getLogger(TopicAiFilterService.class);
    private static final int BATCH_SIZE = 40;

    private final DeepSeekService deepSeek;
    private final ObjectMapper mapper = new ObjectMapper();

    public TopicAiFilterService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    /**
     * 对候选列表做 AI 增强：相关性判断 + 字段补全。
     * 如果 DeepSeek 未配置或调用失败，返回原始列表（fallback 到规则引擎结果）。
     */
    public List<TopicCandidate> enrichWithAi(List<TopicCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return candidates;

        List<TopicCandidate> result = new ArrayList<>();
        // 分批处理
        for (int i = 0; i < candidates.size(); i += BATCH_SIZE) {
            List<TopicCandidate> batch = candidates.subList(i, Math.min(i + BATCH_SIZE, candidates.size()));
            try {
                List<TopicCandidate> enriched = processBatch(batch);
                result.addAll(enriched);
            } catch (Exception e) {
                log.warn("[AI筛选] 批次 {}-{} 处理失败，使用原始数据: {}", i, i + batch.size(), e.getMessage());
                result.addAll(batch); // fallback
            }
        }
        return result;
    }

    private List<TopicCandidate> processBatch(List<TopicCandidate> batch) {
        String prompt = buildPrompt(batch);
        String systemPrompt = """
                你是一名资深保险营销内容专家，精通小红书、抖音、公众号的内容运营。
                你的任务是分析社会热点标题，判断保险代理人能否借势创作内容，并给出详细分析。
                请严格按照 JSON 格式输出，不要输出任何其他文字。
                """ + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();

        String raw = deepSeek.chat(systemPrompt, prompt, "chat");
        return parseAndApply(raw, batch);
    }

    private String buildPrompt(List<TopicCandidate> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是来自各大平台的热点标题列表，请逐条分析并返回 JSON 数组。\n\n");
        sb.append("【分析标准】\n");
        sb.append("- relevant: 保险代理人能否借助这个热点创作内容（true/false）\n");
        sb.append("- aiScore: 保险内容创作价值评分 1-5（5=极佳切入点，1=完全无关）\n");
        sb.append("- insuranceTypes: 对应险种，从[重疾险,医疗险,意外险,寿险,养老险,车险,财产险,理财险]中选，可多选，无关则空数组\n");
        sb.append("- demographics: 目标人群，从[年轻人,中年人,老年人,宝妈,上班族,创业者,学生,中产]中选，可多选\n");
        sb.append("- recommendedPlatforms: 建议发布平台，从[小红书,抖音,公众号,视频号]中选，可多选\n");
        sb.append("- whyThisTopic: 一句话说明为什么保险代理人应该借助这个热点，要具体（最多50字）\n\n");
        sb.append("【热点列表】\n");
        for (int i = 0; i < batch.size(); i++) {
            TopicCandidate c = batch.get(i);
            String title = c.getTitle() == null ? "" : c.getTitle();
            String angle = c.getAngle() == null ? "" : c.getAngle();
            if (angle.isBlank()) {
                sb.append(i).append(". ").append(title).append("\n");
            } else {
                sb.append(i).append(". ").append(title).append("（热度：").append(angle).append("）\n");
            }
        }
        sb.append("\n【输出格式】严格输出 JSON 数组，例如：\n");
        sb.append("[{\"index\":0,\"relevant\":true,\"aiScore\":4,\"insuranceTypes\":[\"医疗险\"],");
        sb.append("\"demographics\":[\"上班族\"],\"recommendedPlatforms\":[\"抖音\",\"公众号\"],");
        sb.append("\"whyThisTopic\":\"医保改革话题热度高，正是切入商业医疗险必要性的最佳时机\"},...]\n");
        return sb.toString();
    }

    private List<TopicCandidate> parseAndApply(String raw, List<TopicCandidate> batch) {
        String json = extractJsonArray(raw);
        List<TopicCandidate> result = new ArrayList<>(batch); // default: return as-is

        try {
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) {
                log.warn("[AI筛选] 响应不是 JSON 数组，跳过应用");
                return result;
            }

            Map<Integer, JsonNode> indexMap = new HashMap<>();
            for (JsonNode node : arr) {
                int idx = node.path("index").asInt(-1);
                if (idx >= 0 && idx < batch.size()) {
                    indexMap.put(idx, node);
                }
            }

            List<TopicCandidate> enriched = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                TopicCandidate c = batch.get(i);
                JsonNode info = indexMap.get(i);
                if (info == null) {
                    enriched.add(c); // no AI info, keep as-is
                    continue;
                }

                boolean relevant = info.path("relevant").asBoolean(true);
                int aiScore = info.path("aiScore").asInt(3);

                // 不相关且 AI 评分 <= 1 的直接过滤掉
                if (!relevant && aiScore <= 1) {
                    log.debug("[AI筛选] 过滤无关热点: {}", c.getTitle());
                    continue;
                }

                // 应用 AI 分析结果
                applyAiResult(c, info, aiScore);
                enriched.add(c);
            }
            return enriched;
        } catch (Exception e) {
            log.warn("[AI筛选] 解析 AI 响应失败: {} | raw={}", e.getMessage(), raw.substring(0, Math.min(200, raw.length())));
            return result; // fallback
        }
    }

    private void applyAiResult(TopicCandidate c, JsonNode info, int aiScore) {
        // 保留原始 AI 评分供下游使用（手动刷新独立评分依赖这个字段）
        c.setAiScore(aiScore);

        // whyThisTopic
        String why = info.path("whyThisTopic").asText("");
        if (!why.isBlank()) {
            c.setWhyThisTopic(why);
        }

        // insuranceTypes (AI 结果优先，原有规则结果兜底)
        List<String> aiInsTypes = toStringList(info.path("insuranceTypes"));
        if (!aiInsTypes.isEmpty()) {
            c.setInsuranceTypes(aiInsTypes);
        }

        // demographics
        List<String> aiDemographics = toStringList(info.path("demographics"));
        if (!aiDemographics.isEmpty()) {
            c.setDemographics(aiDemographics);
        }

        // recommendedPlatforms
        List<String> platforms = toStringList(info.path("recommendedPlatforms"));
        if (!platforms.isEmpty()) {
            c.setRecommendedPlatforms(platforms);
        }

        // TOPHUB 评分：AI 相关性(占主导) + 热度(辅助)
        // 硬封顶 50，保证 TOPHUB 始终低于 BITABLE 最低分 60
        // profile 加成在 TopicGenerationService 统一叠加（最终封顶 58）
        int heatScore = c.getScore(); // 当前是纯热度分 10-50
        int newScore = (int) Math.round(aiScore * 5.5 + heatScore * 0.25);
        newScore = Math.min(50, Math.max(8, newScore));
        c.setScore(newScore);
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) return result;
        if (node.isArray()) {
            for (JsonNode item : node) {
                String s = item.asText("").trim();
                if (!s.isBlank()) result.add(s);
            }
        }
        return result;
    }

    /** 从 AI 响应中提取 JSON 数组，容忍 markdown 代码块包裹 */
    private String extractJsonArray(String raw) {
        if (raw == null) return "[]";
        String s = raw.strip();
        // 去掉 markdown 代码块
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start > 0 && end > start) {
                s = s.substring(start + 1, end).strip();
            }
        }
        // 找到第一个 [ 和最后一个 ]
        int begin = s.indexOf('[');
        int finish = s.lastIndexOf(']');
        if (begin >= 0 && finish > begin) {
            return s.substring(begin, finish + 1);
        }
        return "[]";
    }
}
