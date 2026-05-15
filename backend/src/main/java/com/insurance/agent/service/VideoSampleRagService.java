package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VideoSampleRagService {

    private static final Logger log = LoggerFactory.getLogger(VideoSampleRagService.class);
    private static final String VECTOR_TABLE = "短视频爆款样本库_向量";
    private static final int TOP_K = 3;

    private final PostgresVectorStoreService vectorStore;
    private final DeepSeekService deepSeek;
    private volatile List<PostgresVectorStoreService.VectorHit> lastHits = List.of();

    public VideoSampleRagService(PostgresVectorStoreService vectorStore, DeepSeekService deepSeek) {
        this.vectorStore = vectorStore;
        this.deepSeek = deepSeek;
    }

    /** 返回上次搜索的样本元数据，用于前端展示引用来源 */
    public List<Map<String, String>> getLastSearchResults() {
        return lastHits.stream().map(hit -> {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("index", String.valueOf(lastHits.indexOf(hit) + 1));
            m.put("title", hit.metadata().getOrDefault("field_标题", ""));
            m.put("tags", hit.metadata().getOrDefault("field_属性", hit.metadata().getOrDefault("field_标签", "")));
            m.put("platform", hit.metadata().getOrDefault("field_平台", ""));
            return m;
        }).toList();
    }

    public String buildContext(String query) {
        return buildAnalyzedContext(query, null, "短视频口播脚本", "chat");
    }

    public String buildAnalyzedContext(String query, String userStyle, String taskType, String requestedModel) {
        List<PostgresVectorStoreService.VectorHit> hits = searchSamples(query);
        if (hits.isEmpty()) return "";

        String rawSamples = buildRawSamples(hits);
        String analysisSystem = """
                你是一位短视频爆款脚本拆解专家，专注保险科普内容。
                你的任务不是创作正文，而是从召回的真实样本中提炼「可复用创作框架」，供下一步脚本创作使用。

                【分析目标】
                1. 分析 3 条样本共同的开场钩子设计：用了哪类开场——数据/痛点/反问/反常识。
                2. 分析内容节奏：开场钩子 → 痛点铺陈 → 干货分点 → 行动召唤各占多少比重。
                3. 分析语言风格：句长、口语感、情绪调动方式、专业内容如何平民化。
                4. 分析保险内容处理方式：如何讲干货、如何避免强销售感。
                5. 给出一个可套用但不照抄的脚本创作框架。

                【约束】
                - 用户选择的风格/主题优先；样本框架只作为参考，不要覆盖用户需求。
                - 不要复述大段样本脚本原文，不要生成最终成品脚本。
                - 输出必须简洁，控制在 500 字以内。

                【输出格式】
                [样本共性]
                <用 3-5 条 bullet 总结>

                [可复用开场钩子公式]
                <给 3 条公式，不要给成品句子>

                [可复用脚本框架]
                <按开场/铺陈/干货/行动召唤写>

                [创作注意事项]
                <风格、合规、原创性提醒>
                """ + PromptRules.shortVideoPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();

        StringBuilder user = new StringBuilder();
        user.append("【本次用户需求】\n").append(query).append("\n\n");
        if (userStyle != null && !userStyle.isBlank()) {
            user.append("【用户选择/填写的风格】\n").append(userStyle.trim()).append("\n\n");
        }
        user.append("【任务类型】\n").append(taskType == null ? "短视频口播脚本" : taskType).append("\n\n");
        user.append(rawSamples);

        String framework;
        try {
            framework = deepSeek.chat(analysisSystem, user.toString(), requestedModel);
        } catch (Exception e) {
            log.warn("[VideoRAG] 爆款样本分析失败，降级为直接样本参考。query={} error={}", query, e.getMessage());
            framework = "样本分析失败。请仅参考下方样本的开场钩子、节奏结构和表达方式，保持原创，不要照抄。";
        }

        return """

                【RAG 爆款样本分析结果】
                以下内容是基于相似爆款视频样本提炼出的脚本框架。创作时优先满足用户输入，其次参考该框架；不得照抄样本原句。

                %s

                【召回样本原文（仅供核对，不得照抄）】
                %s
                """.formatted(framework.trim(), rawSamples);
    }

    private List<PostgresVectorStoreService.VectorHit> searchSamples(String query) {
        List<PostgresVectorStoreService.VectorHit> hits;
        try {
            hits = vectorStore.searchByTableName(VECTOR_TABLE, query, TOP_K);
        } catch (Exception e) {
            log.warn("[VideoRAG] 爆款样本库搜索失败，降级为无RAG模式。query={} error={}", query, e.getMessage());
            return List.of();
        }
        log.info("[VideoRAG] query='{}' 召回 {} 条样本", query, hits.size());
        if (hits.isEmpty()) {
            log.warn("[VideoRAG] 未召回任何样本，检查向量表 {} 是否有数据", VECTOR_TABLE);
            return List.of();
        }
        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> meta = hits.get(i).metadata();
            log.info("[VideoRAG] 样本{} 标题='{}' 相似度={}",
                    i + 1,
                    meta.getOrDefault("field_标题", ""),
                    String.format("%.3f", hits.get(i).similarity()));
        }
        lastHits = hits;
        return hits;
    }

    private String buildRawSamples(List<PostgresVectorStoreService.VectorHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从爆款样本库中召回的真实高互动视频脚本：\n\n");

        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> meta = hits.get(i).metadata();
            sb.append("--- 参考样本 ").append(i + 1).append(" ---\n");
            appendField(sb, "标题", meta.get("field_标题"));
            appendField(sb, "博主", meta.get("field_博主链接"));
            appendField(sb, "平台", meta.get("field_平台"));
            appendField(sb, "时长", meta.get("field_时长"));
            String tags = meta.get("field_属性");
            if (tags == null || tags.isBlank()) tags = meta.get("field_标签");
            appendField(sb, "属性/标签", tags);
            // 脚本字段兼容多种命名
            String script = meta.get("field_台词");
            if (script == null || script.isBlank()) script = meta.get("field_脚本");
            if (script == null || script.isBlank()) script = meta.get("field_内容");
            if (script == null || script.isBlank()) script = meta.get("field_口播");
            if (script != null && !script.isBlank()) {
                sb.append("脚本：\n").append(script.trim()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value.trim()).append("\n");
        }
    }
}
