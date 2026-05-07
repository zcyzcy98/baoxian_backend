package com.insurance.agent.controller;

import com.insurance.agent.service.VectorService;
import com.insurance.agent.service.VectorService.*;
import com.insurance.agent.service.PostgresVectorStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量搜索API
 */
@RestController
@RequestMapping("/api/vector")
public class VectorController {

    @Autowired
    private VectorService vectorService;

    @Autowired
    private PostgresVectorStoreService vectorStoreService;

    /**
     * 搜索
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(required = false) String collection,
            @RequestParam(name = "topK", defaultValue = "5") int topK,
            @RequestParam(name = "top_k", required = false) Integer topKSnake
    ) {
        int effectiveTopK = topKSnake != null ? topKSnake : topK;
        SearchResult result = vectorService.search(query, collection, effectiveTopK);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        
        if (!result.success()) {
            response.put("error", result.error());
            return ResponseEntity.badRequest().body(response);
        }
        
        response.put("query", result.query());
        response.put("count", result.count());
        response.put("results", result.results().stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("document", item.document());
                    itemMap.put("sheet_name", item.sheetName());
                    itemMap.put("collection_name", item.collectionName());
                    itemMap.put("metadata", item.metadata());
                    itemMap.put("similarity", String.format("%.2f%%", item.similarity() * 100));
                    return itemMap;
                })
                .toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取统计
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResult> getStats() {
        return ResponseEntity.ok(vectorService.getStats());
    }

    @PostMapping("/vectorize")
    public ResponseEntity<PostgresVectorStoreService.VectorizeResult> vectorize() {
        return ResponseEntity.ok(vectorStoreService.vectorizeAll());
    }

    @PostMapping("/vectorize/bitable")
    public ResponseEntity<PostgresVectorStoreService.VectorizeResult> vectorizeBitable(
            @RequestParam(required = false) String configId
    ) {
        return ResponseEntity.ok(vectorStoreService.vectorizeBitables(configId));
    }
}
