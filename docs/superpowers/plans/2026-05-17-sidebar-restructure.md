# 左侧工具栏重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将侧边栏从 3 组重构为 4 组（工作区 / 业务 / 集成 / 系统），并将工作流、定时任务从 Settings 子路由提升为顶级路由。

**Architecture:** 路由层新增 `/workflows`、`/cron-jobs` 顶级路由（无 Settings Layout 包裹），Settings 内旧路径改为重定向；MainLayout navGroups 完全重写为新分组；Settings/Layout 删除已迁移的两个菜单项。

**Tech Stack:** Vue 3 + TypeScript + Vue Router 4，无新依赖

---

## 文件改动地图

| 文件 | 操作 | 说明 |
|------|------|------|
| `mateclaw-ui/src/i18n/locales/zh-CN.ts` | 修改 | nav 区块新增 `work`、`business` key |
| `mateclaw-ui/src/i18n/locales/en-US.ts` | 修改 | nav 区块新增 `work`、`business` key |
| `mateclaw-ui/src/router/index.ts` | 修改 | 新增顶级路由；Settings 子路由改重定向；删除旧的 `cron-jobs` 顶层重定向 |
| `mateclaw-ui/src/views/Settings/Layout.vue` | 修改 | 从 sections 删除 `cron-jobs`、`workflows`；清理 COMPACT_ROUTES |
| `mateclaw-ui/src/views/layout/MainLayout.vue` | 修改 | 重写 navGroups；System 组 Admin-only 整组逻辑 |

---

## Task 1: i18n — 新增分组标签 key

**Files:**
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`

- [ ] **Step 1: zh-CN 新增 key**

在 `zh-CN.ts` 的 `nav:` 区块（当前第 358 行），在 `core: '核心'` 前插入两行：

```typescript
    work: '工作区',
    business: '业务',
