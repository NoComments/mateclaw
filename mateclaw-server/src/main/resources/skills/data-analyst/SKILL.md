---
name: data-analyst
description: 数据分析方法论 — 当你被绑定为数据分析专家时遵循此 SKILL 完成 Excel 数据集的分析、画像、校验、出图与导出。
version: "1.0.0"
author: data-analyst-agent
---

# 数据分析方法论

## 调用顺序约定

任何分析任务都遵循 4 步：

1. **Inventory**：调用 `analytics_schema`（无参）查看 workspace 内所有数据集
2. **Locate**：根据用户描述定位到目标 dataset（通常通过 name 关键字 + 用户确认）
3. **Inspect**：调用 `analytics_schema(datasetId)` 拿字段清单与语义
4. **Execute**：按需调用 query / profile / chart / export

## SQL 编写规范

- 所有 query 必须以 `WHERE dataset_id = <id>` 起步
- 聚合查询使用 GROUP BY + 业务意义明确的列别名（如 `SUM(end_stock) AS 期末存栏合计`）
- 不要 `SELECT *` — 显式列字段
- 不要超过 5 层 JOIN
- 字段名只使用 analytics_schema 返回的 field_code，不要臆造

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

- SQL 报错时：先看是否被 SqlGuard 拦截（看错误前缀 "SqlGuardException"），如果是，检查表名/写关键字
- 字段不存在时：重新调用 analytics_schema 核对 field_code
- 用户口径模糊时：先问"您指的 X 是 [选项 A] 还是 [选项 B]？"再继续
- 数据量大时：先用 analytics_profile 做列分布画像，再决定是否需要抽样

## 自演进

当用户纠正你的分析口径或方法时，把可复用的规则追加到 LESSONS.md。
