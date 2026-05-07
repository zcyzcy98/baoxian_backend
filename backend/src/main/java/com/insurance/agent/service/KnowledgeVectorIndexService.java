package com.insurance.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KnowledgeVectorIndexService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorIndexService.class);

    private static final Map<String, String> COLLECTION_TO_SHEET = Map.of(
            "insurance_products", "险种总览",
            "coverage_details", "保障责任详解",
            "insurance_tips", "投保注意事项",
            "faq_qa", "常见问题FAQ",
            "claim_cases", "理赔案例库",
            "compliance_words", "合规词库",
            "content_scenarios", "内容场景映射"
    );

    private static final Map<String, String> SHEET_TO_COLLECTION = Map.of(
            "险种总览", "insurance_products",
            "保障责任详解", "coverage_details",
            "投保注意事项", "insurance_tips",
            "常见问题FAQ", "faq_qa",
            "理赔案例库", "claim_cases",
            "合规词库", "compliance_words",
            "内容场景映射", "content_scenarios"
    );

    private final EmbeddingService embeddingService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<IndexSnapshot> snapshotRef = new AtomicReference<>(new IndexSnapshot(List.of(), Map.of(), "", 0L));
    private final DataFormatter formatter = new DataFormatter();

    @Value("${app.data.excel-path:}")
    private String excelPath;

    @Value("${vector.index.path:generated/vector-index.json}")
    private String vectorIndexPath;

    @Value("${vector.index.auto-build-on-startup:false}")
    private boolean autoBuildOnStartup;

    public KnowledgeVectorIndexService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        if (!autoBuildOnStartup) {
            log.info("跳过启动时自动构建向量索引，按需触发");
            return;
        }
        try {
            refreshIfNeeded();
        } catch (Exception e) {
            log.warn("启动时构建向量索引失败，服务继续启动: {}", e.getMessage());
        }
    }

    public synchronized void refreshIfNeeded() {
        Path excel = requireExcelPath();
        long lastModified = getLastModified(excel);
        if (!snapshotRef.get().documents().isEmpty() && snapshotRef.get().sourceLastModified() == lastModified) {
            return;
        }

        Path indexFile = Paths.get(vectorIndexPath);
        IndexSnapshot cached = readIndex(indexFile);
        String sourceHash = hashFile(excel);
        if (cached != null && Objects.equals(cached.sourceHash(), sourceHash)) {
            snapshotRef.set(cached);
            log.info("已加载本地向量索引，共 {} 条", cached.documents().size());
            return;
        }

        IndexSnapshot rebuilt = rebuildIndex(excel, sourceHash, lastModified);
        writeIndex(indexFile, rebuilt);
        snapshotRef.set(rebuilt);
        log.info("已重建向量索引，共 {} 条", rebuilt.documents().size());
    }

    public List<ScoredDocument> search(String query, String collection, int topK) {
        refreshIfNeeded();
        List<Double> queryVector = embeddingService.embed(query);
        IndexSnapshot snapshot = snapshotRef.get();
        List<ScoredDocument> scored = new ArrayList<>();
        for (KnowledgeDocument doc : snapshot.documents()) {
            if (collection != null && !collection.isBlank() && !collection.equals(doc.collectionName())) {
                continue;
            }
            double similarity = cosineSimilarity(queryVector, doc.embedding());
            scored.add(new ScoredDocument(doc, similarity));
        }
        scored.sort(Comparator.comparingDouble(ScoredDocument::similarity).reversed());
        return scored.stream().limit(Math.max(topK, 1)).toList();
    }

    public List<KnowledgeDocument> listByCollection(String collection) {
        refreshIfNeeded();
        if (collection == null || collection.isBlank()) {
            return snapshotRef.get().documents();
        }
        return snapshotRef.get().documents().stream()
                .filter(doc -> collection.equals(doc.collectionName()))
                .toList();
    }

    public Stats stats() {
        refreshIfNeeded();
        Map<String, Integer> counts = snapshotRef.get().collectionCounts();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        List<CollectionStat> collections = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CollectionStat(
                        COLLECTION_TO_SHEET.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getKey(),
                        entry.getValue()))
                .toList();
        return new Stats(total, collections);
    }

    private IndexSnapshot rebuildIndex(Path excel, String sourceHash, long lastModified) {
        List<KnowledgeDocument> docs = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();

        try (InputStream in = Files.newInputStream(excel); Workbook workbook = new XSSFWorkbook(in)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String collection = SHEET_TO_COLLECTION.get(sheet.getSheetName());
                if (collection == null) {
                    continue;
                }
                List<String> headers = readHeaders(sheet);
                for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null || isEmptyRow(row, headers.size())) {
                        continue;
                    }
                    Map<String, String> metadata = readMetadata(sheet.getSheetName(), headers, row);
                    String document = buildDocument(sheet.getSheetName(), metadata);
                    if (document.isBlank()) {
                        continue;
                    }
                    List<Double> embedding = embeddingService.embed(document);
                    String id = sheet.getSheetName() + "_" + (rowNum - 1);
                    docs.add(new KnowledgeDocument(
                            id,
                            document,
                            metadata,
                            collection,
                            sheet.getSheetName(),
                            embedding
                    ));
                    counts.merge(collection, 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取 Excel 失败: " + e.getMessage(), e);
        }

        return new IndexSnapshot(docs, counts, sourceHash, lastModified);
    }

    private List<String> readHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow == null) return headers;
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            headers.add(readCell(headerRow.getCell(i)));
        }
        return headers;
    }

    private Map<String, String> readMetadata(String sheetName, List<String> headers, Row row) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.isBlank()) {
                continue;
            }
            String value = readCell(row.getCell(i));
            if (value != null && !value.isBlank()) {
                metadata.put(header, value);
            }
        }
        metadata.put("sheet", sheetName);
        return metadata;
    }

    private String buildDocument(String sheetName, Map<String, String> metadata) {
        return switch (sheetName) {
            case "险种总览" -> joinFields(
                    metadata.get("险种全称"),
                    metadata.get("核心定义"),
                    "适用人群：" + blankToEmpty(metadata.get("适用人群")),
                    "核心价值：" + blankToEmpty(metadata.get("核心价值"))
            );
            case "保障责任详解" -> joinFields(
                    metadata.get("责任名称"),
                    metadata.get("责任说明"),
                    "赔付条件：" + blankToEmpty(metadata.get("赔付条件")),
                    "注意事项：" + blankToEmpty(metadata.get("注意事项"))
            );
            case "投保注意事项" -> joinFields(
                    metadata.get("要点"),
                    metadata.get("详细说明"),
                    "常见陷阱/误区：" + blankToEmpty(metadata.get("常见陷阱/误区"))
            );
            case "常见问题FAQ" -> joinFields(
                    "问题：" + blankToEmpty(metadata.get("常见问题")),
                    "解答：" + blankToEmpty(metadata.get("专业解答"))
            );
            case "理赔案例库" -> joinFields(
                    metadata.get("案例标题"),
                    "投保背景：" + blankToEmpty(metadata.get("投保背景")),
                    "出险经过：" + blankToEmpty(metadata.get("出险经过")),
                    "理赔过程：" + blankToEmpty(metadata.get("理赔过程")),
                    "赔付结果：" + blankToEmpty(metadata.get("赔付结果")),
                    "关键启示：" + blankToEmpty(metadata.get("关键启示"))
            );
            case "合规词库" -> joinFields(
                    "违规词：" + blankToEmpty(metadata.get("违规词/话术")),
                    "违规原因：" + blankToEmpty(metadata.get("违规原因")),
                    "合规替换建议：" + blankToEmpty(metadata.get("合规替换建议"))
            );
            case "内容场景映射" -> joinFields(
                    "场景：" + blankToEmpty(metadata.get("生活场景/时间节点")),
                    "描述：" + blankToEmpty(metadata.get("场景描述")),
                    "关联险种：" + blankToEmpty(metadata.get("关联险种")),
                    "推荐选题：" + blankToEmpty(metadata.get("推荐选题方向"))
            );
            default -> metadata.entrySet().stream()
                    .filter(entry -> !"sheet".equals(entry.getKey()))
                    .map(entry -> entry.getKey() + "：" + entry.getValue())
                    .reduce("", (left, right) -> joinFields(left, right));
        };
    }

    private String joinFields(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("。");
            }
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private boolean isEmptyRow(Row row, int cells) {
        for (int i = 0; i < cells; i++) {
            if (!readCell(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int len = Math.min(a.size(), b.size());
        for (int i = 0; i < len; i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Path requireExcelPath() {
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalStateException("未配置 app.data.excel-path，无法构建向量索引");
        }
        Path path = Paths.get(excelPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Excel 数据文件不存在: " + excelPath);
        }
        return path;
    }

    private long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new RuntimeException("读取文件时间失败: " + path, e);
        }
    }

    private String hashFile(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算文件 hash 失败: " + path, e);
        }
    }

    private IndexSnapshot readIndex(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            PersistedIndex persisted = mapper.readValue(in, PersistedIndex.class);
            if (persisted == null || persisted.documents == null) {
                return null;
            }
            return new IndexSnapshot(
                    persisted.documents,
                    persisted.collectionCounts == null ? Map.of() : persisted.collectionCounts,
                    persisted.sourceHash == null ? "" : persisted.sourceHash,
                    persisted.sourceLastModified
            );
        } catch (IOException e) {
            log.warn("读取本地向量索引失败，将重建: {}", e.getMessage());
            return null;
        }
    }

    private void writeIndex(Path path, IndexSnapshot snapshot) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            PersistedIndex persisted = new PersistedIndex();
            persisted.generatedAt = Instant.now().toString();
            persisted.sourceHash = snapshot.sourceHash();
            persisted.sourceLastModified = snapshot.sourceLastModified();
            persisted.collectionCounts = snapshot.collectionCounts();
            persisted.documents = snapshot.documents();
            try (OutputStream out = Files.newOutputStream(path)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, persisted);
            }
        } catch (IOException e) {
            throw new RuntimeException("保存向量索引失败: " + path, e);
        }
    }

    public record ScoredDocument(KnowledgeDocument document, double similarity) {}

    public record Stats(int total, List<CollectionStat> collections) {}

    public record CollectionStat(String sheetName, String collectionName, int count) {}

    public record IndexSnapshot(
            List<KnowledgeDocument> documents,
            Map<String, Integer> collectionCounts,
            String sourceHash,
            long sourceLastModified
    ) {}

    public record KnowledgeDocument(
            String id,
            String document,
            Map<String, String> metadata,
            String collectionName,
            String sheetName,
            List<Double> embedding
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersistedIndex {
        @JsonProperty("generated_at")
        public String generatedAt;

        @JsonProperty("source_hash")
        public String sourceHash;

        @JsonProperty("source_last_modified")
        public long sourceLastModified;

        @JsonProperty("collection_counts")
        public Map<String, Integer> collectionCounts;

        @JsonProperty("documents")
        public List<KnowledgeDocument> documents;
    }
}
