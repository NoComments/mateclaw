/**
 * Client-side categorization for the SkillMarket page.
 *
 * Why client-side: the backend skill registry is open-ended (built-in / MCP /
 * ACP / dynamic) and doesn't carry a UX-level category. Rather than migrate
 * the schema for a pure-display concern, we keep a slug → group map here.
 * Unknown slugs fall back to the `extension` group, which is collapsed by
 * default in the UI to keep the long tail out of sight without removing it.
 */

export type SkillGroupKey =
  | 'office'
  | 'data'
  | 'browser'
  | 'channel'
  | 'schedule'
  | 'retrieval'
  | 'design'
  | 'devops'
  | 'mateclaw'
  | 'extension'

export interface SkillGroupDef {
  key: SkillGroupKey
  icon: string
  /** i18n key under `skills.groups.*` */
  i18nKey: string
  /** Hidden behind a "show extensions" toggle */
  collapsedByDefault?: boolean
}

/**
 * Display order. Extension group is last and collapsed.
 */
export const SKILL_GROUPS: readonly SkillGroupDef[] = [
  { key: 'office', icon: '📄', i18nKey: 'office' },
  { key: 'retrieval', icon: '🌐', i18nKey: 'retrieval' },
  { key: 'channel', icon: '💬', i18nKey: 'channel' },
  { key: 'schedule', icon: '⏰', i18nKey: 'schedule' },
  { key: 'mateclaw', icon: '🏠', i18nKey: 'mateclaw' },
  // 高阶 / 开发者向分组：默认折叠，避免淹没业务用户的视线，
  // 同时仍然保留独立分组结构，方便研发或定制场景按需展开。
  { key: 'data', icon: '🔍', i18nKey: 'data', collapsedByDefault: true },
  { key: 'browser', icon: '🧭', i18nKey: 'browser', collapsedByDefault: true },
  { key: 'devops', icon: '🛠️', i18nKey: 'devops', collapsedByDefault: true },
  { key: 'design', icon: '🎨', i18nKey: 'design', collapsedByDefault: true },
  { key: 'extension', icon: '🧩', i18nKey: 'extension', collapsedByDefault: true },
]

/**
 * Slug (= skill.name) → group. Any skill whose slug is not listed is treated
 * as an `extension` and folded by default.
 */
const SLUG_TO_GROUP: Record<string, SkillGroupKey> = {
  // Office docs
  docx: 'office',
  xlsx: 'office',
  pptx: 'office',
  pdf: 'office',
  file_reader: 'office',

  // Data
  sql_query: 'data',

  // Information retrieval
  news: 'retrieval',
  arxiv: 'retrieval',
  x_intel: 'retrieval',
  blogwatcher: 'retrieval',
  ckjia_shopping: 'retrieval',
  'ckjia-shopping': 'retrieval',

  // Channel / messaging
  channel_message: 'channel',
  chat_with_agent: 'channel',
  multi_agent_collaboration: 'channel',
  dingtalk_channel_connect: 'channel',

  // Scheduling / triggers
  cron: 'schedule',
  webhook_subscriptions: 'schedule',
  'webhook-subscriptions': 'schedule',

  // Browser / RPA
  browser_cdp: 'browser',
  browser_visible: 'browser',

  // Engineering / dev process
  make_plan: 'devops',
  plan: 'devops',
  writing_plans: 'devops',
  'writing-plans': 'devops',
  spike: 'devops',
  ideation: 'devops',
  subagent_driven_development: 'devops',
  'subagent-driven-development': 'devops',
  systematic_debugging: 'devops',
  'systematic-debugging': 'devops',
  test_driven_development: 'devops',
  'test-driven-development': 'devops',
  requesting_code_review: 'devops',
  'requesting-code-review': 'devops',
  skill_creator: 'devops',
  'skill-creator': 'devops',

  // Design / visualization
  architecture_diagram: 'design',
  'architecture-diagram': 'design',
  claude_design: 'design',
  'claude-design': 'design',
  design_md: 'design',
  'design-md': 'design',
  popular_web_designs: 'design',
  'popular-web-designs': 'design',
  sketch: 'design',

  // Platform self-help
  guidance: 'mateclaw',
  mateclaw_source_index: 'mateclaw',
}

