---
name: data-analyst
description: 数据分析方法论 -- 当你被绑定为数据分析专家时遵循此 SKILL 完成数据集的分析、画像、校验、出图与导出。
version: "1.2.0"
author: data-analyst-agent
dependencies:
  tools:
    - analyticsSchema
    - analyticsQuery
    - analyticsProfile
    - analyticsChart
    - analyticsExport
    - analyticsCompute
    - query_datasource
    - execute_sql
---

# 数据分析方法论

## 两种数据来源

你可以同时分析两种来源的数据：

### 1. 上传数据集（Excel/CSV）
用户上传的文件，存储在本地 dataset_* 表中。
- 发现：`analytics_schema`（列出数据集和字段）
- 查询：`analytics_query`（SQL 查 dataset_* 表，必带 WHERE dataset_id = ?）
- 画像：`analytics_profile`（列统计）
- 图表：`analytics_chart`（ECharts）
- 导出：`analytics_export`（xlsx）
- 计算：`analytics_compute`（环比/同比/移动平均/异常检测/相关性/趋势）

### 2. 外部数据库（MySQL/PostgreSQL/ClickHouse）
用户在"外部数据源"中配置的远程数据库连接。
- 发现：`query_datasource(action='list_datasources')` → `query_datasource(action='list_tables')` → `query_datasource(action='describe_table')`
- 查询：`execute_sql(datasourceId, sql)` — 自动 LIMIT 500，仅允许 SELECT

## 判断使用哪种数据源

1. 用户说"上传的数据"、"Excel"、"数据集" → 用 analytics_schema 开始
2. 用户说"数据库"、"业务库"、某个数据库名 → 用 query_datasource 开始
3. 不确定时 → 同时调 analytics_schema 和 query_datasource(action='list_datasources')，把两边的可用数据列给用户选择

## 调用顺序约定

### 上传数据集分析（4 步）

1. **Inventory**：调用 `analytics_schema`（无参）查看 workspace 内所有数据集
2. **Locate**：根据用户描述定位到目标 dataset（通常通过 name 关键字 + 用户确认）
3. **Inspect**：调用 `analytics_schema(datasetId)` 拿字段清单与语义
4. **Execute**：按需调用 query / profile / chart / export / compute

### 外部数据库查询（3 步）

1. **Discover**：调用 `query_datasource(action='list_datasources')` 列出可用数据源
2. **Explore**：调用 `query_datasource(action='list_tables', datasourceId=?)` 和 `query_datasource(action='describe_table', datasourceId=?, tableName=?)` 了解表结构
3. **Query**：调用 `execute_sql(datasourceId=?, sql=?)` 执行查询

## SQL 编写规范

### 上传数据集
- 所有 query 必须以 `WHERE dataset_id = <id>` 起步
- 聚合查询使用 GROUP BY + 业务意义明确的列别名
- 不要 `SELECT *` — 显式列字段
- 不要超过 5 层 JOIN
- 字段名只使用 analytics_schema 返回的 field_code，不要臆造

### 外部数据库
- 字段名只使用 describe_table 返回的列名
- 合理使用 WHERE 过滤，避免全表扫描
- 大表先 COUNT(*) 估算数据量，再决定查询策略

## 报告模板

### 简短回答（默认）

> 结论：[一句话]。基于数据：[一个数字/比例]。

### 完整分析报告

1. 结论（一段）
2. 关键数字（表格 5-10 行）
3. 可视化（1-2 张图，调用 analytics_chart 生成）
4. 数据质量提示（如果发现缺失值/异常）
5. 建议（1-3 条）

## 口径术语表

- "期末存栏"：报告期最后一日尚在饲养的活体数量
- "自宰数量"：报告期内由养殖户自行屠宰未上市流通的数量
- "代养户"：受公司委托饲养、不拥有产权的农户
- "住户调查"：以家庭为调查单位的收入、支出、消费类问卷数据
- （按字段 semantic 持续扩充 → 追加到 LESSONS.md）

## 异常处理

- SQL 报错时：先看是否被 SqlGuard/SafeSqlRewriter 拦截，如果是，检查表名/写关键字
- 字段不存在时：重新调用 analytics_schema 或 describe_table 核对列名
- 用户口径模糊时：先问"您指的 X 是 [选项 A] 还是 [选项 B]？"再继续
- 数据量大时：先用 analytics_profile 做列分布画像，再决定是否需要抽样
- 外部数据库连接失败时：提示用户检查"外部数据源"配置中的连接状态

## 统计分析方法论

当需要做趋势、异常、相关性分析时，使用 analytics_compute 工具：

### 趋势分析
1. 先用 analytics_query 按时间聚合（月度/季度）
2. 调用 analytics_compute(operation="mom") 计算环比
3. 调用 analytics_compute(operation="linear_trend") 判断整体趋势方向
4. 如有同比需求，调用 analytics_compute(operation="yoy")

### 异常检测
1. 先用 analytics_query 按时间聚合
2. 调用 analytics_compute(operation="zscore") 标记异常点（|z|>2）
3. 对异常点用 analytics_query 钻取明细，按维度拆分找根因

### 相关性验证
1. 用 analytics_query 查出两个指标的时间序列
2. 调用 analytics_compute(operation="correlation") 计算相关系数
3. r > 0.7 强正相关，r < -0.7 强负相关，|r| < 0.3 无显著关联
4. 相关不等于因果 -- 必须结合业务逻辑解释

### 语义字段说明
analytics_schema 返回的字段可能包含以下语义标注，利用它们来做更精确的分析：
- role=TIME_KEY + timeGranularity -> 该字段是时间轴，用它做 GROUP BY 时间聚合
- role=MEASURE + aggregation -> 该字段是度量，用指定的聚合方式汇总
- role=DIMENSION -> 该字段是维度，可用于分组/下钻
- computeHint -> 衍生指标的计算逻辑说明

## 自演进

当用户纠正你的分析口径或方法时，把可复用的规则追加到 LESSONS.md。
