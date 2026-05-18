-- V116: 种子「数据分析专家」数字员工
--
-- Agent ID 1000000020，绑定 analytics_* 5 个工具。
-- data-analyst SKILL.md 绑定由 DataAnalystAgentSeedService（Order 130）在 Flyway 之后完成。

INSERT INTO mate_agent (id, name, description, agent_type, system_prompt, model_name, max_iterations, enabled, icon, tags, workspace_id, create_time, update_time, deleted)
VALUES (
    1000000020,
    '数据分析专家',
    '上传 Excel 数据后通过对话进行汇总、画像、出图、校验、导出',
    'react',
    '你是「数据分析专家」，负责帮助调查员、辅调员对企业季报、住户问卷等结构化数据做分析。

【核心工作方式】
1. 用户提问后，第一件事必须调用 analytics_schema 了解当前 workspace 有哪些数据集和字段语义。
2. 然后用 analytics_query 跑 SQL。SQL 必须基于 schema 给出的物理列名 field_code。SQL 必带 WHERE dataset_id = ? 过滤。
3. 需要列分布时调 analytics_profile；需要可视化时调 analytics_chart；用户要下载结果时调 analytics_export。
4. 输出报告格式：先结论（一段话），再支撑数据（表格/图），最后给 1-3 条建议或追问。

【绝对禁止】
- 不要试图执行写操作 SQL（INSERT/UPDATE/DELETE/DROP），SqlGuard 会拦截，浪费往返
- 不要凭空编造字段名——必须以 analytics_schema 返回的为准
- 字段口径不清时（如"期末存栏"指什么时点）主动问用户

【输出规范】
- 数字带单位（来自字段 unit）
- 比较用百分比时保留两位小数
- 表格用 markdown，长表格只展示前 10 行 + 总数提示',
    'qwen-plus',
    20,
    TRUE,
    'mdi-chart-bar',
    '数据分析,Excel,数字员工',
    1,
    NOW(), NOW(), 0
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    system_prompt = VALUES(system_prompt),
    update_time = NOW();

-- 工具绑定 (IDs 1000000021-1000000025)
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000021, 1000000020, 'analyticsSchema', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();

INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000022, 1000000020, 'analyticsQuery', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();

INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000023, 1000000020, 'analyticsProfile', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();

INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000024, 1000000020, 'analyticsChart', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();

INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000025, 1000000020, 'analyticsExport', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE enabled = TRUE, deleted = 0, update_time = NOW();
