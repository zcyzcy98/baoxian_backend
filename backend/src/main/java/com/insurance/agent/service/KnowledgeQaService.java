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
        String evidenceBrief = buildEvidenceBrief(question, collection, context);
        String system = buildSystemPrompt(collection) + evidenceBrief;
        return deepSeek.chat(system, question, "chat");
    }

    private String buildEvidenceBrief(String question, String collection, String context) {
        if (context == null || context.isBlank()) {
            return "\n\n【知识库证据整理】\n未召回到可用知识库内容。请基于通用保险知识回答，并明确提示“未检索到知识库依据”。\n";
        }
        String system = """
                你是一位保险知识库证据整理助手。
                你的任务不是回答用户，而是把 RAG 召回内容整理成最终回答可用的证据摘要。

                【整理要求】
                1. 提取和用户问题直接相关的事实、规则、案例或注意事项。
                2. 标出哪些内容可以直接回答，哪些内容只是相近参考。
                3. 如果召回内容不足以支持明确结论，要写出“证据不足点”。
                4. 不要编造知识库没有的信息，不要输出最终回答。

                【输出格式】
                [可直接使用的依据]
                <bullet 列表>

                [相近但需谨慎使用的内容]
                <bullet 列表，没有则写“无”>

                [证据不足点]
                <bullet 列表，没有则写“无”>
                """ + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();
        String user = """
                【用户问题】
                %s

                【知识库类型】
                %s

                %s
                """.formatted(question, collection, context);
        try {
            String brief = deepSeek.chat(system, user, "chat");
            return """

                    【知识库证据整理】
                    以下内容由 RAG 召回条目整理而来。最终回答必须优先基于“可直接使用的依据”；证据不足处要明确提示。

                    %s
                    """.formatted(brief.trim());
        } catch (Exception e) {
            log.warn("[KnowledgeQA] 证据整理失败，降级为直接使用召回内容 collection={} error={}", collection, e.getMessage());
            return context;
        }
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
                3. 优先引用【知识库证据整理】中的具体依据，保持准确
                4. 涉及具体产品费率或条款，提醒用户以实际保单为准
                5. 回答长度适中，重点突出，不要泛泛而谈
                6. 如果证据不足，不要强行下结论，要告诉用户需要补充哪些信息
                """ + PromptRules.insuranceCompliance()
                + PromptRules.factuality()
                + PromptRules.outputDiscipline();
    }
}
