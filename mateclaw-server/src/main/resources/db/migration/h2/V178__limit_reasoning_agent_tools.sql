-- V121: Limit tool bindings for 推理分析师 (id=1000000003)
--
-- Without explicit bindings the agent inherits ALL enabled tools (~80+),
-- causing the full tool-schema payload to exceed provider token limits.
-- Binding 6 core tool beans restricts the agent to ~15 functions instead.

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002100, 1000000003, 'dateTimeTool',          TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002101, 1000000003, 'webSearchTool',         TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002102, 1000000003, 'workspaceMemoryTool',   TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002103, 1000000003, 'wikiTool',              TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002104, 1000000003, 'readFileTool',          TRUE, NOW(), NOW(), 0);

MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id) VALUES (1000002105, 1000000003, 'documentExtractTool',   TRUE, NOW(), NOW(), 0);
