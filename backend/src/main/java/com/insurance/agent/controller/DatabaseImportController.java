package com.insurance.agent.controller;

import com.insurance.agent.service.ExcelPostgresImportService;
import com.insurance.agent.service.ExcelPostgresImportService.ImportResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/db")
public class DatabaseImportController {
    private final ExcelPostgresImportService importService;

    public DatabaseImportController(ExcelPostgresImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import-excel")
    public ResponseEntity<ImportResult> importExcel() {
        return ResponseEntity.ok(importService.importExcelToPostgres());
    }
}
