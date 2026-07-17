-- V122: Limit tool bindings for 通用助手 (id=1000000001) and 任务规划师 (id=1000000002)

-- ======== 通用助手 (id=1000000001) ========
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002110, 1000000001, 'dateTimeTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002111, 1000000001, 'webSearchTool',       TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002112, 1000000001, 'workspaceMemoryTool', TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002113, 1000000001, 'wikiTool',            TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002114, 1000000001, 'readFileTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002115, 1000000001, 'writeFileTool',       TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002116, 1000000001, 'editFileTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002117, 1000000001, 'documentExtractTool', TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002118, 1000000001, 'delegateAgentTool',   TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002119, 1000000001, 'imageGenerateTool',   TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002120, 1000000001, 'docxRenderTool',      TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002121, 1000000001, 'xlsxRenderTool',      TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002122, 1000000001, 'pptxRenderTool',      TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002123, 1000000001, 'pdfRenderTool',       TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);

-- ======== 任务规划师 (id=1000000002) ========
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002130, 1000000002, 'dateTimeTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002131, 1000000002, 'webSearchTool',       TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002132, 1000000002, 'workspaceMemoryTool', TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002133, 1000000002, 'wikiTool',            TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002134, 1000000002, 'readFileTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002135, 1000000002, 'writeFileTool',       TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002136, 1000000002, 'editFileTool',        TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002137, 1000000002, 'documentExtractTool', TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002138, 1000000002, 'shellExecuteTool',    TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002139, 1000000002, 'delegateAgentTool',   TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000002140, 1000000002, 'cronJobTool',         TRUE, NOW(), NOW(), 0) ON DUPLICATE KEY UPDATE tool_name = VALUES(tool_name);
