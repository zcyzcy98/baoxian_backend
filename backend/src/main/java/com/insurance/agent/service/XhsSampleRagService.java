package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class XhsSampleRagService {

    private static final Logger log = LoggerFactory.getLogger(XhsSampleRagService.class);
    private static final String VECTOR_TABLE = "小红书爆款样本库_向量";
    private static final int TOP_K = 3;

    private final PostgresVectorStoreService vectorStore;
    private final DeepSeekService deepSeek;
    private volatile List<PostgresVectorStoreService.VectorHit> lastHits = List.of();

    public XhsSampleRagService(PostgresVectorStoreService vectorStore, DeepSeekService deepSeek) {
        this.vectorStore = vectorStore;
        this.deepSeek = deepSeek;
    }

    /** 返回上次搜索的样本元数据，用于前端展示引用来源 */
    public List<Map<String, String>> getLastSearchResults() {
        return lastHits.stream().map(hit -> {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("index", String.valueOf(lastHits.indexOf(hit) + 1));
            m.put("title", hit.metadata().getOrDefault("field_标题", ""));
            m.put("emotion", hit.metadata().getOrDefault("field_情绪", ""));
            m.put("persona", hit.metadata().getOrDefault("field_人设", ""));
            m.put("tags", hit.metadata().getOrDefault("field_标签", ""));
            return m;
        }).toList();
    }

    public String buildContext(String query) {
        return buildAnalyzedContext(query, null, "小红书内容创作", "chat");
    }

    public String buildAnalyzedContext(String query, String userStyle, String taskType, String requestedModel) {
        List<PostgresVectorStoreService.VectorHit> hits = searchSamples(query);
        if (hits.isEmpty()) return "";

        String rawSamples = buildRawSamples(hits);
        String analysisSystem = """
                你是一位小红书爆款样本拆解专家，专注保险科普内容。
                你的任务不是创作正文，而是从召回的真实样本中提炼“可复用写作框架”，供下一步创作使用。

                【分析目标】
                1. 分析 3 条样本共同的标题钩子：人设、场景、冲突、情绪、利益点分别怎么出现。
                2. 分析正文结构：开头如何抓人，中段如何分点，结尾如何互动。
                3. 分析语言风格：句长、口语感、emoji 使用、情绪强度、专业解释方式。
                4. 分析保险内容处理方式：如何讲干货、如何避免强销售、如何降低合规风险。
                5. 给出一个可套用但不照抄的创作框架。

                【约束】
                - 用户选择的风格优先；样本框架只作为参考，不要覆盖用户风格。
                - 不要复述大段样本文案，不要生成最终标题或正文。
                - 输出必须简洁，控制在 500 字以内。

                【输出格式】
                [样本共性]
                <用 3-5 条 bullet 总结>

                [可复用标题公式]
                <给 3 条公式，不要给成品标题>

                [可复用正文框架]
                <按开头/中段/结尾写>

                [创作注意事项]
                <风格、合规、原创性提醒>
                """ + PromptRules.xhsPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();

        StringBuilder user = new StringBuilder();
        user.append("【本次用户需求】\n").append(query).append("\n\n");
        if (userStyle != null && !userStyle.isBlank()) {
            user.append("【用户选择/填写的风格】\n").append(userStyle.trim()).append("\n\n");
        }
        user.append("【任务类型】\n").append(taskType == null ? "小红书内容创作" : taskType).append("\n\n");
        user.append(rawSamples);

        String framework;
        try {
            framework = deepSeek.chat(analysisSystem, user.toString(), requestedModel);
        } catch (Exception e) {
            log.warn("[RAG] 爆款样本分析失败，降级为直接样本参考。query={} error={}", query, e.getMessage());
            framework = "样本分析失败。请仅参考下方样本的标题钩子、结构节奏和表达方式，保持原创，不要照抄。";
        }

        return """

                【RAG 爆款样本分析结果】
                以下内容是基于相似爆款样本提炼出的写作框架。创作时优先满足用户输入，其次参考该框架；不得照抄样本原句。

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
            log.error("[RAG] 爆款样本库搜索失败，降级为无RAG模式。query={} error={}", query, e.getMessage(), e);
            return List.of();
        }
        log.info("[RAG] query='{}' 召回 {} 条样本", query, hits.size());
        if (hits.isEmpty()) {
            log.warn("[RAG] 未召回任何样本，检查向量表是否有数据");
            return List.of();
        }
        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> meta = hits.get(i).metadata();
            log.info("[RAG] 样本{} 标题='{}' 相似度={} 标签='{}'",
                    i + 1,
                    meta.getOrDefault("field_标题", ""),
                    String.format("%.3f", hits.get(i).similarity()),
                    meta.getOrDefault("field_标签", ""));
        }
        lastHits = hits;
        return hits;
    }

    private String buildRawSamples(List<PostgresVectorStoreService.VectorHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是从爆款样本库中召回的真实高互动笔记：\n\n");

        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> meta = hits.get(i).metadata();
            sb.append("--- 参考样本 ").append(i + 1).append(" ---\n");
            appendField(sb, "标题", meta.get("field_标题"));
            appendField(sb, "情绪", meta.get("field_情绪"));
            appendField(sb, "人设", meta.get("field_人设"));
            appendField(sb, "标签", meta.get("field_标签"));
            String content = meta.get("field_正文");
            if (content != null && !content.isBlank()) {
                sb.append("正文：\n").append(content.trim()).append("\n");
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
