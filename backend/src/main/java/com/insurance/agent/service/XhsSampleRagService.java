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

    public XhsSampleRagService(PostgresVectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String buildContext(String query) {
        List<PostgresVectorStoreService.VectorHit> hits;
        try {
            hits = vectorStore.searchByTableName(VECTOR_TABLE, query, TOP_K);
        } catch (Exception e) {
            log.error("[RAG] 爆款样本库搜索失败，降级为无RAG模式。query={} error={}", query, e.getMessage(), e);
            return "";
        }
        log.info("[RAG] query='{}' 召回 {} 条样本", query, hits.size());
        if (hits.isEmpty()) {
            log.warn("[RAG] 未召回任何样本，检查向量表是否有数据");
            return "";
        }
        for (int i = 0; i < hits.size(); i++) {
            Map<String, String> meta = hits.get(i).metadata();
            log.info("[RAG] 样本{} 标题='{}' 相似度={} 标签='{}'",
                    i + 1,
                    meta.getOrDefault("field_标题", ""),
                    String.format("%.3f", hits.get(i).similarity()),
                    meta.getOrDefault("field_标签", ""));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【爆款样本参考】\n");
        sb.append("以下是从爆款样本库中召回的真实高互动笔记，请仔细学习其结构、情绪表达和钩子设计，");
        sb.append("生成风格相似但内容原创的文案，不要逐字复制。\n\n");

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
