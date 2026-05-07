package com.insurance.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.BitableConfig;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PostgresVectorStoreService {
    private static final Logger log = LoggerFactory.getLogger(PostgresVectorStoreService.class);
    private static final String FEISHU_COLLECTION = "feishu_bitable";

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
    private final FeishuBitableService feishuBitableService;
    private final BitableConfigService bitableConfigService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${app.data.excel-path:}")
    private String excelPath;

    public PostgresVectorStoreService(EmbeddingService embeddingService,
                                      FeishuBitableService feishuBitableService,
                                      BitableConfigService bitableConfigService) {
        this.embeddingService = embeddingService;
        this.feishuBitableService = feishuBitableService;
        this.bitableConfigService = bitableConfigService;
    }

    @PostConstruct
    public void init() {
        ensureVectorTable();
    }

    public VectorizeResult vectorizeAll() {
        Path excel = requireExcelPath();
        List<TableVectorStat> stats = new ArrayList<>();
        int total = 0;

        try (Connection conn = openConnection();
             InputStream in = Files.newInputStream(excel);
             Workbook workbook = new XSSFWorkbook(in)) {
            conn.setAutoCommit(false);
            ensureVectorExtension(conn);

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String collection = SHEET_TO_COLLECTION.get(sheet.getSheetName());
                if (collection == null) {
                    continue;
                }
                String vectorTableName = vectorTableName(sheet.getSheetName());
                List<String> headers = readHeaders(sheet);
                ensureVectorTable(conn, vectorTableName);
                truncateVectorTable(conn, vectorTableName);
                int count = 0;
                for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null || ExcelSheetUtils.isEmptyRow(row, headers.size())) {
                        continue;
                    }
                    Map<String, String> metadata = ExcelSheetUtils.readMetadata(sheet.getSheetName(), headers, row);
                    String document = ExcelSheetUtils.buildDocument(sheet.getSheetName(), metadata);
                    if (document.isBlank()) {
                        continue;
                    }
                    List<Double> embedding = embeddingService.embed(document);
                    insertVectorRow(conn,
                            vectorTableName,
                            sheet.getSheetName() + "_" + rowNum,
                            collection,
                            sheet.getSheetName(),
                            document,
                            metadata,
                            embedding);
                    count++;
                    total++;
                }
                stats.add(new TableVectorStat(sheet.getSheetName(), collection, count));
                log.info("已向量化 sheet {} 共 {} 条", sheet.getSheetName(), count);
            }

            conn.commit();
            return new VectorizeResult(true, "向量化完成", total, stats);
        } catch (Exception e) {
            throw new RuntimeException("写入 PostgreSQL 向量表失败: " + e.getMessage(), e);
        }
    }

    public VectorizeResult vectorizeBitables(String configId) {
        List<BitableConfig> configs = resolveBitableConfigs(configId);
        List<TableVectorStat> stats = new ArrayList<>();
        int total = 0;

        try (Connection conn = openConnection()) {
            conn.setAutoCommit(false);
            ensureVectorExtension(conn);
            if (configId == null || configId.isBlank()) {
                for (BitableConfig cfg : configs) {
                    ensureVectorTable(conn, vectorTableName(resolveBitableTableName(cfg)));
                    truncateVectorTable(conn, vectorTableName(resolveBitableTableName(cfg)));
                }
            }

            for (BitableConfig cfg : configs) {
                if (!isVectorizableBitable(cfg)) {
                    continue;
                }
                String sourceTableName = resolveBitableTableName(cfg);
                String vectorTableName = vectorTableName(sourceTableName);
                ensureVectorTable(conn, vectorTableName);
                if (configId != null && !configId.isBlank()) {
                    deleteBitableConfigRows(conn, vectorTableName, cfg.getId());
                }

                String tableId = resolveBitableTableId(cfg);
                Map<String, Object> data = feishuBitableService.getAllRecords(null, cfg.getAppToken(), tableId);
                if (!Boolean.TRUE.equals(data.get("success"))) {
                    throw new RuntimeException("读取飞书多维表格失败(" + cfg.getName() + "): " + data.get("error"));
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                if (records == null) {
                    records = List.of();
                }

                int count = 0;
                for (Map<String, Object> record : records) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fields = (Map<String, Object>) record.get("fields");
                    String recordId = stringValue(record.get("recordId"));
                    Map<String, String> metadata = buildBitableMetadata(cfg, tableId, recordId, fields);
                    String document = buildBitableDocument(cfg, fields);
                    if (document.isBlank()) {
                        continue;
                    }
                    List<Double> embedding = embeddingService.embed(document);
                    insertVectorRow(conn,
                            vectorTableName,
                            "feishu_" + safeId(cfg.getId()) + "_" + safeId(recordId),
                            FEISHU_COLLECTION,
                            sourceTableName,
                            document,
                            metadata,
                            embedding);
                    count++;
                    total++;
                }
                stats.add(new TableVectorStat(sourceTableName, FEISHU_COLLECTION, count));
                log.info("已向量化飞书多维表格 {} 共 {} 条", sourceTableName, count);
            }

            conn.commit();
            return new VectorizeResult(true, "飞书多维表格向量化完成", total, stats);
        } catch (Exception e) {
            throw new RuntimeException("写入飞书多维表格向量失败: " + e.getMessage(), e);
        }
    }

    public List<VectorHit> searchByTableName(String tableName, String query, int topK) {
        try (Connection conn = openConnection()) {
            List<Double> queryVector = embeddingService.embed(query);
            String vectorLiteral = toVectorLiteral(queryVector);
            return searchSingleTable(conn, tableName, vectorLiteral, topK);
        } catch (Exception e) {
            throw new RuntimeException("向量搜索失败(" + tableName + "): " + e.getMessage(), e);
        }
    }

    public List<VectorHit> search(String query, String collection, int topK) {
        try (Connection conn = openConnection()) {
            ensureVectorExtension(conn);
            List<Double> queryVector = embeddingService.embed(query);
            String vectorLiteral = toVectorLiteral(queryVector);
            if (collection != null && !collection.isBlank()) {
                if (FEISHU_COLLECTION.equals(collection)) {
                    List<VectorHit> feishuHits = new ArrayList<>();
                    for (BitableConfig cfg : resolveBitableConfigs(null)) {
                        feishuHits.addAll(searchSingleTable(conn, vectorTableName(resolveBitableTableName(cfg)), vectorLiteral, topK));
                    }
                    feishuHits.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
                    return feishuHits.stream().limit(Math.max(topK, 1)).toList();
                }
                return searchSingleTable(conn, vectorTableNameByCollection(collection), vectorLiteral, topK);
            }

            List<VectorHit> allHits = new ArrayList<>();
            for (String sheetName : SHEET_TO_COLLECTION.keySet()) {
                allHits.addAll(searchSingleTable(conn, vectorTableName(sheetName), vectorLiteral, topK));
            }
            for (BitableConfig cfg : resolveBitableConfigs(null)) {
                allHits.addAll(searchSingleTable(conn, vectorTableName(resolveBitableTableName(cfg)), vectorLiteral, topK));
            }
            allHits.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
            return allHits.stream().limit(Math.max(topK, 1)).toList();
        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL 向量搜索失败: " + e.getMessage(), e);
        }
    }

    public VectorStats stats() {
        try (Connection conn = openConnection()) {
            List<CollectionInfo> collections = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, String> entry : SHEET_TO_COLLECTION.entrySet()) {
                String sheetName = entry.getKey();
                String collection = entry.getValue();
                int cnt = countTableRows(conn, vectorTableName(sheetName));
                total += cnt;
                collections.add(new CollectionInfo(sheetName, collection, cnt));
            }
            for (BitableConfig cfg : resolveBitableConfigs(null)) {
                String sourceTableName = resolveBitableTableName(cfg);
                int feishuCount = countTableRows(conn, vectorTableName(sourceTableName));
                total += feishuCount;
                collections.add(new CollectionInfo(sourceTableName, FEISHU_COLLECTION, feishuCount));
            }
            return new VectorStats(total, collections);
        } catch (Exception e) {
            throw new RuntimeException("读取向量统计失败: " + e.getMessage(), e);
        }
    }

    private void ensureVectorTable() {
        try (Connection conn = openConnection()) {
            ensureVectorExtension(conn);
            for (String sheetName : SHEET_TO_COLLECTION.keySet()) {
                ensureVectorTable(conn, vectorTableName(sheetName));
            }
            for (BitableConfig cfg : resolveBitableConfigs(null)) {
                ensureVectorTable(conn, vectorTableName(resolveBitableTableName(cfg)));
            }
        } catch (Exception e) {
            log.warn("初始化 PostgreSQL 向量表失败，服务继续启动: {}", e.getMessage());
        }
    }

    private void ensureVectorExtension(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
    }

    private void ensureVectorTable(Connection conn, String tableName) throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS " + quoteIdentifier(tableName) + " (" +
                "id TEXT PRIMARY KEY," +
                "collection_name TEXT NOT NULL," +
                "sheet_name TEXT NOT NULL," +
                "document TEXT NOT NULL," +
                "metadata JSONB NOT NULL," +
                "embedding vector(1024) NOT NULL" +
                ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void truncateVectorTable(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + quoteIdentifier(tableName));
        }
    }

    private void insertVectorRow(Connection conn,
                                 String tableName,
                                 String id,
                                 String collection,
                                 String sheetName,
                                 String document,
                                 Map<String, String> metadata,
                                 List<Double> embedding) throws Exception {
        String sql = "INSERT INTO " + quoteIdentifier(tableName) +
                " (id, collection_name, sheet_name, document, metadata, embedding) VALUES (?, ?, ?, ?, ?::jsonb, ?::vector)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, collection);
            ps.setString(3, sheetName);
            ps.setString(4, document);
            ps.setString(5, mapper.writeValueAsString(metadata));
            ps.setString(6, toVectorLiteral(embedding));
            ps.executeUpdate();
        }
    }

    private void deleteBitableConfigRows(Connection conn, String tableName, String configId) throws Exception {
        String sql = "DELETE FROM " + quoteIdentifier(tableName) + " WHERE metadata->>'config_id' = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, configId);
            ps.executeUpdate();
        }
    }

    private List<String> readHeaders(Sheet sheet) {
        return ExcelSheetUtils.readHeaders(sheet);
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private Path requireExcelPath() {
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalStateException("未配置 app.data.excel-path");
        }
        Path path = Paths.get(excelPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Excel 文件不存在: " + excelPath);
        }
        return path;
    }

    private String quoteIdentifier(String raw) {
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private String vectorTableName(String sheetName) {
        return sheetName + "_向量";
    }

    private String vectorTableNameByCollection(String collection) {
        for (Map.Entry<String, String> entry : SHEET_TO_COLLECTION.entrySet()) {
            if (entry.getValue().equals(collection)) {
                return vectorTableName(entry.getKey());
            }
        }
        if (FEISHU_COLLECTION.equals(collection)) {
            return vectorTableName(resolveDefaultBitableTableName());
        }
        throw new IllegalArgumentException("未知 collection: " + collection);
    }

    private List<BitableConfig> resolveBitableConfigs(String configId) {
        if (configId != null && !configId.isBlank()) {
            BitableConfig cfg = bitableConfigService.getConfig(configId);
            if (cfg == null) {
                throw new IllegalArgumentException("未找到飞书多维表格配置: " + configId);
            }
            return List.of(cfg);
        }
        return bitableConfigService.getAllConfigs().stream()
                .filter(this::isVectorizableBitable)
                .toList();
    }

    private boolean isVectorizableBitable(BitableConfig cfg) {
        return cfg != null
                && cfg.isActive()
                && cfg.getAppToken() != null && !cfg.getAppToken().isBlank()
                && ((cfg.getTableId() != null && !cfg.getTableId().isBlank())
                    || (cfg.getSourceTableName() != null && !cfg.getSourceTableName().isBlank()));
    }

    private String resolveDefaultBitableTableName() {
        return resolveBitableConfigs(null).stream()
                .findFirst()
                .map(this::resolveBitableTableName)
                .orElse("飞书多维表格");
    }

    private String resolveBitableTableName(BitableConfig cfg) {
        if (cfg.getSourceTableName() != null && !cfg.getSourceTableName().isBlank()) {
            return cfg.getSourceTableName().trim();
        }
        if (cfg.getName() != null && !cfg.getName().isBlank()) {
            return cfg.getName().trim();
        }
        return "飞书多维表格";
    }

    private String resolveBitableTableId(BitableConfig cfg) {
        if (cfg.getTableId() != null && !cfg.getTableId().isBlank()) {
            return cfg.getTableId();
        }
        String targetTableName = resolveBitableTableName(cfg);
        return feishuBitableService.getTables(null, cfg.getAppToken()).stream()
                .filter(table -> table.getName() != null && table.getName().trim().equals(targetTableName))
                .map(FeishuBitableService.TableInfo::getTableId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未在飞书 Base 中找到表: " + targetTableName + "，请检查 sourceTableName 或配置 tableId"));
    }

    private Map<String, String> buildBitableMetadata(BitableConfig cfg, String tableId, String recordId, Map<String, Object> fields) {
        java.util.LinkedHashMap<String, String> metadata = new java.util.LinkedHashMap<>();
        metadata.put("source", "feishu_bitable");
        metadata.put("config_id", nullToEmpty(cfg.getId()));
        metadata.put("config_name", nullToEmpty(cfg.getName()));
        metadata.put("app_token", nullToEmpty(cfg.getAppToken()));
        metadata.put("table_id", nullToEmpty(tableId));
        metadata.put("source_table_name", resolveBitableTableName(cfg));
        metadata.put("record_id", nullToEmpty(recordId));
        metadata.put("kind", nullToEmpty(cfg.getKind()));
        metadata.put("category", nullToEmpty(cfg.getCategory()));
        if (fields != null) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String value = stringifyFieldValue(entry.getValue());
                if (!value.isBlank()) {
                    metadata.put("field_" + entry.getKey(), value);
                }
            }
        }
        return metadata;
    }

    private String buildBitableDocument(BitableConfig cfg, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (cfg.getName() != null && !cfg.getName().isBlank()) {
            sb.append("飞书表：").append(cfg.getName()).append("。");
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String value = stringifyFieldValue(entry.getValue());
            if (!value.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("。");
                }
                sb.append(entry.getKey()).append("：").append(value);
            }
        }
        return sb.toString();
    }

    private String stringifyFieldValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::stringValue)
                    .filter(s -> !s.isBlank())
                    .reduce("", (left, right) -> left.isBlank() ? right : left + "、" + right);
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String safeId(String value) {
        String raw = value == null || value.isBlank() ? "unknown" : value;
        return raw.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private List<VectorHit> searchSingleTable(Connection conn, String tableName, String vectorLiteral, int topK) throws Exception {
        String sql = "SELECT id, collection_name, sheet_name, document, metadata, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM " + quoteIdentifier(tableName) + " " +
                "ORDER BY embedding <=> ?::vector LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorLiteral);
            ps.setString(2, vectorLiteral);
            ps.setInt(3, Math.max(topK, 1));
            List<VectorHit> hits = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new VectorHit(
                            rs.getString("id"),
                            rs.getString("document"),
                            readMetadataJson(rs.getString("metadata")),
                            rs.getString("collection_name"),
                            rs.getString("sheet_name"),
                            1 - rs.getDouble("similarity"),
                            rs.getDouble("similarity")
                    ));
                }
            }
            return hits;
        }
    }

    private int countTableRows(Connection conn, String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(tableName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String toVectorLiteral(List<Double> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, String> readMetadataJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return mapper.readValue(json, new TypeReference<>() {});
    }

    public record VectorizeResult(boolean success, String message, int total, List<TableVectorStat> tables) {}

    public record TableVectorStat(String sheetName, String collectionName, int rowCount) {}

    public record VectorHit(
            String id,
            String document,
            Map<String, String> metadata,
            String collectionName,
            String sheetName,
            double distance,
            double similarity
    ) {}

    public record VectorStats(int total, List<CollectionInfo> collections) {}

    public record CollectionInfo(String sheetName, String collectionName, int count) {}
}
