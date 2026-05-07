package com.insurance.agent.service;

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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExcelPostgresImportService {
    private static final Logger log = LoggerFactory.getLogger(ExcelPostgresImportService.class);

    @Value("${app.data.excel-path:}")
    private String excelPath;

    @Value("${postgres.import.enabled:true}")
    private boolean importEnabled;

    @Value("${postgres.import.auto-run:false}")
    private boolean autoRun;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @PostConstruct
    public void maybeAutoRun() {
        if (!importEnabled || !autoRun) {
            return;
        }
        importExcelToPostgres();
    }

    public ImportResult importExcelToPostgres() {
        if (!importEnabled) {
            return new ImportResult(false, "postgres.import.enabled=false，已跳过导入", List.of());
        }
        Path excel = requireExcelPath();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new ImportResult(false, "未配置 spring.datasource.url", List.of());
        }

        List<TableImportStat> stats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             InputStream in = Files.newInputStream(excel);
             Workbook workbook = new XSSFWorkbook(in)) {

            conn.setAutoCommit(false);
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String tableName = quoteIdentifier(sheet.getSheetName());
                List<String> headers = ExcelSheetUtils.readHeaders(sheet);
                if (headers.isEmpty()) {
                    continue;
                }
                createTable(conn, tableName, headers);
                truncateTable(conn, tableName);
                int inserted = insertRows(conn, sheet, tableName, headers);
                stats.add(new TableImportStat(sheet.getSheetName(), inserted));
                log.info("已导入 sheet {} -> table {} 共 {} 行", sheet.getSheetName(), sheet.getSheetName(), inserted);
            }
            conn.commit();
            return new ImportResult(true, "导入完成", stats);
        } catch (Exception e) {
            throw new RuntimeException("导入 Excel 到 PostgreSQL 失败: " + e.getMessage(), e);
        }
    }

    private void createTable(Connection conn, String tableName, List<String> headers) throws Exception {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        ddl.append("\"_row_id\" INTEGER PRIMARY KEY");
        for (String header : headers) {
            ddl.append(", ").append(quoteIdentifier(header)).append(" TEXT");
        }
        ddl.append(")");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }
    }

    private void truncateTable(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + tableName);
        }
    }

    private int insertRows(Connection conn, Sheet sheet, String tableName, List<String> headers) throws Exception {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (\"_row_id\"");
        for (String header : headers) {
            sql.append(", ").append(quoteIdentifier(header));
        }
        sql.append(") VALUES (?"); // _row_id
        for (int i = 0; i < headers.size(); i++) {
            sql.append(", ?");
        }
        sql.append(")");

        int inserted = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || ExcelSheetUtils.isEmptyRow(row, headers.size())) {
                    continue;
                }
                Map<String, String> metadata = ExcelSheetUtils.readMetadata(sheet.getSheetName(), headers, row);
                ps.setInt(1, rowNum);
                for (int i = 0; i < headers.size(); i++) {
                    ps.setString(i + 2, metadata.getOrDefault(headers.get(i), ""));
                }
                ps.addBatch();
                inserted++;
            }
            ps.executeBatch();
        }
        return inserted;
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

    public record ImportResult(boolean success, String message, List<TableImportStat> tables) {}

    public record TableImportStat(String tableName, int rowCount) {}
}
