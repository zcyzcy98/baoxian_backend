package com.insurance.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BitableConfig {
    private String id;
    private String name;
    private String description;
    private String appToken;
    private String tableId;
    private String sourceTableName;
    private String category;
    private boolean active;

    /**
     * 表的"角色"。决定怎么从这张表里抽取选题。
     * 取值见 BitableTopicReader 里的 KIND_* 常量:
     *   HOT_NOTE  - 爆款笔记素材库 (标题/正文/点赞数/...)
     *   TEMPLATE  - 抽象的标题/正文模板
     *   CASE      - 理赔/成功案例
     *   FAQ       - 客户咨询/常见问题
     *   PRODUCT   - 保险产品信息 (一般不进选题广场, 但可被引用)
     *   CUSTOM    - 自定义, 走 generic 解析
     */
    private String kind;

    /**
     * 是否在"选题广场"中展示该表的内容（默认 true）。
     * 设为 false 可让此表仅供其他功能（如视频创作、向量检索）使用，
     * 不出现在 /daily 推荐列表里。
     */
    private boolean showInTopicSquare = true;

    /**
     * 字段映射: 标准语义名 (英文) → 你飞书表里实际的列名 (中文).
     * 例如 {"title": "标题", "likes": "点赞数"}
     * 没填的字段会用 BitableTopicReader.DEFAULT_FIELD_MAPS 里的默认值.
     */
    private Map<String, String> fieldMap;

    public BitableConfig() {}

    public BitableConfig(String id, String name, String description,
                          String appToken, String tableId, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.appToken = appToken;
        this.tableId = tableId;
        this.category = category;
        this.active = true;
    }

    public BitableConfig(String id, String name, String description,
                          String appToken, String tableId, String category,
                          String kind, Map<String, String> fieldMap) {
        this(id, name, description, appToken, tableId, category);
        this.kind = kind;
        this.fieldMap = fieldMap;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAppToken() { return appToken; }
    public void setAppToken(String appToken) { this.appToken = appToken; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getSourceTableName() { return sourceTableName; }
    public void setSourceTableName(String sourceTableName) { this.sourceTableName = sourceTableName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public boolean isShowInTopicSquare() { return showInTopicSquare; }
    public void setShowInTopicSquare(boolean showInTopicSquare) { this.showInTopicSquare = showInTopicSquare; }

    public Map<String, String> getFieldMap() { return fieldMap; }
    public void setFieldMap(Map<String, String> fieldMap) { this.fieldMap = fieldMap; }

    /** 拿这个表的"标准语义名 → 实际列名"映射, 优先用本配置, fallback 到默认. */
    public Map<String, String> resolveFieldMap(Map<String, String> defaults) {
        Map<String, String> merged = new HashMap<>();
        if (defaults != null) merged.putAll(defaults);
        if (fieldMap != null) merged.putAll(fieldMap); // 用户配置覆盖默认
        return merged;
    }
}
