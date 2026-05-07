package com.insurance.agent.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量搜索服务 - 使用 PostgreSQL + pgvector
 */
@Service
public class VectorService {
    private final PostgresVectorStoreService vectorStore;

    public VectorService(PostgresVectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SearchResult search(String query, String collection, int topK) {
        try {
            if (query == null || query.isBlank()) {
                return new SearchResult(false, "请提供查询内容", null, 0, List.of());
            }

            List<SearchItem> items = vectorStore.search(query.trim(), collection, topK).stream()
                    .map(hit -> new SearchItem(
                            hit.id(),
                            hit.document(),
                            hit.metadata(),
                            hit.collectionName(),
                            hit.sheetName(),
                            hit.distance(),
                            hit.similarity()
                    ))
                    .toList();

            return new SearchResult(true, null, query.trim(), items.size(), items);
        } catch (Exception e) {
            return new SearchResult(false, e.getMessage(), null, 0, List.of());
        }
    }

    public StatsResult getStats() {
        PostgresVectorStoreService.VectorStats stats = vectorStore.stats();
        return new StatsResult(
                stats.total(),
                stats.collections().stream()
                        .map(item -> new CollectionInfo(item.sheetName(), item.collectionName(), item.count()))
                        .toList()
        );
    }

    public record SearchResult(
            boolean success,
            String error,
            String query,
            int count,
            List<SearchItem> results
    ) {}

    public record SearchItem(
            String id,
            String document,
            Map<String, String> metadata,
            String collectionName,
            String sheetName,
            double distance,
            double similarity
    ) {}

    public record StatsResult(
            int total,
            List<CollectionInfo> collections
    ) {}

    public record CollectionInfo(
            String sheetName,
            String collectionName,
            int count
    ) {}
}
