-- V117: Add analyticsCompute tool binding + semantic columns on template fields
--
-- Part 1: Bind the new tool to the data-analyst agent (id=1000000020)
MERGE INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
KEY (id)
VALUES (1000000026, 1000000020, 'analyticsCompute', TRUE, NOW(), NOW(), 0);

-- Part 2: Semantic layer columns on mate_dataset_template_field
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS time_granularity VARCHAR(20) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS aggregation VARCHAR(10) DEFAULT NULL;
ALTER TABLE mate_dataset_template_field ADD COLUMN IF NOT EXISTS compute_hint VARCHAR(500) DEFAULT NULL;