```

插入后 nav 区块顶部应为：

```typescript
  nav: {
    work: '工作区',
    business: '业务',
    dashboard: '仪表盘',
    // ...其余保持不变
```

- [ ] **Step 2: en-US 新增 key**

找到 `en-US.ts` 中 `nav:` 区块（同样位置，有 `core: 'Core'`），在 `core` 前插入：

```typescript
    work: 'Work',
    business: 'Business',
```

- [ ] **Step 3: Commit**

```bash
cd mateclaw-ui
git add src/i18n/locales/zh-CN.ts src/i18n/locales/en-US.ts
git commit -m "feat(i18n): add nav.work and nav.business group label keys"
```

---

## Task 2: Router — 提升工作流和定时任务为顶级路由

**Files:**
- Modify: `mateclaw-ui/src/router/index.ts`

- [ ] **Step 1: 新增顶级路由**

在 `router/index.ts` 中，找到 `path: 'memory'` 路由块（约第 49 行），在其后、`// ==================== Connect ====================` 注释之前插入：

```typescript
        // ==================== Work (promoted from Settings) ====================
        {
          path: 'workflows',
          name: 'Workflows',
          component: () => import('@/views/Workflows.vue'),
          meta: { title: 'Workflows' },
        },
        {
          path: 'cron-jobs',
          name: 'CronJobs',
          component: () => import('@/views/CronJobs.vue'),
          meta: { title: 'Cron Jobs' },
        },
```

- [ ] **Step 2: Settings 子路由改重定向**

找到 Settings children 中的以下两个块（约第 166–178 行），**将 component 定义替换为 redirect**：

原代码：
```typescript
            {
              path: 'cron-jobs',
              name: 'SettingsCronJobs',
              component: () => import('@/views/CronJobs.vue'),
              meta: { title: 'Settings - Cron Jobs' },
            },
            {
              path: 'workflows',
              name: 'SettingsWorkflows',
              component: () => import('@/views/Workflows.vue'),
              meta: { title: 'Settings - Workflows' },
            },
```

替换为：
```typescript
            { path: 'cron-jobs', redirect: '/cron-jobs' },
            { path: 'workflows', redirect: '/workflows' },
```

- [ ] **Step 3: 删除旧的顶层反向重定向**

找到根 children 底部的 Redirects 区块（约第 264 行），删除这一行：

```typescript
        { path: 'cron-jobs', redirect: '/settings/cron-jobs' },
```

> 这行原来把 `/cron-jobs` 发往 `/settings/cron-jobs`，现在 `/cron-jobs` 已是独立路由，不需要再重定向。

- [ ] **Step 4: 验证路由无 TS 错误**

```bash
cd mateclaw-ui
pnpm exec vue-tsc --noEmit 2>&1 | grep -E "error|Error" | head -20
```

预期：无输出（无错误）

- [ ] **Step 5: Commit**

```bash
git add src/router/index.ts
git commit -m "feat(router): promote /workflows and /cron-jobs to top-level routes"
```

---

## Task 3: Settings/Layout.vue — 清理已迁移的菜单项

**Files:**
- Modify: `mateclaw-ui/src/views/Settings/Layout.vue`

- [ ] **Step 1: 从 COMPACT_ROUTES 删除 /settings/workflows**

找到第 55 行：

```typescript
const COMPACT_ROUTES = ['/settings/workflows', '/settings/triggers']
```

改为：

```typescript
const COMPACT_ROUTES = ['/settings/triggers']
```

- [ ] **Step 2: 从 sections 删除 cron-jobs 和 workflows 条目**

找到 sections computed 中以下代码块（约第 164–176 行）：

```typescript
  {
    id: 'cron-jobs',
    path: '/settings/cron-jobs',
    label: t('nav.cronJobs'),
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
  },
  {
    id: 'workflows',
    path: '/settings/workflows',
    label: t('nav.workflows', 'Workflows'),
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>',
  },
```

**整段删除**（divider-advanced 保留，triggers 等后续项保留）。

- [ ] **Step 3: 验证 Settings 页面无 TS 错误**

```bash
cd mateclaw-ui
pnpm exec vue-tsc --noEmit 2>&1 | grep -E "error|Error" | head -20
```

预期：无输出

- [ ] **Step 4: Commit**

```bash
git add src/views/Settings/Layout.vue
git commit -m "refactor(settings): remove workflows and cron-jobs from settings sub-nav"
```

---

## Task 4: MainLayout.vue — 重写 navGroups

**Files:**
- Modify: `mateclaw-ui/src/views/layout/MainLayout.vue`

- [ ] **Step 1: 替换 navGroups computed**

找到 `MainLayout.vue` 中的 `const navGroups = computed(() => [` 块（约第 326 行），将整个 computed 替换为：

```typescript
const navGroups = computed(() => [
  {
    key: 'work',
    label: t('nav.work'),
    items: [
      {
        path: '/dashboard',
        label: t('nav.dashboard', 'Dashboard'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`,
      },
      {
        path: '/chat',
        label: t('nav.chat'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`,
      },
      {
        path: '/agents',
        label: t('nav.agents'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>`,
      },
      {
        path: '/workflows',
        label: t('nav.workflows'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
      },
      {
        path: '/cron-jobs',
        label: t('nav.cronJobs'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
      },
      {
        path: '/activity',
        label: t('nav.activity'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
      },
      {
        path: '/memory',
        label: t('nav.memory'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a4 4 0 0 1 4 4v2a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4z"/><path d="M16 14H8a4 4 0 0 0-4 4v2h16v-2a4 4 0 0 0-4-4z"/><line x1="12" y1="11" x2="12" y2="14"/></svg>`,
      },
    ],
  },
  {
    key: 'business',
    label: t('nav.business'),
    items: [
      {
        path: '/enterprise',
        label: t('nav.enterprise'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18"/><path d="M5 21V7l7-4 7 4v14"/><path d="M9 9h.01"/><path d="M9 12h.01"/><path d="M9 15h.01"/><path d="M9 18h.01"/><path d="M15 9h.01"/><path d="M15 12h.01"/><path d="M15 15h.01"/><path d="M15 18h.01"/></svg>`,
      },
      {
        path: '/wiki',
        label: t('nav.wiki'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="14" y2="11"/></svg>`,
      },
    ],
  },
  {
    key: 'connect',
    label: t('nav.connect'),
    items: [
      {
        path: '/channels',
        label: t('nav.channels'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.6 1.18h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.91 8.73a16 16 0 0 0 6.29 6.29l1.62-1.62a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg>`,
      },
      {
        path: '/skills',
        label: t('nav.skills'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>`,
      },
      {
        path: '/plugins',
        label: t('nav.plugins'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 3h-8v4h8V3z"/></svg>`,
      },
    ],
  },
  ...(isAdminRole.value ? [{
    key: 'system',
    label: t('nav.system'),
    items: [
      {
        path: '/settings/models',
        label: t('nav.settings'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M4.93 4.93a10 10 0 0 0 0 14.14"/></svg>`,
      },
      {
        path: '/security',
        label: t('nav.security'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`,
      },
      {
        path: '/backstage',
        label: t('nav.backstage'),
        tooltip: t('nav.backstageTooltip'),
        icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>`,
      },
    ],
  }] : []),
])
```

- [ ] **Step 2: 确认 attention dot 逻辑仍正确**

attention dot 绑定条件是 `item.path === '/backstage' && backstageAlertActive`。检查模板中该条件引用的 `item.path` 与新 navGroups 里 backstage 的 `path: '/backstage'` 一致 — 无需改动模板。

- [ ] **Step 3: 验证 TS 无错误**

```bash
cd mateclaw-ui
pnpm exec vue-tsc --noEmit 2>&1 | grep -E "error|Error" | head -20
```

预期：无输出

- [ ] **Step 4: Commit**

```bash
git add src/views/layout/MainLayout.vue
git commit -m "feat(sidebar): restructure nav into Work/Business/Connect/System groups"
```

---

## Task 5: 构建验证 + 冒烟测试

**Files:** 无改动，仅验证

- [ ] **Step 1: 完整 build**

```bash
cd mateclaw-ui
pnpm build 2>&1 | tail -20
```

预期：最后一行出现 `✓ built in` 或 `dist/` 文件生成，无红色错误。

- [ ] **Step 2: Lint**

```bash
cd mateclaw-ui
pnpm lint 2>&1 | tail -10
```

预期：无错误输出（warning 可接受）。

- [ ] **Step 3: 冒烟测试清单**

启动后端（`cd mateclaw-server && mvn spring-boot:run`）和前端（`cd mateclaw-ui && pnpm dev`），打开 `http://localhost:5173`，用 `admin / admin123` 登录，逐项验证：

```
□ 侧边栏显示 4 组：工作区 / 业务 / 集成 / 系统
□ 工作区：Dashboard、对话、数字员工、工作流、定时任务、活动记录、记忆 共 7 项
□ 业务：企业场景、Wiki 知识库 共 2 项
□ 集成：渠道、技能、插件 共 3 项
□ 系统（admin 可见）：设置、安全、后台 共 3 项
□ 点击"工作流" → 进入 /workflows，无 Settings 二级导航
□ 点击"定时任务" → 进入 /cron-jobs，无 Settings 二级导航
□ 手动访问 /settings/workflows → 自动跳转到 /workflows
□ 手动访问 /settings/cron-jobs → 自动跳转到 /cron-jobs
□ 普通员工账号登录 → 侧边栏无"系统"组（3 组 12 项）
□ 后台监控 attention dot 在有 stuck 任务时仍显示
□ 侧边栏折叠/展开正常
□ 浏览器 console 无红色报错
```

- [ ] **Step 4: 最终 Commit（如有遗漏小修）**

```bash
git add -p   # 选择性 stage 修复
git commit -m "fix(sidebar): post-smoke-test corrections"
```

---

## 验证标准（done criteria）

- `pnpm build` 零错误
- `pnpm lint` 零错误
- 冒烟测试清单全部 ✓
