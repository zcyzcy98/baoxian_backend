package com.insurance.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ComplianceCheckService {
    private static final String COLLECTION_NAME = "compliance_words";
    private static final int RECALL_TOP_K = 16;

    private final KnowledgeVectorIndexService vectorIndexService;

    public ComplianceCheckService(KnowledgeVectorIndexService vectorIndexService) {
        this.vectorIndexService = vectorIndexService;
    }

    public ComplianceCheckResult check(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("待检测文本不能为空");
        }

        String normalizedText = normalize(content);
        Map<String, HitAccumulator> hitMap = new LinkedHashMap<>();
        for (KnowledgeVectorIndexService.KnowledgeDocument doc : vectorIndexService.listByCollection(COLLECTION_NAME)) {
            registerIfMatched(hitMap, normalizedText, doc, 0D, false);
        }

        List<MatchedPhrase> matched = hitMap.values().stream()
                .sorted(Comparator
                        .comparingInt(HitAccumulator::firstIndex)
                        .thenComparing(HitAccumulator::matchedPhraseLength, Comparator.reverseOrder()))
                .map(HitAccumulator::toResult)
                .toList();

        if (!matched.isEmpty()) {
            String cleanedContent = removeMatchedPhrases(content, matched);
            String summary = buildSummary(content, false, 0, matched, List.of());
            return new ComplianceCheckResult(
                    content,
                    true,
                    cleanedContent,
                    summary,
                    0,
                    0,
                    0,
                    matched,
                    List.of()
            );
        }

        List<KnowledgeVectorIndexService.ScoredDocument> recalled = vectorIndexService.search(
                content.trim(),
                COLLECTION_NAME,
                RECALL_TOP_K
        );
        List<RelatedRule> relatedRules = recalled.stream()
                .map(hit -> toRelatedRule(hit.document(), hit.similarity()))
                .toList();
        String summary = buildSummary(content, true, relatedRules.size(), matched, relatedRules);
        return new ComplianceCheckResult(
                content,
                false,
                content,
                summary,
                1,
                relatedRules.size(),
                recalled.size(),
                matched,
                relatedRules
        );
    }

    private boolean registerIfMatched(
            Map<String, HitAccumulator> hitMap,
            String normalizedText,
            KnowledgeVectorIndexService.KnowledgeDocument doc,
            double similarity,
            boolean recalledByRag
    ) {
        Map<String, String> metadata = doc.metadata();
        String phraseSource = firstNonBlank(
                metadata.get("违规词/话术"),
                metadata.get("违规词"),
                metadata.get("敏感词语"),
                metadata.get("罹患词语")
        );
        if (phraseSource.isBlank()) {
            return false;
        }

        List<String> phrases = parsePhrases(phraseSource);
        boolean matched = false;
        for (String phrase : phrases) {
            String normalizedPhrase = normalize(phrase);
            if (normalizedPhrase.isBlank()) {
                continue;
            }
            int firstIndex = normalizedText.indexOf(normalizedPhrase);
            if (firstIndex < 0) {
                continue;
            }
            matched = true;
            String key = doc.id() + "::" + normalizedPhrase;
            HitAccumulator existing = hitMap.get(key);
            if (existing == null) {
                hitMap.put(key, new HitAccumulator(
                        doc.id(),
                        phrase.trim(),
                        firstIndex,
                        firstNonBlank(
                                metadata.get("违规原因"),
                                metadata.get("违规原因/风险点"),
                                metadata.get("违规说明")
                        ),
                        firstNonBlank(
                                metadata.get("合规替换建议"),
                                metadata.get("合规建议"),
                                metadata.get("替换建议")
                        ),
                        buildSensitiveTips(metadata),
                        similarity,
                        recalledByRag,
                        metadata
                ));
            } else {
                existing.merge(similarity, recalledByRag, metadata);
            }
        }
        return matched;
    }

    private String buildSummary(
            String originalText,
            boolean usedRag,
            int recalledDocCount,
            List<MatchedPhrase> matched,
            List<RelatedRule> relatedRules
    ) {
        int textLength = originalText == null ? 0 : originalText.length();
        if (matched.isEmpty()) {
            String ragText = usedRag
                    ? "未发现精确命中项，已追加 RAG 相似规则召回，供人工复核语义风险。"
                    : "未发现精确命中项。";
            return "检测完成，当前文本未命中合规词库中的明确违规词/话术。"
                    + "\n\n" + ragText
                    + "\n\n检测信息：文本长度 " + textLength
                    + " 字，RAG 召回 " + recalledDocCount
                    + " 条相似规则。";
        }

        return "检测完成，发现 " + matched.size()
                + " 处疑似不合规表述。已优先使用“合规词库”精确命中锁定违规词/话术。"
                + "\n\n检测信息：文本长度 " + textLength
                + " 字，未触发 RAG 召回。";
    }

    private List<String> parsePhrases(String phraseSource) {
        if (phraseSource == null || phraseSource.isBlank()) {
            return List.of();
        }
        String normalized = phraseSource
                .replace("；", "\n")
                .replace(";", "\n")
                .replace("、", "\n")
                .replace("/", "\n")
                .replace("|", "\n")
                .replace("，", "\n")
                .replace(",", "\n");
        List<String> phrases = new ArrayList<>();
        for (String part : normalized.split("\\R+")) {
            String cleaned = stripQuotes(part).trim();
            if (!cleaned.isBlank()) {
                phrases.add(cleaned);
            }
        }
        return phrases;
    }

    private String stripQuotes(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("‘", "")
                .replace("’", "")
                .replace("'", "");
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("，", ",")
                .replace("。", ".")
                .replace("：", ":")
                .replace("；", ";")
                .replace("（", "(")
                .replace("）", ")");
    }

    private String buildSensitiveTips(Map<String, String> metadata) {
        List<String> parts = new ArrayList<>();
        appendIfPresent(parts, metadata, "敏感词语");
        appendIfPresent(parts, metadata, "敏感词");
        appendIfPresent(parts, metadata, "罹患词语");
        appendIfPresent(parts, metadata, "罹患词");
        appendIfPresent(parts, metadata, "风险等级");
        appendIfPresent(parts, metadata, "风险提示");
        return String.join("；", parts);
    }

    private void appendIfPresent(List<String> parts, Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        if (value != null && !value.isBlank()) {
            parts.add(key + "：" + value.trim());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private RelatedRule toRelatedRule(KnowledgeVectorIndexService.KnowledgeDocument doc, double similarity) {
        Map<String, String> metadata = doc.metadata();
        return new RelatedRule(
                firstNonBlank(
                        metadata.get("违规词/话术"),
                        metadata.get("违规词"),
                        metadata.get("敏感词语"),
                        metadata.get("罹患词语")
                ),
                firstNonBlank(
                        metadata.get("违规原因"),
                        metadata.get("违规原因/风险点"),
                        metadata.get("违规说明")
                ),
                firstNonBlank(
                        metadata.get("合规替换建议"),
                        metadata.get("合规建议"),
                        metadata.get("替换建议")
                ),
                buildSensitiveTips(metadata),
                similarity,
                metadata == null ? Map.of() : new LinkedHashMap<>(metadata)
        );
    }

    private String removeMatchedPhrases(String content, List<MatchedPhrase> matched) {
        if (content == null || content.isBlank()) {
            return "";
        }
        NormalizedText normalizedContent = normalizeWithSourceMap(content);
        List<String> phrases = matched.stream()
                .map(MatchedPhrase::matchedPhrase)
                .filter(phrase -> phrase != null && !phrase.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        List<TextRange> ranges = new ArrayList<>();
        for (String phrase : phrases) {
            String normalizedPhrase = normalize(phrase);
            if (normalizedPhrase.isBlank()) {
                continue;
            }
            int from = 0;
            while (from < normalizedContent.text().length()) {
                int idx = normalizedContent.text().indexOf(normalizedPhrase, from);
                if (idx < 0) {
                    break;
                }
                int originalStart = normalizedContent.sourceIndexes().get(idx);
                int originalEnd = normalizedContent.sourceIndexes().get(idx + normalizedPhrase.length() - 1) + 1;
                ranges.add(new TextRange(originalStart, originalEnd));
                from = idx + normalizedPhrase.length();
            }
        }
        if (ranges.isEmpty()) {
            return content;
        }
        ranges.sort(Comparator.comparingInt(TextRange::start).reversed());
        StringBuilder sb = new StringBuilder(content);
        for (TextRange range : mergeRanges(ranges)) {
            sb.delete(range.start(), range.end());
        }
        return cleanupRemovedText(sb.toString());
    }

    private String cleanupRemovedText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" *([，。！？；：,.!?;:])", "$1")
                .replaceAll("([，；、,;]){2,}", "$1")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private NormalizedText normalizeWithSourceMap(String text) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            String normalizedChar = normalize(String.valueOf(ch));
            if (normalizedChar.isBlank()) {
                continue;
            }
            normalized.append(normalizedChar);
            for (int j = 0; j < normalizedChar.length(); j++) {
                sourceIndexes.add(i);
            }
        }
        return new NormalizedText(normalized.toString(), sourceIndexes);
    }

    private List<TextRange> mergeRanges(List<TextRange> ranges) {
        List<TextRange> ascending = ranges.stream()
                .sorted(Comparator.comparingInt(TextRange::start))
                .toList();
        List<TextRange> merged = new ArrayList<>();
        for (TextRange range : ascending) {
            if (merged.isEmpty()) {
                merged.add(range);
                continue;
            }
            TextRange last = merged.get(merged.size() - 1);
            if (range.start() <= last.end()) {
                merged.set(merged.size() - 1, new TextRange(last.start(), Math.max(last.end(), range.end())));
            } else {
                merged.add(range);
            }
        }
        merged.sort(Comparator.comparingInt(TextRange::start).reversed());
        return merged;
    }

    public record ComplianceCheckResult(
            String content,
            boolean hasRisk,
            String cleanedContent,
            String summary,
            int chunkCount,
            int recalledRuleCount,
            int recalledCandidateCount,
            List<MatchedPhrase> matchedPhrases,
            List<RelatedRule> relatedRules
    ) {}

    public record MatchedPhrase(
            String matchedPhrase,
            int firstIndex,
            String violationReason,
            String replacementSuggestion,
            String sensitiveTips,
            boolean recalledByRag,
            double similarity,
            Map<String, String> metadata
    ) {}

    public record RelatedRule(
            String phrase,
            String violationReason,
            String replacementSuggestion,
            String sensitiveTips,
            double similarity,
            Map<String, String> metadata
    ) {}

    private record NormalizedText(String text, List<Integer> sourceIndexes) {}

    private record TextRange(int start, int end) {}

    private static final class HitAccumulator {
        private final String documentId;
        private final String matchedPhrase;
        private final int firstIndex;
        private final String violationReason;
        private final String replacementSuggestion;
        private final String sensitiveTips;
        private boolean recalledByRag;
        private double similarity;
        private Map<String, String> metadata;

        private HitAccumulator(
                String documentId,
                String matchedPhrase,
                int firstIndex,
                String violationReason,
                String replacementSuggestion,
                String sensitiveTips,
                double similarity,
                boolean recalledByRag,
                Map<String, String> metadata
        ) {
            this.documentId = documentId;
            this.matchedPhrase = matchedPhrase;
            this.firstIndex = firstIndex;
            this.violationReason = violationReason;
            this.replacementSuggestion = replacementSuggestion;
            this.sensitiveTips = sensitiveTips;
            this.similarity = similarity;
            this.recalledByRag = recalledByRag;
            this.metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
        }

        private void merge(double similarity, boolean recalledByRag, Map<String, String> metadata) {
            this.similarity = Math.max(this.similarity, similarity);
            this.recalledByRag = this.recalledByRag || recalledByRag;
            if (metadata != null && !metadata.isEmpty()) {
                this.metadata = new LinkedHashMap<>(metadata);
            }
        }

        private int firstIndex() {
            return firstIndex;
        }

        private int matchedPhraseLength() {
            return matchedPhrase == null ? 0 : matchedPhrase.length();
        }

        private MatchedPhrase toResult() {
            Map<String, String> enriched = new LinkedHashMap<>(metadata);
            enriched.put("rule_id", documentId);
            return new MatchedPhrase(
                    matchedPhrase,
                    firstIndex,
                    Objects.requireNonNullElse(violationReason, ""),
                    Objects.requireNonNullElse(replacementSuggestion, ""),
                    Objects.requireNonNullElse(sensitiveTips, ""),
                    recalledByRag,
                    similarity,
                    enriched
            );
        }
    }
}
