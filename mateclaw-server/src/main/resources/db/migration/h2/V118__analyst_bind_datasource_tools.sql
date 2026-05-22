-- V118: Bind external datasource tools to Data Analyst agent
--
-- The Data Analyst (id=1000000020) can now discover and query both
-- local dataset_* tables AND external databases configured in Settings.

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id)
VALUES (1000000027, 1000000020, 'query_datasource', TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id)
VALUES (1000000028, 1000000020, 'execute_sql', TRUE, NOW(), NOW(), 0);

-- Update agent description to reflect unified capability
UPDATE mate_agent
SET description = '分析上传的 Excel 数据集和外部数据库，支持汇总、画像、出图、校验、导出',
    update_time = NOW()
WHERE id = 1000000020;
