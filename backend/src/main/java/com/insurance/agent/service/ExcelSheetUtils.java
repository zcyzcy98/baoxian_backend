package com.insurance.agent.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ExcelSheetUtils {
    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelSheetUtils() {}

    static List<String> readHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        List<String> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = readCell(headerRow.getCell(i));
            if (!header.isBlank()) {
                headers.add(header);
            }
        }
        return headers;
    }

    static Map<String, String> readMetadata(String sheetName, List<String> headers, Row row) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header == null || header.isBlank()) continue;
            String value = readCell(row.getCell(i));
            if (!value.isBlank()) {
                metadata.put(header, value);
            }
        }
        metadata.put("sheet", sheetName);
        return metadata;
    }

    static boolean isEmptyRow(Row row, int cells) {
        for (int i = 0; i < cells; i++) {
            if (!readCell(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    static String buildDocument(String sheetName, Map<String, String> metadata) {
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
                    .reduce("", ExcelSheetUtils::joinFields);
        };
    }

    private static String readCell(Cell cell) {
        return cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String joinFields(String left, String right) {
        if (left == null || left.isBlank()) return right == null ? "" : right;
        if (right == null || right.isBlank()) return left;
        return left + "。" + right;
    }

    private static String joinFields(String... parts) {
        String current = "";
        for (String part : parts) {
            current = joinFields(current, part);
        }
        return current;
    }
}