/**
 * Per-skill Chinese description fallback. Used when the underlying
 * SKILL.md frontmatter `description` is English. The card renderer prefers
 * the explicit `description_zh` field (future) over this map, and falls back
 * to the existing `description` when neither is present.
 */
const ZH_DESCRIPTION: Record<string, string> = {
  // Office
  docx: '处理 Word 文档：创建、读取、编辑 .docx 文件',
  xlsx: '处理 Excel 表格：批量读写、公式、导出 .xlsx',
  pptx: '处理 PPT 演示文稿：创建、解析、编辑 .pptx',
  pdf: '处理 PDF 文件：解析、合并、拆分、加水印、表单',
  file_reader: '读取与摘要文本类文件（txt/md/json/yaml/csv/log/源代码）',

  // Data
  sql_query: '将自然语言问题转为只读 SQL，自动发现表结构并执行查询',

  // Retrieval
  news: '检索互联网最新新闻，覆盖财经、科技、社会等分类',
  arxiv: '按关键词、作者、分类或编号检索 arXiv 论文',
  x_intel: '读取 X(Twitter) 帖子、搜索、时间线、用户主页',
  blogwatcher: '订阅并监控博客和 RSS/Atom 源',
  'ckjia-shopping': '跨平台比价：淘宝/京东/天猫/拼多多商品聚合搜索 + 拍图识物',

  // Channel
  channel_message: '主动向用户/会话/渠道单向推送消息，用于通知、提醒、异步结果回推',
  chat_with_agent: '咨询其他数字员工，支持单次委托或多任务并行委托',
  multi_agent_collaboration: '编排多个数字员工并行或串行协作，整合各方结果',
  dingtalk_channel_connect: '使用可见浏览器自动完成 QingwenClaws 钉钉渠道接入',

  // Schedule
  cron: '创建/管理定时任务：未来某时刻执行或按周期重复执行',
  'webhook-subscriptions': '订阅外部事件，事件触发时自动调度数字员工',

  // Browser
  browser_cdp: '通过 CDP 协议连接已运行的浏览器，用于远程调试或多工具共享',
  browser_visible: '以可见窗口启动真实浏览器，适合演示、调试、需人工参与的场景',

  // DevOps / Dev process
  make_plan: '向更强模型请求一份分步可落地的执行计划，由当前数字员工执行',
  plan: '把计划写入 .mateclaw/plans/ 目录，仅记录不执行',
  'writing-plans': '将多步任务写成实现计划：清单化、含路径、含代码骨架',
  spike: '快速一次性实验，用来在正式实现前验证想法',
  ideation: '通过创造性约束生成项目创意',
  'subagent-driven-development': '通过子 Agent 委托执行计划，两阶段评审',
  'systematic-debugging': '四阶段根因调试法：先理解 bug 再动手修',
  'test-driven-development': 'TDD：强制 RED-GREEN-REFACTOR，先写测试再写实现',
  'requesting-code-review': '提交前自动代码审查：安全扫描、质量门禁、自动修复',
  'skill-creator': '创建新技能或更新已有技能的元数据/正文',

  // Design
  'architecture-diagram': '生成暗色主题的 SVG 架构图/云图/基础设施图（HTML 输出）',
  'claude-design': '设计一次性 HTML 页面（落地页、演示稿、原型）',
  'design-md': '编写/校验/导出 Google DESIGN.md 设计 token 规范',
  'popular-web-designs': '54 套真实设计系统范例（Stripe / Linear / Vercel 等）',
  sketch: '快速产出 2-3 套 HTML 设计稿用于对比',

  // QingwenClaws self
  guidance: '回答关于 QingwenClaws 安装与配置的问题，优先查本地文档',
  mateclaw_source_index: '把用户问题映射到 QingwenClaws 文档路径与源码入口，回答"XX 功能在哪里实现"',
}

/**
 * Resolve a skill slug to its UX group. Unknown slugs go to `extension` so
 * the long tail is collapsed by default — that's how we hide the rarely-used
 * skills without deleting them.
 */
export function categorizeSkill(slug: string | null | undefined): SkillGroupKey {
  if (!slug) return 'extension'
  return SLUG_TO_GROUP[slug] || 'extension'
}

export function getZhDescription(slug: string | null | undefined): string | undefined {
  if (!slug) return undefined
  return ZH_DESCRIPTION[slug]
}
