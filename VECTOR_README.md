# 保险知识数据与向量检索

## 系统架构

```
Excel 数据 → Java 导入 PostgreSQL 原始表
Excel 数据 → Java 分 sheet 向量化 → PostgreSQL 分表向量库 → Java API
```

## 启动步骤

### 1. 配置 Excel 路径、数据库、Embedding Key

```bash
export EXCEL_DATA_PATH="/Users/yang2krown/Desktop/保险 Agent/保险行业知识图谱.xlsx"
export JDBC_DATABASE_URL="jdbc:postgresql://localhost:54328/postgres"
export JDBC_DATABASE_USERNAME="root"
export JDBC_DATABASE_PASSWORD="e65K4t7z1"
export EMBEDDING_API_KEY="你的 DashScope Key"
```

### 2. 启动 Java 后端

在 IDEA 中运行 `AgentApplication.java`，服务将在 `http://localhost:8888` 启动

首次启动会自动：
- 提供 Excel 原始数据导入 PostgreSQL 的接口
- 提供向量检索接口
- 向量数据按 sheet 分别写入 PostgreSQL

## API 使用

### 导入 Excel 到 PostgreSQL

```bash
curl -X POST http://localhost:8888/api/db/import-excel
```

导入后会创建 7 张表，表名与 Excel sheet 名保持一致：

- `险种总览`
- `保障责任详解`
- `投保注意事项`
- `常见问题FAQ`
- `理赔案例库`
- `合规词库`
- `内容场景映射`

### 分 sheet 向量化写入 PostgreSQL

```bash
curl -X POST http://localhost:8888/api/vector/vectorize
```

向量化后会创建 7 张对应的向量表：

- `险种总览_向量`
- `保障责任详解_向量`
- `投保注意事项_向量`
- `常见问题FAQ_向量`
- `理赔案例库_向量`
- `合规词库_向量`
- `内容场景映射_向量`

### 查看统计

```bash
curl http://localhost:8888/api/vector/stats
```

### 搜索

```bash
# 搜索所有集合
curl "http://localhost:8888/api/vector/search?query=重疾险怎么买&top_k=3"

# 搜索指定集合
curl "http://localhost:8888/api/vector/search?query=医疗险&collection=faq_qa&top_k=3"
```

## 数据集合对应关系

| Excel Sheet | Collection Name | 数量 |
|-------------|-----------------|------|
| 险种总览 | insurance_products | 15 |
| 保障责任详解 | coverage_details | 56 |
| 投保注意事项 | insurance_tips | 73 |
| 常见问题FAQ | faq_qa | 335 |
| 理赔案例库 | claim_cases | 270 |
| 合规词库 | compliance_words | 89 |
| 内容场景映射 | content_scenarios | 34 |

## 技术栈

- **原始数据存储**: PostgreSQL
- **向量模型**: 阿里云 DashScope text-embedding-v3
- **向量存储**: PostgreSQL `pgvector`（按 sheet 分表）
- **后端**: Spring Boot (Java)
