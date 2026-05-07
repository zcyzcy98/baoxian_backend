package com.insurance.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeQaService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeQaService.class);
    private static final int TOP_K = 3;

    private final PostgresVectorStoreService vectorStore;
    private final DeepSeekService deepSeek;

    public KnowledgeQaService(PostgresVectorStoreService vectorStore, DeepSeekService deepSeek) {
        this.vectorStore = vectorStore;
        this.deepSeek = deepSeek;
    }

    public String answer(String question, String collection) {
        List<PostgresVectorStoreService.VectorHit> hits;
        try {
            hits = vectorStore.search(question, collection, TOP_K);
        } catch (Exception e) {
            log.error("[KnowledgeQA] 向量搜索失败 collection={}: {}", collection, e.getMessage());
            hits = List.of();
        }
        log.info("[KnowledgeQA] collection={} 召回 {} 条，query='{}'", collection, hits.size(), question);

        String context = buildContext(hits);
        String system = buildSystemPrompt(collection) + context;
        return deepSeek.chat(system, question, "chat");
    }

    private String buildContext(List<PostgresVectorStoreService.VectorHit> hits) {
        if (hits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n【知识库召回内容】\n");
        for (int i = 0; i < hits.size(); i++) {
            PostgresVectorStoreService.VectorHit hit = hits.get(i);
            log.info("[KnowledgeQA] 条目{} 相似度={} id={}", i + 1,
                    String.format("%.3f", hit.similarity()), hit.id());
            sb.append("\n--- 参考条目 ").append(i + 1)
              .append("（相似度 ").append(String.format("%.3f", hit.similarity())).append("）---\n")
              .append(hit.document()).append("\n");
        }
        sb.append("\n请优先基于以上知识库内容回答。若内容不足，可补充一般性专业知识，但请注明。\n");
        return sb.toString();
    }

    private String buildSystemPrompt(String collection) {
        String role = switch (collection) {
            case "faq_qa" -> "你是一位专业的保险顾问，擅长用通俗易懂的语言解答客户对保险的常见疑问。";
            case "claim_cases" -> "你是一位资深保险理赔专家，熟悉各类真实理赔案例和赔付规则，能帮助客户了解理赔流程和关键注意点。";
            case "insurance_products" -> "你是一位保险产品专家，对各类险种的核心定义、适用人群和核心价值了如指掌，能帮助客户选择合适的险种。";
            case "insurance_tips" -> "你是一位专业的保险顾问，熟悉投保过程中的各类注意事项、常见陷阱和正确做法，能帮助客户少走弯路。";
            case "coverage_details" -> "你是一位保险责任专家，精通各险种的保障范围、赔付条件和责任边界，能清晰解释保障条款。";
            default -> "你是一位专业的保险顾问。";
        };
        return role + """

                回答要求：
                1. 语言通俗易懂，避免堆砌术语；如需使用术语，请简短解释
                2. 结构清晰，适当分点或使用小标题
                3. 优先引用知识库中的具体内容，保持准确
                4. 涉及具体产品费率或条款，提醒用户以实际保单为准
                5. 回答长度适中，重点突出，不要泛泛而谈
                """;
    }
}
