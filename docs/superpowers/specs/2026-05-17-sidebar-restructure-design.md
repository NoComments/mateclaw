# 左侧工具栏重构设计文档

**日期**：2026-05-17  
**范围**：`mateclaw-ui/src/views/layout/MainLayout.vue` + 路由迁移  
**目标**：面向企业员工私用，降低认知负担，高频功能前置，角色边界清晰

---

## 一、问题陈述

现有侧边栏 3 组（Core / Connect / System），13+ 个菜单项存在以下问题：

- Core 组混杂了操作工具（Chat、Agents）与知识管理（Wiki、Memory）与场景应用（Enterprise），语义不清
- 工作流（`/settings/workflows`）和定时任务（`/settings/cron-jobs`）埋在 Settings 二级导航"高级"下，企业员工难以发现
- Admin 功能（设置、安全、后台监控）与员工功能同级陈列，认知噪音大

---

## 二、最终分组方案（4 组 15 项）

### 工作区（Work）— 全员可见
企业员工每日高频操作区，是侧边栏的核心入口。

| # | 菜单项 | 路由 | 变更说明 |
|---|--------|------|---------|
| 1 | Dashboard | `/dashboard` | 不动 |
| 2 | 对话 | `/chat` | 不动 |
| 3 | 数字员工 | `/agents` | 不动 |
| 4 | 工作流 | `/workflows` | **新顶级路由**，从 `/settings/workflows` 抽取 |
| 5 | 定时任务 | `/cron-jobs` | **新顶级路由**，从 `/settings/cron-jobs` 抽取 |
| 6 | 活动日志 | `/activity` | 从 Connect 组移入 |
| 7 | 记忆 | `/memory` | 从 Core 组移入 |

### 业务（Business）— 全员可见
企业场景化功能与知识资产，低频但重要。

| # | 菜单项 | 路由 | 变更说明 |
|---|--------|------|---------|
| 8 | 企业应用 | `/enterprise` | 从 Core 组移入 |
| 9 | Wiki | `/wiki` | 从 Core 组移入 |

### 集成（Connect）— 全员可见
Agent 的外部连接与能力扩展。

| # | 菜单项 | 路由 | 变更说明 |
|---|--------|------|---------|
| 10 | 渠道 | `/channels` | 不动 |
| 11 | 技能 | `/skills` | 不动 |
| 12 | 插件 | `/plugins` | 不动 |

### 系统（System）— 仅 Admin 可见
管理员专属，`role !== 'admin'` 时整组不渲染。

| # | 菜单项 | 路由 | 变更说明 |
|---|--------|------|---------|
| 13 | 设置 | `/settings/models` | 不动，Settings Layout 保留完整子导航 |
| 14 | 安全 | `/security` | 不动 |
| 15 | 后台监控 | `/backstage` | 不动，保留注意力红点（stuck > 0 时显示） |

---

## 三、路由迁移方案

### 3.1 新增顶级路由

在 `src/router/index.ts` 中，将 Workflows 和 CronJobs 从 Settings 子路由**提升为顶级路由**：

```
/workflows   →  component: () => import('@/views/Workflows.vue')
/cron-jobs   →  component: () => import('@/views/CronJobs.vue')
```

两者**不套用** Settings Layout，直接在 MainLayout 的 `<router-view>` 中渲染，与 Chat、Agents 页面体验一致。

### 3.2 旧路径重定向（向后兼容）

```
/settings/workflows  →  redirect: '/workflows'
/settings/cron-jobs  →  redirect: '/cron-jobs'
```

保留重定向确保书签、历史链接不失效。

### 3.3 Settings Layout 清理

`Settings/Layout.vue` 中的 `COMPACT_ROUTES` 数组移除 `/settings/workflows`（该路由已不存在）。Settings 子导航中的"工作流"和"定时任务"入口一并删除，避免重复入口。

---

## 四、MainLayout.vue 改动范围

### 4.1 navGroups 重写

将现有 `navGroups` computed 替换为新的 4 组结构：

```typescript
const navGroups = computed(() => [
  {
    key: 'work',
    label: t('nav.work'),
    items: [
      { path: '/dashboard', label: t('nav.dashboard'), icon: '...' },
      { path: '/chat',      label: t('nav.chat'),      icon: '...' },
      { path: '/agents',    label: t('nav.agents'),    icon: '...' },
      { path: '/workflows', label: t('nav.workflows'), icon: '...' },  // 新
      { path: '/cron-jobs', label: t('nav.cronJobs'),  icon: '...' },  // 新
      { path: '/activity',  label: t('nav.activity'),  icon: '...' },
      { path: '/memory',    label: t('nav.memory'),    icon: '...' },
    ],
  },
  {
    key: 'business',
    label: t('nav.business'),
    items: [
      { path: '/enterprise', label: t('nav.enterprise'), icon: '...' },
      { path: '/wiki',       label: t('nav.wiki'),       icon: '...' },
    ],
  },
  {
    key: 'connect',
    label: t('nav.connect'),
    items: [
      { path: '/channels', label: t('nav.channels'), icon: '...' },
      { path: '/skills',   label: t('nav.skills'),   icon: '...' },
      { path: '/plugins',  label: t('nav.plugins'),  icon: '...' },
    ],
  },
  // System 组：isAdminRole 为 false 时整组不注入
  ...(isAdminRole.value ? [{
    key: 'system',
    label: t('nav.system'),
    items: [
      { path: '/settings/models', label: t('nav.settings'),  icon: '...' },
      { path: '/security',        label: t('nav.security'),  icon: '...' },
      {
        path: '/backstage',
        label: t('nav.backstage'),
        tooltip: t('nav.backstageTooltip'),
        icon: '...',
      },
    ],
  }] : []),
])
```

### 4.2 isNavItemActive 更新

新增对 `/workflows` 和 `/cron-jobs` 的精确匹配（它们是顶级路由，`===` 即可，无需 startsWith）。

### 4.3 i18n 新增 key

两个语言文件各新增：
- `nav.work`：`'工作区'` / `'Work'`
- `nav.business`：`'业务'` / `'Business'`
- `nav.workflows`：`'工作流'` / `'Workflows'`（如已存在则复用）
- `nav.cronJobs`：`'定时任务'` / `'Cron Jobs'`（如已存在则复用）

---

## 五、不在本次范围内

- 页面内容本身（Workflows.vue、CronJobs.vue）不改动
- WorkspaceSwitcher 不改动
- 底部 Footer（健康指示器、主题/语言切换、用户信息）不改动
- 移动端适配逻辑不改动（仅 navGroups 数据层变化，渲染逻辑不动）

---

## 六、验证标准

1. `pnpm build` 零错误
2. `pnpm lint` 零错误
3. 普通员工登录：侧边栏无"系统"组，共 3 组 12 项
4. Admin 登录：侧边栏 4 组 15 项，后台监控注意力红点正常
5. 点击"工作流"→ 进入 `/workflows`，不显示 Settings 二级导航
6. 点击"定时任务"→ 进入 `/cron-jobs`，不显示 Settings 二级导航
7. 访问 `/settings/workflows` → 自动跳转至 `/workflows`
8. 访问 `/settings/cron-jobs` → 自动跳转至 `/cron-jobs`
