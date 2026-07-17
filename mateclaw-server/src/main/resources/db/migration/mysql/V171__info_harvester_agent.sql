-- V114: 种子「信息采集专家」数字员工 + 注册 WebFetchTool 内置工具
--
-- 配套：
--   - classpath:skills/info-harvester/SKILL.md                                （抓取流程 + 简报格式）
--   - vip.mate.tool.builtin.WebFetchTool                                      （HTTP/Jsoup 抓取）
--   - vip.mate.skill.installer.InfoHarvesterAgentSeedService                  （Order 121，
--     在 BuiltinSkillSeedService 之后回填 info-harvester skill 的 mate_agent_skill 绑定）
--
-- 与 V113 同样的考量：skill 行的 id 由 BuiltinSkillSeedService 分配，所以这里只
-- 种 agent 和 mate_agent_tool 绑定；skill 绑定走 Java seed 服务。

-- 1) WebFetchTool 注册到 mate_tool，使其在「编辑数字员工 → 工具」picker 中可见
INSERT INTO mate_tool (id, name, display_name, description, tool_type, bean_name, icon, enabled, builtin, create_time, update_time, deleted)
VALUES (1000000023, 'WebFetchTool', 'Web Fetch', 'Fetch a single web page (HTTP + Jsoup). Modes: text (clean text), html (raw), links (title<TAB>url). Built-in per-host throttle 1.5s, 10s timeout, 1 MB body cap. For JS-rendered pages use browser_use; for search-engine queries use search.', 'builtin', 'webFetchTool', '🌐', TRUE, TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    bean_name = VALUES(bean_name),
    icon = VALUES(icon),
    enabled = TRUE,
    builtin = TRUE,
    deleted = 0,
    update_time = NOW();

-- 2) 数字员工：信息采集专家
INSERT INTO mate_agent (id, name, description, agent_type, system_prompt, model_name, max_iterations, enabled, icon, tags, workspace_id, create_time, update_time, deleted)
VALUES (
    1000000030,
    '信息采集专家',
    '每日抓取农业农村部 / 河南省农业农村厅 / 河南省发改委 / 河南省人民政府四个外网站点，产出结构化每日简报。',
    'react',
    '你是「信息采集专家」，专门为河南省农业产业相关业务做权威信息汇集。

【你的固定职责】
- 仅采集 info-harvester 技能里列出的 5 个外网列表页，不要被详情页里的外链带走
- 每次会话默认产出一份《每日要情简报》，除非用户明确要求换格式
- 严格遵守 info-harvester 技能的 5 步工作流程

【你被授权使用的工具】
- web_fetch：抓取 HTML 页面，支持 text/html/links 三种返回模式
- search：兜底搜索（仅当某站点 web_fetch 全部失败时使用，用站名+今日日期搜索备用入口）

【输出要求】
- 全程中文
- 不编造没抓到的内容，宁可在对应小节写「今日无新内容」
- 简报最后必须附「采集统计」小节，列出本次扫描数、失败 host
- 单次产出 800~1500 字，超长用户不读

【边界】
- 不主动联想或推荐未在 info-harvester 技能列表里的站点
- 不解读政策法律含义；如要做趋势判断，放在「值得关注」一节并明确标注「编辑视角」
- 抓取失败要如实反馈，不要静默吞错',
    NULL,
    30,
    TRUE,
    'pi:rss',
    'builtin,harvester,daily-brief',
    1,
    NOW(),
    NOW(),
    0
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    system_prompt = VALUES(system_prompt),
    icon = VALUES(icon),
    tags = VALUES(tags),
    enabled = VALUES(enabled),
    deleted = 0,
    update_time = NOW();

-- 3) 工具绑定：web_fetch + search
INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000031, 1000000030, 'web_fetch', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    enabled = TRUE,
    deleted = 0,
    update_time = NOW();

INSERT INTO mate_agent_tool (id, agent_id, tool_name, enabled, create_time, update_time, deleted)
VALUES (1000000032, 1000000030, 'search', TRUE, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    enabled = TRUE,
    deleted = 0,
    update_time = NOW();
