# QingwenClaws 品牌定制改造 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 mateclaw fork 改造成 QingwenClaws 商业产品对外品牌：消除所有 UI / JAR / Docker / 日志 / HTTP / 文档中可见的 `mateclaw` 痕迹，保留 Java 包名 / Maven 模块 / 表前缀等结构性命名以维持上游 sync 能力。

**Architecture:** 中改档位（B 档）。视觉资产 → UI 改造 → 后端 runtime → 种子数据 → 文档 → 容器编排 → 验收。每个 phase 自带构建/测试/grep 验证，频繁提交。

**Tech Stack:** Spring Boot 3.5 + Java 21 (Maven) / Vue 3 + TypeScript + Vite (pnpm) / Docker Compose / Flyway / H2 + MySQL

**Spec:** `docs/superpowers/specs/2026-05-17-brand-customization-design.md`

**Branch base:** `baseline/v1.3.0` → 建议从此分支切出 `feat/brand-customization-qingwenclaws` 工作分支

---

## 关键约束（每个 task 都要记住）

### OUT-of-scope（不要改 — 改了会引发结构性冲突）

| 类别 | 例子 |
|---|---|
| Maven groupId | `vip.mate` |
| Java 包根 | `vip.mate.*`（所有 .java 文件 import 都不动）|
| Java 类名 | `MateClawException`、`MateClawStateKeys`、`MateClawDocTool` 等 |
| 模块目录名 | `mateclaw-server/`、`mateclaw-ui/`、`mateclaw-webchat/`、`mateclaw-plugin-api/`、`mateclaw-plugin-sample/` |
| Maven artifactId | `mateclaw-server`、`mateclaw-plugin-api`、`mateclaw-plugin-sample` |
| DB 表前缀 | `mate_skill`、`mate_model_provider` 等 |
| Flyway 迁移文件名 | `V36__skill_i18n_name.sql` 等历史迁移 |
| **YAML 配置根键** | `application.yml` 里的 `mateclaw:` 根节点 + 其下所有 `mateclaw.xxx` 路径（Java `@ConfigurationProperties(prefix="mateclaw")` 依赖）|
| **环境变量前缀** | `MATECLAW_*`（Java `@Value("${MATECLAW_*}")` 依赖）|
| LICENSE 文件 | 必须保留 Apache 2.0 原文 |

### 验证字串清扫的标准 grep（每个 phase 结尾跑一次）

```bash
grep -RIin --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist \
  --exclude-dir=.git --exclude-dir=.superpowers \
  "mateclaw\|mate.claw" . \
  | grep -vE "(vip/mate|vip\.mate|mate_|mateclaw-(server|ui|webchat|plugin-api|plugin-sample)/|MateClaw|MATECLAW_|^[^:]+:[0-9]+:\s*mateclaw:|LICENSE)" \
  > /tmp/remaining-mateclaw.txt
wc -l /tmp/remaining-mateclaw.txt
```

每个 phase 结尾该文件行数应**逐步下降**，最终 phase 应为 0。

---

## Phase 0 · 准备工作

### Task 0.1: 切工作分支

**Files:**
- 无文件变更

- [ ] **Step 1: 确认当前在 baseline/v1.3.0 分支且工作区干净**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
git status
git rev-parse --abbrev-ref HEAD
```

Expected: branch `baseline/v1.3.0`, no uncommitted changes (except `.github/PULL_REQUEST_TEMPLATE.md` untracked — leave alone for now).

- [ ] **Step 2: 切出工作分支**

Run:
```bash
git checkout -b feat/brand-customization-qingwenclaws
```

Expected: `Switched to a new branch 'feat/brand-customization-qingwenclaws'`

### Task 0.2: 基线 grep 计数（基准线）

**Files:**
- 无文件变更

- [ ] **Step 1: 记录基线 mateclaw 字串总命中数**

Run:
```bash
grep -RIin --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist \
  --exclude-dir=.git --exclude-dir=.superpowers \
  "mateclaw\|mate.claw" . \
  | grep -vE "(vip/mate|vip\.mate|mate_|mateclaw-(server|ui|webchat|plugin-api|plugin-sample)/|MateClaw|MATECLAW_|^[^:]+:[0-9]+:\s*mateclaw:|LICENSE)" \
  | wc -l
```

Expected: 数字 > 500（用作后续递减观察的基线）。把数字记在心里或临时记事，每 phase 结尾再跑一次对比。

---

## Phase 1 · 视觉资产生成（Day 1）

### Task 1.1: 创建主 logo SVG（横版 wordmark）

**Files:**
- Create: `mateclaw-ui/public/logo/qingwenclaws_logo.svg`

- [ ] **Step 1: 创建主 logo SVG**

`mateclaw-ui/public/logo/qingwenclaws_logo.svg`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="320" height="80" viewBox="0 0 320 80" role="img" aria-label="QingwenClaws">
  <defs>
    <linearGradient id="bodyG" x1="25%" y1="10%" x2="75%" y2="95%">
      <stop offset="0%" stop-color="#caf0f8"/>
      <stop offset="38%" stop-color="#48cae4"/>
      <stop offset="62%" stop-color="#00b4d8"/>
      <stop offset="100%" stop-color="#023e8a"/>
    </linearGradient>
    <linearGradient id="pincerG" x1="20%" y1="5%" x2="80%" y2="95%">
      <stop offset="0%" stop-color="#ffffff"/>
      <stop offset="45%" stop-color="#48cae4"/>
      <stop offset="100%" stop-color="#03045e"/>
    </linearGradient>
    <linearGradient id="textG" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#90e0ef"/>
      <stop offset="50%" stop-color="#00b4d8"/>
      <stop offset="100%" stop-color="#0077a8"/>
    </linearGradient>
    <radialGradient id="halo" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#00b4d8" stop-opacity="0.35"/>
      <stop offset="100%" stop-color="#00b4d8" stop-opacity="0"/>
    </radialGradient>
  </defs>

  <!-- Icon group, transform-rotated -15° -->
  <g transform="translate(10,8) rotate(-12, 36, 32)">
    <ellipse cx="36" cy="32" rx="38" ry="32" fill="url(#halo)"/>
    <!-- Shell rings -->
    <path d="M 8 50 C 0 45, -2 30, 6 18 C 12 10, 20 8, 28 12" stroke="#48cae4" stroke-width="2" fill="none" stroke-linecap="round" opacity="0.7"/>
    <path d="M 14 52 C 6 45, 4 30, 12 20 C 18 12, 26 10, 34 14" stroke="#48cae4" stroke-width="2" fill="none" stroke-linecap="round" opacity="0.8"/>
    <path d="M 22 54 C 14 46, 12 32, 20 22 C 26 14, 34 12, 42 16" stroke="#48cae4" stroke-width="2" fill="none" stroke-linecap="round" opacity="0.9"/>
    <!-- Body -->
    <path d="M 30 14 C 25 5, 45 0, 56 6 C 64 11, 68 22, 64 30 C 60 38, 50 42, 40 38 C 32 35, 26 26, 30 14 Z" fill="url(#bodyG)"/>
    <!-- Rim light -->
    <path d="M 30 14 C 28 6, 45 2, 56 8" stroke="#ffffff" stroke-width="1.5" fill="none" opacity="0.7"/>
    <!-- Upper pincer hook -->
    <path d="M 60 14 C 64 6, 70 0, 72 4 C 74 8, 70 14, 64 18 L 62 18 C 62 16, 60 16, 60 14 Z" fill="url(#pincerG)"/>
    <!-- Lower pincer -->
    <path d="M 64 30 C 72 28, 80 26, 78 30 C 76 34, 70 36, 66 36 L 64 36 Z" fill="url(#pincerG)"/>
    <circle cx="72" cy="4" r="1.5" fill="#ffffff" opacity="0.9"/>
    <circle cx="78" cy="30" r="1.5" fill="#ffffff" opacity="0.9"/>
  </g>

  <!-- Wordmark -->
  <text x="100" y="50" font-family="-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Helvetica Neue', Arial, sans-serif" font-size="32" font-weight="700" fill="url(#textG)" letter-spacing="0.5">QingwenClaws</text>
</svg>
```

- [ ] **Step 2: 浏览器肉眼验证**

Run:
```bash
open mateclaw-ui/public/logo/qingwenclaws_logo.svg
```

Expected: 浏览器打开，看到蟹钳图标 + QingwenClaws 文字，没有渲染错误。

### Task 1.2: 创建纯图标版 logo（侧栏/小尺寸用）

**Files:**
- Create: `mateclaw-ui/public/logo/qingwenclaws_logo_s.svg`

- [ ] **Step 1: 创建方版图标**

`mateclaw-ui/public/logo/qingwenclaws_logo_s.svg`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128" role="img" aria-label="QingwenClaws">
  <defs>
    <linearGradient id="bodyG" x1="25%" y1="10%" x2="75%" y2="95%">
      <stop offset="0%" stop-color="#caf0f8"/>
      <stop offset="38%" stop-color="#48cae4"/>
      <stop offset="62%" stop-color="#00b4d8"/>
      <stop offset="100%" stop-color="#023e8a"/>
    </linearGradient>
    <linearGradient id="pincerG" x1="20%" y1="5%" x2="80%" y2="95%">
      <stop offset="0%" stop-color="#ffffff"/>
      <stop offset="45%" stop-color="#48cae4"/>
      <stop offset="100%" stop-color="#03045e"/>
    </linearGradient>
    <radialGradient id="halo" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#00b4d8" stop-opacity="0.45"/>
      <stop offset="100%" stop-color="#00b4d8" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="128" height="128" fill="none"/>
  <g transform="translate(20,18) rotate(-12, 44, 46)">
    <ellipse cx="44" cy="46" rx="56" ry="48" fill="url(#halo)"/>
    <path d="M 8 70 C -2 60, -4 38, 8 22 C 16 12, 28 10, 38 14" stroke="#48cae4" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.7"/>
    <path d="M 18 74 C 6 64, 4 40, 16 24 C 24 14, 36 12, 46 16" stroke="#48cae4" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.8"/>
    <path d="M 28 78 C 18 68, 16 44, 26 28 C 34 18, 46 14, 56 20" stroke="#48cae4" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.9"/>
    <path d="M 40 18 C 32 6, 60 0, 76 8 C 86 14, 92 28, 86 40 C 80 50, 66 56, 52 50 C 42 46, 36 32, 40 18 Z" fill="url(#bodyG)"/>
    <path d="M 40 18 C 38 8, 60 2, 76 10" stroke="#ffffff" stroke-width="2" fill="none" opacity="0.7"/>
    <path d="M 80 16 C 86 4, 94 -2, 98 4 C 102 10, 94 20, 84 24 L 82 22 C 82 20, 80 18, 80 16 Z" fill="url(#pincerG)"/>
    <path d="M 86 40 C 100 36, 108 34, 104 40 C 100 46, 92 50, 84 50 L 82 48 Z" fill="url(#pincerG)"/>
    <circle cx="98" cy="4" r="2" fill="#ffffff" opacity="0.9"/>
    <circle cx="104" cy="40" r="2" fill="#ffffff" opacity="0.9"/>
  </g>
</svg>
```

- [ ] **Step 2: 肉眼验证**

Run: `open mateclaw-ui/public/logo/qingwenclaws_logo_s.svg`

Expected: 方版图标，比横版更聚焦。

### Task 1.3: 创建 favicon.svg

**Files:**
- Create: `mateclaw-ui/public/logo/favicon.svg`

- [ ] **Step 1: 创建 32×32 favicon SVG**

`mateclaw-ui/public/logo/favicon.svg`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" role="img" aria-label="QingwenClaws">
  <defs>
    <linearGradient id="b" x1="20%" y1="10%" x2="80%" y2="90%">
      <stop offset="0%" stop-color="#48cae4"/>
      <stop offset="100%" stop-color="#023e8a"/>
    </linearGradient>
    <linearGradient id="p" x1="20%" y1="5%" x2="80%" y2="95%">
      <stop offset="0%" stop-color="#caf0f8"/>
      <stop offset="100%" stop-color="#0077a8"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="6" fill="#0d1b2a"/>
  <g transform="translate(4,4) rotate(-12, 12, 12)">
    <path d="M 4 18 C -1 14, -1 6, 4 2 C 8 0, 12 1, 14 3" stroke="#48cae4" stroke-width="1.5" fill="none" stroke-linecap="round" opacity="0.85"/>
    <path d="M 10 4 C 8 -1, 16 -2, 20 1 C 23 4, 24 9, 21 12 C 18 14, 14 14, 11 11 C 9 9, 8 7, 10 4 Z" fill="url(#b)"/>
    <path d="M 20 2 C 23 -2, 26 -3, 27 0 C 28 3, 25 6, 21 6 L 21 4 Z" fill="url(#p)"/>
    <path d="M 21 12 C 25 11, 28 10, 27 13 C 26 16, 22 16, 20 15 Z" fill="url(#p)"/>
  </g>
</svg>
```

- [ ] **Step 2: 肉眼验证**

Run: `open mateclaw-ui/public/logo/favicon.svg`

Expected: 32×32 紧凑版 logo，深底圆角。

### Task 1.4: 替换 favicon.ico

**Files:**
- Replace: `mateclaw-ui/public/logo/favicon.ico`

- [ ] **Step 1: 用 ImageMagick 把 SVG 转 ICO**

Run（需要安装 ImageMagick `brew install imagemagick`）:
```bash
cd mateclaw-ui/public/logo
# 备份旧 favicon
mv favicon.ico favicon.ico.mateclaw.bak
# 从 SVG 渲染 32x32 PNG，再打包到 ICO
magick -background none favicon.svg -resize 32x32 favicon-32.png
magick favicon-32.png favicon.ico
rm favicon-32.png
```

Expected: `favicon.ico` 重新生成，文件存在。

- [ ] **Step 2: 删除备份并验证**

Run:
```bash
rm favicon.ico.mateclaw.bak
file favicon.ico
```

Expected: `favicon.ico: MS Windows icon resource - 1 icon, 32x32, ...`

### Task 1.5: 生成 PNG fallback

**Files:**
- Create: `mateclaw-ui/public/logo/qingwenclaws_logo.png` (512×128)
- Create: `mateclaw-ui/public/logo/qingwenclaws_logo_s.png` (128×128)

- [ ] **Step 1: 从 SVG 渲染 PNG**

Run:
```bash
cd mateclaw-ui/public/logo
magick -background none -density 600 qingwenclaws_logo.svg -resize 512x128 qingwenclaws_logo.png
magick -background none -density 600 qingwenclaws_logo_s.svg -resize 128x128 qingwenclaws_logo_s.png
```

Expected: 两个 PNG 文件生成。

- [ ] **Step 2: 文件类型验证**

Run: `file qingwenclaws_logo.png qingwenclaws_logo_s.png`

Expected: 两个文件都识别为 `PNG image data`。

### Task 1.6: 删除旧 mateclaw logo 文件

**Files:**
- Delete: `mateclaw-ui/public/logo/mateclaw_logo.png`
- Delete: `mateclaw-ui/public/logo/mateclaw_logo_s.png`

- [ ] **Step 1: 删除旧 PNG**

Run:
```bash
rm mateclaw-ui/public/logo/mateclaw_logo.png
rm mateclaw-ui/public/logo/mateclaw_logo_s.png
ls mateclaw-ui/public/logo/
```

Expected: 列表里看到的是 `favicon.ico`、`favicon.svg`、`qingwenclaws_logo.png`、`qingwenclaws_logo.svg`、`qingwenclaws_logo_s.png`、`qingwenclaws_logo_s.svg`，没有 mateclaw_*.png。

### Task 1.7: Phase 1 提交

- [ ] **Step 1: 提交视觉资产**

Run:
```bash
git add mateclaw-ui/public/logo/
git status
git commit -m "$(cat <<'EOF'
feat(brand): add QingwenClaws logo assets and replace favicon

引入 QingwenClaws SVG/PNG logo 资产 (主版 + 方版) 与 favicon (ico + svg)，
删除旧 mateclaw 资产。视觉风格：侧面蟹钳 + 青蓝渐变 + 光晕。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Expected: `[feat/brand-customization-qingwenclaws ...]` commit 成功。

---

## Phase 2 · UI 改造（Day 1–2）

### Task 2.1: 更新 index.html

**Files:**
- Modify: `mateclaw-ui/index.html`

- [ ] **Step 1: 改 title 和 favicon 引用**

Edit `mateclaw-ui/index.html`:

old:
```html
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" href="/logo/favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>MateClaw - AI 助手</title>
```

new:
```html
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/logo/favicon.svg" />
    <link rel="alternate icon" href="/logo/favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>QingwenClaws - AI 助手</title>
```

- [ ] **Step 2: 验证文件状态**

Run: `grep -n "title\|favicon" mateclaw-ui/index.html`

Expected: title 行显示 `<title>QingwenClaws - AI 助手</title>`，favicon 行有 svg + ico 两行。

### Task 2.2: 替换 zh-CN.ts 品牌词

**Files:**
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`

- [ ] **Step 1: 逐行替换品牌字串**

Edit `mateclaw-ui/src/i18n/locales/zh-CN.ts`:

| Line | Old | New |
|---|---|---|
| 3 | `title: 'MateClaw - AI 助手',` | `title: 'QingwenClaws - AI 助手',` |
| 485 | `aboutTitle: '关于 MateClaw',` | `aboutTitle: '关于 QingwenClaws',` |
| 624 | `claudeCodeOauthRevokeHint: '请在 Claude Code 客户端中退出登录。MateClaw 不会修改 Claude Code 的本地凭据。',` | `claudeCodeOauthRevokeHint: '请在 Claude Code 客户端中退出登录。QingwenClaws 不会修改 Claude Code 的本地凭据。',` |
| 1417 | `dashscope: '阿里云 DashScope key — 跟 MateClaw .env 里的 DASHSCOPE_API_KEY 同一个值',` | `dashscope: '阿里云 DashScope key — 跟 QingwenClaws .env 里的 DASHSCOPE_API_KEY 同一个值',` |
| 1610 | `emptyHint: '将插件 JAR 文件放入 ~/.mateclaw/plugins/ 目录后重启服务',` | `emptyHint: '将插件 JAR 文件放入 ~/.qingwenclaws/plugins/ 目录后重启服务',` |
| 2444 | `heroDesc: 'MateClaw 的重点不是把更多能力堆进聊天框...` | `heroDesc: 'QingwenClaws 的重点不是把更多能力堆进聊天框...` |
| 2750 | `webchatHint: 'WebChat 用于把 MateClaw 聊天挂件嵌入外部网站...` | `webchatHint: 'WebChat 用于把 QingwenClaws 聊天挂件嵌入外部网站...` |
| 3184 | `title: '欢迎使用 MateClaw',` | `title: '欢迎使用 QingwenClaws',` |
| 3204 | `startUsing: '开始使用 MateClaw',` | `startUsing: '开始使用 QingwenClaws',` |
| 3402 | `readMateClawDoc: '查阅系统文档',` | `readQingwenClawsDoc: '查阅系统文档',` （**注意 key 也改**）|

- [ ] **Step 2: 验证替换完整**

Run: `grep -n "MateClaw\|mateclaw" mateclaw-ui/src/i18n/locales/zh-CN.ts`

Expected: 无输出（或仅剩你确认 OUT 的命中）。

- [ ] **Step 3: 更新 key 引用**

key `readMateClawDoc` 被改名为 `readQingwenClawsDoc`，需要找到所有 .vue 文件里的引用并更新。

Run:
```bash
grep -rn "readMateClawDoc" mateclaw-ui/src/
```

如果有命中，逐个编辑那些 .vue / .ts 文件，把 `t('about.readMateClawDoc')` 之类引用改为 `t('about.readQingwenClawsDoc')`。

Expected: 替换完成后再次 grep 应无输出。

### Task 2.3: 替换 en-US.ts 品牌词

**Files:**
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`

- [ ] **Step 1: 逐行替换品牌字串**

Edit `mateclaw-ui/src/i18n/locales/en-US.ts`:

| Line | Old | New |
|---|---|---|
| 3 | `title: 'MateClaw - AI Assistant',` | `title: 'QingwenClaws - AI Assistant',` |
| 599 | `aboutTitle: 'About MateClaw',` | `aboutTitle: 'About QingwenClaws',` |
| 738 | `... MateClaw does not modify Claude Code's on-disk credentials.',` | `... QingwenClaws does not modify Claude Code's on-disk credentials.',` |
| 1519 | `dashscope: '... same value as MateClaw .env DASHSCOPE_API_KEY',` | `dashscope: '... same value as QingwenClaws .env DASHSCOPE_API_KEY',` |
| 1712 | `emptyHint: 'Place plugin JAR files in ~/.mateclaw/plugins/ and restart the server',` | `emptyHint: 'Place plugin JAR files in ~/.qingwenclaws/plugins/ and restart the server',` |
| 2432 | `heroDesc: 'MateClaw is not about stuffing more capability...` | `heroDesc: 'QingwenClaws is not about stuffing more capability...` |
| 2650 | `webchatHint: 'WebChat embeds the MateClaw chat widget...` | `webchatHint: 'WebChat embeds the QingwenClaws chat widget...` |
| 3092 | `title: 'Welcome to MateClaw',` | `title: 'Welcome to QingwenClaws',` |
| 3112 | `startUsing: 'Start Using MateClaw',` | `startUsing: 'Start Using QingwenClaws',` |
| 3310 | `readMateClawDoc: 'Read System Docs',` | `readQingwenClawsDoc: 'Read System Docs',` |

- [ ] **Step 2: 验证替换完整**

Run: `grep -n "MateClaw\|mateclaw" mateclaw-ui/src/i18n/locales/en-US.ts`

Expected: 无输出。

### Task 2.4: 修复 ChatConsole.vue 的 localStorage key

**Files:**
- Modify: `mateclaw-ui/src/views/ChatConsole.vue`

- [ ] **Step 1: 替换 localStorage key**

Edit `mateclaw-ui/src/views/ChatConsole.vue`:

old (line 456-458):
```typescript
const thinkingEnabled = ref(localStorage.getItem('mateclaw_thinking') !== 'off')

watch(thinkingEnabled, (v) => localStorage.setItem('mateclaw_thinking', v ? 'on' : 'off'))
```

new:
```typescript
const thinkingEnabled = ref(localStorage.getItem('qingwenclaws_thinking') !== 'off')

watch(thinkingEnabled, (v) => localStorage.setItem('qingwenclaws_thinking', v ? 'on' : 'off'))
```

- [ ] **Step 2: 验证**

Run: `grep -n "mateclaw_thinking\|qingwenclaws_thinking" mateclaw-ui/src/views/ChatConsole.vue`

Expected: 两行命中都是 `qingwenclaws_thinking`。

### Task 2.5: 检查 App.vue 的 VITE_APP_TITLE 与 logo 引用

**Files:**
- Modify: `mateclaw-ui/src/App.vue`

- [ ] **Step 1: 读 App.vue 并定位 logo / title 引用**

Run:
```bash
grep -n "logo\|VITE_APP_TITLE\|MateClaw\|mateclaw" mateclaw-ui/src/App.vue
```

记录所有命中行号。

- [ ] **Step 2: 替换 logo 路径**

对每个 `/logo/mateclaw_logo.png` 或 `/logo/mateclaw_logo_s.png` 引用，改成：
- `/logo/qingwenclaws_logo.png` 或 `/logo/qingwenclaws_logo_s.png`
- 优先用 SVG：`/logo/qingwenclaws_logo.svg` / `/logo/qingwenclaws_logo_s.svg`

对 `VITE_APP_TITLE` 引用，确认 fallback 字符串里没有 'MateClaw'，如果有则改为 'QingwenClaws'。

- [ ] **Step 3: 验证**

Run: `grep -n "MateClaw\|mateclaw_logo" mateclaw-ui/src/App.vue`

Expected: 无输出。

### Task 2.6: 全局搜索 logo 引用并替换

**Files:**
- Modify: any `.vue` / `.ts` / `.css` 文件中引用 `mateclaw_logo` 的位置

- [ ] **Step 1: 搜索 UI 项目所有 logo 引用**

Run:
```bash
grep -rn "mateclaw_logo\|MateClaw" mateclaw-ui/src/
```

- [ ] **Step 2: 逐一替换**

对每个命中：
- `mateclaw_logo.png` → `qingwenclaws_logo.png`（或 `.svg`）
- `mateclaw_logo_s.png` → `qingwenclaws_logo_s.png`（或 `.svg`）
- 任何剩余 `MateClaw` 字面量 → `QingwenClaws`

- [ ] **Step 3: 验证**

Run: `grep -rn "mateclaw_logo\|MateClaw" mateclaw-ui/src/`

Expected: 无输出。

### Task 2.7: 引入 Design Tokens

**Files:**
- Modify: `mateclaw-ui/src/assets/main.css`

- [ ] **Step 1: 在 main.css 顶部添加 --qwc-* tokens**

读取 `mateclaw-ui/src/assets/main.css`，找到现有 `:root` 块（应该已有 `--mc-*` tokens）。

在 `:root { ... }` 内部追加：

```css
/* QingwenClaws brand tokens — primary palette */
--qwc-bg-deep:      #0d1b2a;
--qwc-bg-mid:       #1b2f4b;
--qwc-cyan-deep:    #0077a8;
--qwc-cyan-primary: #00b4d8;
--qwc-cyan-light:   #48cae4;
--qwc-cyan-pale:    #caf0f8;
--qwc-white:        #ffffff;
```

- [ ] **Step 2: 验证 token 添加**

Run: `grep -n "qwc-" mateclaw-ui/src/assets/main.css`

Expected: 至少 7 行命中。

> **不要在本 task 里改任何 --mc-* 引用** — 保持现有样式不动，新 token 作为后续视觉迭代的基础。这是品牌定制的"轻接入"策略，避免 UI 视觉大改。

### Task 2.8: 构建验证 UI

- [ ] **Step 1: 装依赖并 lint**

Run:
```bash
cd mateclaw-ui
pnpm install
pnpm lint
```

Expected: zero errors（warning 可接受）。

- [ ] **Step 2: 构建**

Run:
```bash
pnpm build
```

Expected: 构建成功，dist/ 生成。无 type 错误。

- [ ] **Step 3: 字串清扫验证**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
grep -rn "MateClaw\|mateclaw" mateclaw-ui/src/ mateclaw-ui/public/ mateclaw-ui/index.html 2>/dev/null
```

Expected: 无输出（注意 dist/ 已被 .gitignore，不会命中）。

### Task 2.9: Phase 2 提交

- [ ] **Step 1: 提交 UI 改造**

Run:
```bash
git add mateclaw-ui/
git status
git commit -m "$(cat <<'EOF'
feat(brand): rebrand UI surface from MateClaw to QingwenClaws

- index.html title + favicon (svg + ico)
- i18n locales (zh-CN, en-US): 10 brand string per file
- ChatConsole localStorage key: mateclaw_thinking → qingwenclaws_thinking
- App.vue and other components: logo asset references
- main.css: introduce --qwc-* design tokens (additive, no --mc-* changes)

Verified: pnpm lint + pnpm build green, grep MateClaw/mateclaw_logo in src/ empty.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Expected: commit 成功。

---

## Phase 3 · 后端运行时改造（Day 2）

### Task 3.1: 修改 pom.xml（mateclaw-server）

**Files:**
- Modify: `mateclaw-server/pom.xml`

- [ ] **Step 1: 修改 finalName 和 name/description**

Edit `mateclaw-server/pom.xml`:

| Line | Old | New |
|---|---|---|
| 12 | `<name>MateClaw Server</name>` | `<name>QingwenClaws Server</name>` |
| 13 | `<description>MateClaw - Java+Vue Personal AI Assistant powered by Spring AI Alibaba</description>` | `<description>QingwenClaws - Enterprise Personal Agent Assistant</description>` |

找到 `<build>` 块中（如果有 `<finalName>` 标签），加入或修改：
```xml
<finalName>qingwenclaws-server</finalName>
```

如果没有 `<finalName>` 标签，在 `<build>` 第一行加入。

> **不要改** `artifactId` 行（mateclaw-server），它是 OUT-of-scope。
> **不要改** 注释里的 `MateClaw Plugin API`（line 57）和 `MateClaw 不需要语音功能`（line 252）—— 等下一步统一改注释。

- [ ] **Step 2: 修改注释中的 MateClaw**

Edit `mateclaw-server/pom.xml`:

| Line | Old | New |
|---|---|---|
| 57 | `<!-- ===== MateClaw Plugin API ===== -->` | `<!-- ===== QingwenClaws Plugin API ===== -->` |
| 252 | `<!-- 排除 audio 相关依赖（MateClaw 不需要语音功能） -->` | `<!-- 排除 audio 相关依赖（QingwenClaws 不需要语音功能） -->` |

- [ ] **Step 3: 验证 maven 解析**

Run:
```bash
cd mateclaw-server
mvn help:effective-pom -q | grep -E "finalName|<name>|<description>" | head -5
```

Expected: 输出包含 `<finalName>qingwenclaws-server</finalName>` 和 `<name>QingwenClaws Server</name>`。

### Task 3.2: 修改 application.yml

**Files:**
- Modify: `mateclaw-server/src/main/resources/application.yml`

> **关键 OUT 范围提醒**：line 126 的 `mateclaw:` 是 `@ConfigurationProperties(prefix="mateclaw")` 的绑定根键，**不要改**。其下属配置（line 127+）也不要改 key 名。

- [ ] **Step 1: 改 spring.application.name**

old (line 8):
```yaml
    name: mateclaw-server
```

new:
```yaml
    name: qingwenclaws-server
```

- [ ] **Step 2: 改 H2 数据库文件路径**

old (line 24):
```yaml
    url: jdbc:h2:file:./data/mateclaw;MODE=MySQL;...
```

new:
```yaml
    url: jdbc:h2:file:./data/qingwenclaws;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
```

> 完整 url 其余部分保持不变。

- [ ] **Step 3: 改 JWT 默认 secret 字面量**

old (line 128, 在 `mateclaw:` 块下):
```yaml
    secret: ${JWT_SECRET:MateClaw-JWT-Secret-Key-2024-Please-Change-In-Production}
```

new:
```yaml
    secret: ${JWT_SECRET:QingwenClaws-JWT-Secret-Key-2026-Please-Change-In-Production}
```

> 注意：**只改字面量默认值**（即 `:` 后到 `}` 前），不改 `${JWT_SECRET:...}` 的环境变量名 `JWT_SECRET`。

- [ ] **Step 4: 改用户数据目录**

old (line 136):
```yaml
      root: ${user.home}/.mateclaw/skills
```
new:
```yaml
      root: ${user.home}/.qingwenclaws/skills
```

old (line 148):
```yaml
    user-dir: ${user.home}/.mateclaw/plugins
```
new:
```yaml
    user-dir: ${user.home}/.qingwenclaws/plugins
```

- [ ] **Step 5: 改注释**

| Line | Old | New |
|---|---|---|
| 58 | `# mateclaw's runtime header parser (McpClientManager.parseHeaders) can` | `# qingwenclaws's runtime header parser (McpClientManager.parseHeaders) can` |
| 125 | `# MateClaw 自定义配置` | `# QingwenClaws 自定义配置（注：内部 YAML 根键 mateclaw: 保留以维持 Java @ConfigurationProperties 绑定）` |
| 192 | `# MateClaw Agent 配置` | `# QingwenClaws Agent 配置` |

- [ ] **Step 6: 验证**

Run:
```bash
grep -n "mateclaw\|MateClaw" mateclaw-server/src/main/resources/application.yml | \
  grep -vE "^[0-9]+:\s*mateclaw:|^[0-9]+:.*mateclaw\."
```

Expected: 应只显示 OUT-of-scope 的命中（即 `mateclaw:` 根键和它下面的 `mateclaw.xxx` 路径），其它全清干净。

### Task 3.3: 修改 application-mysql.yml

**Files:**
- Modify: `mateclaw-server/src/main/resources/application-mysql.yml`

- [ ] **Step 1: 改 MySQL 默认 DB 名 + 密码 + 注释**

Edit `mateclaw-server/src/main/resources/application-mysql.yml`:

| Line | Old | New |
|---|---|---|
| 7 | `    #   CREATE DATABASE mateclaw CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` | `    #   CREATE DATABASE qingwenclaws CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` |
| 16 | `${DB_NAME:mateclaw}` (in jdbc url) | `${DB_NAME:qingwenclaws}` |
| 19 | `    password: ${DB_PASSWORD:mateclaw123}` | `    password: ${DB_PASSWORD:qingwenclaws123}` |

- [ ] **Step 2: 验证**

Run: `grep -n "mateclaw\|MateClaw" mateclaw-server/src/main/resources/application-mysql.yml`

Expected: 无输出。

### Task 3.4: 修改 messages.properties

**Files:**
- Modify: `mateclaw-server/src/main/resources/messages.properties`
- Modify: `mateclaw-server/src/main/resources/messages_en.properties`

- [ ] **Step 1: 改 messages.properties 标题注释**

old (line 1):
```properties
# ==================== MateClaw i18n: zh-CN (default) ====================
```

new:
```properties
# ==================== QingwenClaws i18n: zh-CN (default) ====================
```

- [ ] **Step 2: 改 messages_en.properties 标题注释**

old (line 1):
```properties
# ==================== MateClaw i18n: en-US ====================
```

new:
```properties
# ==================== QingwenClaws i18n: en-US ====================
```

- [ ] **Step 3: 全文搜索其他命中**

Run:
```bash
grep -n "MateClaw\|mateclaw" mateclaw-server/src/main/resources/messages.properties mateclaw-server/src/main/resources/messages_en.properties
```

如果有更多命中，逐一替换。

Expected: 替换完所有命中后再 grep 应无输出。

### Task 3.5: 修改 Dockerfile

**Files:**
- Modify: `mateclaw-server/Dockerfile`

> Dockerfile 里大量出现 `mateclaw-ui/`、`mateclaw-server/`、`mateclaw-plugin-api/` 等 **路径引用**，这些都是 OUT 范围（模块目录名）。**只改 LABEL 和构建产物名**，不动 COPY 路径。

- [ ] **Step 1: 找 LABEL 和注释**

Run:
```bash
grep -nE "LABEL|^#" mateclaw-server/Dockerfile | grep -i "mateclaw\|MateClaw" | head -10
```

- [ ] **Step 2: 改 LABEL**

如果 Dockerfile 里有 `LABEL maintainer=...` 或 `LABEL org.opencontainers.image.title=...` 等，替换品牌字串：

- `LABEL maintainer="MateClaw"` → `LABEL maintainer="QingwenClaws (擎问科技)"`
- 任何 `image.title` / `image.description` 含 `MateClaw` → `QingwenClaws`

> 如果 Dockerfile 当前没有 LABEL，新增到顶部：

```dockerfile
LABEL org.opencontainers.image.title="QingwenClaws Server"
LABEL org.opencontainers.image.description="QingwenClaws - Enterprise Personal Agent Assistant"
LABEL org.opencontainers.image.vendor="擎问科技 (Qingwen Tech)"
```

- [ ] **Step 3: 改注释里的品牌字串**

old:
```dockerfile
# Pre-fetch mateclaw-server dependencies (uses mirror, so this won't hang)
```
new:
```dockerfile
# Pre-fetch qingwenclaws-server dependencies (uses mirror, so this won't hang)
```

> **不改** COPY 行里的 `mateclaw-ui/`、`mateclaw-server/`、`mateclaw-plugin-api/` 路径（模块目录名 OUT）。

- [ ] **Step 4: 改构建产物 JAR 名引用**

如果 Dockerfile 里有 `ENTRYPOINT java -jar mateclaw-server-*.jar` 或类似引用旧 JAR 名的，改为 `qingwenclaws-server-*.jar`。

- [ ] **Step 5: 验证**

Run:
```bash
grep -n "mateclaw\|MateClaw" mateclaw-server/Dockerfile | \
  grep -vE "(mateclaw-(server|ui|plugin-api)/|mateclaw-plugin\.json)"
```

Expected: 无输出。

### Task 3.6: 后端构建 + 测试验证

- [ ] **Step 1: Maven 构建**

Run:
```bash
cd mateclaw-server
mvn clean package -DskipTests
ls target/*.jar
```

Expected: 构建成功，target/ 下有 `qingwenclaws-server-*.jar`（不再是 `mateclaw-server-*.jar`）。

- [ ] **Step 2: 跑测试套件**

Run:
```bash
mvn test 2>&1 | tee /tmp/mvn-test-phase3.log | tail -50
```

Expected: BUILD SUCCESS, Tests run: ..., Failures: 0, Errors: 0。

> 如果有失败，常见原因：
> - 测试硬编码了 `mateclaw-server` JAR 名 → 改测试
> - 测试硬编码了 H2 路径 `data/mateclaw.mv.db` → 改测试
> - i18n 测试期望 `MateClaw` 字串 → 改测试期望
>
> 不要为了让测试通过而回滚 spec 改造，应改测试断言。

### Task 3.7: Phase 3 提交

- [ ] **Step 1: 提交后端运行时改造**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
git add mateclaw-server/pom.xml mateclaw-server/Dockerfile \
        mateclaw-server/src/main/resources/application.yml \
        mateclaw-server/src/main/resources/application-mysql.yml \
        mateclaw-server/src/main/resources/messages.properties \
        mateclaw-server/src/main/resources/messages_en.properties \
        mateclaw-server/src/test/  # 如果改了测试
git status
git commit -m "$(cat <<'EOF'
feat(brand): rebrand backend runtime artifacts to QingwenClaws

- pom.xml: finalName qingwenclaws-server, project name/description
- application.yml: spring.application.name, H2 path, JWT default secret,
  ~/.qingwenclaws/{skills,plugins} user data dirs
- application-mysql.yml: default DB name + password
- Dockerfile: LABEL metadata + build artifact name
- messages.properties (zh/en): comment headers

OUT-of-scope (intentionally kept): YAML root key `mateclaw:` and its
sub-paths (Java @ConfigurationProperties binding); see UPGRADING.md.

Verified: mvn package green, JAR name = qingwenclaws-server-*.jar, mvn test green.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4 · 种子数据 + Java 字符串字面量（Day 3）

### Task 4.1: 种子数据品牌化 — data-zh.sql

**Files:**
- Modify: `mateclaw-server/src/main/resources/db/data-zh.sql`

- [ ] **Step 1: 全文 sed 批量替换**

> 这个文件有 ~48 处命中，先用 sed 批量替换最常见的几种字面量模式，然后用 grep 检查剩余的，逐一手工处理。

Run:
```bash
cd mateclaw-server/src/main/resources/db
cp data-zh.sql data-zh.sql.bak

# 主品牌词替换（区分大小写）
sed -i.tmp \
  -e 's/MateClaw 初始数据/QingwenClaws 初始数据/g' \
  -e 's/MateClaw Admin/QingwenClaws Admin/g' \
  -e "s/你是 MateClaw 的通用助手/你是 QingwenClaws 的通用助手/g" \
  -e 's/MateClaw 项目文档读取/QingwenClaws 项目文档读取/g' \
  -e 's/MateClawDocTool/QingwenClawsDocTool/g' \
  -e 's/mateClawDocTool/qingwenClawsDocTool/g' \
  -e 's/MateClaw 文档/QingwenClaws 文档/g' \
  -e "s/读取 MateClaw 内置项目文档/读取 QingwenClaws 内置项目文档/g" \
  -e 's/Filesystem MCP for MateClaw workspace/Filesystem MCP for QingwenClaws workspace/g' \
  -e 's/参考 MateClaw 文档/参考 QingwenClaws 文档/g' \
  -e 's/从 MateClaw 迁移的技能元数据/从 QingwenClaws 迁移的技能元数据/g' \
  -e "s/'MateClaw'/'QingwenClaws'/g" \
  -e 's/"upstream":"mateclaw"/"upstream":"qingwenclaws"/g' \
  -e 's/MateClaw 安装与配置/QingwenClaws 安装与配置/g' \
  -e 's/MateClaw 文档路径与源码入口/QingwenClaws 文档路径与源码入口/g' \
  data-zh.sql

rm -f data-zh.sql.tmp
```

> 注意：**保留** `mate_skill`、`mate_*` 表名（DB 表前缀 OUT）。也**保留** Java 类名引用如 `MateClawException` 之类（OUT）。
>
> **保留** `MateClawDocTool` 类名引用如果它出现在 builtin tool 的 `bean_name` 字段中——sed 已经替换了 `mateClawDocTool`（小驼峰 bean name）和 `MateClawDocTool`（display name 字符串），如果有别的引用对应 Java 类（如 `vip.mate.tool.MateClawDocTool`），需要回退那行。

- [ ] **Step 2: 检查 Java 类引用回退**

Run:
```bash
grep -n "MateClawDocTool\|mateClawDocTool" data-zh.sql
```

如果还有命中，确认它是字符串值还是 Java 类引用：
- 如果是 `bean_name = 'mateClawDocTool'` 这种 Spring bean 名引用 Java 类 → **回退**（这是 Java 端约定，OUT）
- 如果是 `display_name = 'MateClawDocTool'` 这种纯展示字符串 → 已替换为 `QingwenClawsDocTool`

> 决策：保险起见，Spring bean name 字段（`bean_name`、`bean_id` 等）保留旧名。其他字符串字段都改。

如果 sed 把 bean name 也改了，用以下命令回退：
```bash
sed -i.tmp -e "s/'qingwenClawsDocTool'/'mateClawDocTool'/g" data-zh.sql
rm -f data-zh.sql.tmp
```

- [ ] **Step 3: 检查 mateclaw 小写命中**

Run:
```bash
grep -n "mateclaw" data-zh.sql
```

如果有命中（如 `"upstream":"mateclaw"`），但 step 1 的 sed 已处理过，那应该是剩余的。逐一确认：
- 元数据字段 `upstream":"mateclaw"` → 已批量改为 `qingwenclaws`
- 文件路径 `/.mateclaw/skills` → 改为 `/.qingwenclaws/skills`
- 其他 → 视语境决定

跑：
```bash
sed -i.tmp -e 's|/\.mateclaw/|/.qingwenclaws/|g' data-zh.sql
rm -f data-zh.sql.tmp
grep -n "mateclaw" data-zh.sql
```

如果仍有命中，列出来逐一手工编辑。

- [ ] **Step 4: 删除 sed 备份**

Run:
```bash
rm -f data-zh.sql.bak
```

### Task 4.2: 种子数据品牌化 — data-en.sql

**Files:**
- Modify: `mateclaw-server/src/main/resources/db/data-en.sql`

- [ ] **Step 1: 检查 en.sql 的命中模式**

Run:
```bash
cd mateclaw-server/src/main/resources/db
grep -c "MateClaw\|mateclaw" data-en.sql
grep -n "MateClaw\|mateclaw" data-en.sql | head -30
```

- [ ] **Step 2: 批量替换**

```bash
cp data-en.sql data-en.sql.bak

sed -i.tmp \
  -e 's/MateClaw Admin/QingwenClaws Admin/g' \
  -e "s/You are MateClaw/You are QingwenClaws/g" \
  -e "s/'MateClaw'/'QingwenClaws'/g" \
  -e 's/MateClaw [Dd]oc/QingwenClaws Doc/g' \
  -e 's/MateClawDocTool/QingwenClawsDocTool/g' \
  -e 's/MateClaw workspace/QingwenClaws workspace/g' \
  -e 's/MateClaw [Ss]kill/QingwenClaws Skill/g' \
  -e 's/MateClaw [Pp]roject/QingwenClaws project/g' \
  -e 's/"upstream":"mateclaw"/"upstream":"qingwenclaws"/g' \
  -e 's|/\.mateclaw/|/.qingwenclaws/|g' \
  data-en.sql

rm -f data-en.sql.tmp
```

- [ ] **Step 3: 逐行检查剩余命中**

Run: `grep -n "MateClaw\|mateclaw" data-en.sql`

对每个剩余命中：判断是字符串值（替换）还是 Java/Spring bean 引用（保留）。逐一处理。

- [ ] **Step 4: 删除备份**

Run: `rm -f data-en.sql.bak`

### Task 4.3: 种子数据品牌化 — data-mysql-{zh,en}.sql

**Files:**
- Modify: `mateclaw-server/src/main/resources/db/data-mysql-zh.sql`
- Modify: `mateclaw-server/src/main/resources/db/data-mysql-en.sql`

- [ ] **Step 1: 比较 h2 和 mysql 版本差异**

Run:
```bash
cd mateclaw-server/src/main/resources/db
diff data-zh.sql data-mysql-zh.sql | head -20
diff data-en.sql data-mysql-en.sql | head -20
```

> 两个版本应该结构相同，仅 SQL 方言不同（H2 的 MERGE INTO vs MySQL 的 INSERT ... ON DUPLICATE KEY）。

- [ ] **Step 2: 应用相同的 sed 批量替换到 mysql 版本**

复用 Task 4.1 / 4.2 的 sed 命令：

```bash
# zh mysql
cp data-mysql-zh.sql data-mysql-zh.sql.bak
sed -i.tmp \
  -e 's/MateClaw 初始数据/QingwenClaws 初始数据/g' \
  -e 's/MateClaw Admin/QingwenClaws Admin/g' \
  -e "s/你是 MateClaw 的通用助手/你是 QingwenClaws 的通用助手/g" \
  -e 's/MateClaw 项目文档读取/QingwenClaws 项目文档读取/g' \
  -e 's/MateClawDocTool/QingwenClawsDocTool/g' \
  -e 's/mateClawDocTool/qingwenClawsDocTool/g' \
  -e 's/MateClaw 文档/QingwenClaws 文档/g' \
  -e "s/读取 MateClaw 内置项目文档/读取 QingwenClaws 内置项目文档/g" \
  -e 's/Filesystem MCP for MateClaw workspace/Filesystem MCP for QingwenClaws workspace/g' \
  -e 's/参考 MateClaw 文档/参考 QingwenClaws 文档/g' \
  -e 's/从 MateClaw 迁移的技能元数据/从 QingwenClaws 迁移的技能元数据/g' \
  -e "s/'MateClaw'/'QingwenClaws'/g" \
  -e 's/"upstream":"mateclaw"/"upstream":"qingwenclaws"/g' \
  -e 's/MateClaw 安装与配置/QingwenClaws 安装与配置/g' \
  -e 's/MateClaw 文档路径与源码入口/QingwenClaws 文档路径与源码入口/g' \
  -e 's|/\.mateclaw/|/.qingwenclaws/|g' \
  data-mysql-zh.sql

# 回退 bean name
sed -i.tmp -e "s/'qingwenClawsDocTool'/'mateClawDocTool'/g" data-mysql-zh.sql
rm -f data-mysql-zh.sql.tmp data-mysql-zh.sql.bak

# en mysql
cp data-mysql-en.sql data-mysql-en.sql.bak
sed -i.tmp \
  -e 's/MateClaw Admin/QingwenClaws Admin/g' \
  -e "s/You are MateClaw/You are QingwenClaws/g" \
  -e "s/'MateClaw'/'QingwenClaws'/g" \
  -e 's/MateClaw [Dd]oc/QingwenClaws Doc/g' \
  -e 's/MateClawDocTool/QingwenClawsDocTool/g' \
  -e 's/MateClaw workspace/QingwenClaws workspace/g' \
  -e 's/MateClaw [Ss]kill/QingwenClaws Skill/g' \
  -e 's/MateClaw [Pp]roject/QingwenClaws project/g' \
  -e 's/"upstream":"mateclaw"/"upstream":"qingwenclaws"/g' \
  -e 's|/\.mateclaw/|/.qingwenclaws/|g' \
  data-mysql-en.sql

sed -i.tmp -e "s/'qingwenClawsDocTool'/'mateClawDocTool'/g" data-mysql-en.sql
rm -f data-mysql-en.sql.tmp data-mysql-en.sql.bak
```

- [ ] **Step 3: 验证 4 个 SQL 文件保持镜像一致**

Run:
```bash
diff <(grep -v "^--\|^MERGE\|^INSERT\|^UPDATE\|^DELETE\|^SET" data-zh.sql) \
     <(grep -v "^--\|^MERGE\|^INSERT\|^UPDATE\|^DELETE\|^SET" data-mysql-zh.sql) | head -20
```

> 如果差异很大，说明两个文件结构就不一样（正常 — SQL 方言不同）。重点是检查品牌字串两边都改了。

Run:
```bash
grep -c "MateClaw\|mateclaw" data-zh.sql data-en.sql data-mysql-zh.sql data-mysql-en.sql
```

Expected: 四个文件的 mateclaw 命中数都应**等于** OUT 范围预期（如 mate_skill 表前缀、Java bean name 等），其余都是 0。

### Task 4.4: 后端 Java 源码品牌字符串扫描与改造

**Files:**
- Modify: `mateclaw-server/src/main/java/**/*.java`（按 grep 结果选定）
- Modify: `mateclaw-server/src/main/resources/prompts/**/*.txt`

> 这一步是范围最难界定的：Java 源码里有 200+ 文件出现 `MateClaw`/`mateclaw` 字串，其中绝大多数是 **类名引用**（OUT）和 **JavaDoc 注释**。
>
> 真正需要改的只有：
> 1. **用户可见的字符串字面量** — 错误消息、prompt 模板、日志 user-facing 文本
> 2. **HTTP 响应里的自报家门字段**（如 `Server: mateclaw/1.3.0` header）
>
> 我们用 grep + 人工 review 的两阶段方式处理。

- [ ] **Step 1: 找出所有 Java 字符串字面量命中**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
grep -rn --include="*.java" '"[^"]*MateClaw[^"]*"\|"[^"]*mateclaw[^"]*"' mateclaw-server/src/main/java/ > /tmp/java-string-hits.txt
wc -l /tmp/java-string-hits.txt
head -30 /tmp/java-string-hits.txt
```

记录命中数量，预计 30–80 行。

- [ ] **Step 2: 分类逐行 review**

打开 `/tmp/java-string-hits.txt` 逐行判断：

| 模式 | 决策 |
|---|---|
| 异常消息 `throw new MateClawException("err.xxx", "..MateClaw..")` 的**中文消息部分** | 改 |
| 异常消息的 `err.xxx` **i18n key** | 不改（i18n key 是协议） |
| 日志 `log.info("MateClaw started...")` | 改 |
| Bean ID `@Bean("mateClawXxx")` | 不改（Java bean 名约定，OUT） |
| 类引用 `MateClawStateKeys.xxx` | 不改（类名 OUT） |
| Spring `@Value("${mateclaw.xxx}")` | 不改（YAML 配置键 OUT） |
| HTTP response header `Server` 之类 | 改 |
| User-Agent 字符串 | 改 |
| Annotation `@Component("mateclawXxx")` | 不改 |

对每个"改"的命中，编辑对应文件，把字面量里的 `MateClaw` → `QingwenClaws`，`mateclaw` → `qingwenclaws`（注意大小写场景）。

- [ ] **Step 3: 处理 prompt 模板**

Run:
```bash
grep -rln "MateClaw\|mateclaw" mateclaw-server/src/main/resources/prompts/ 2>/dev/null
```

每个命中文件：打开，把 prompt 文本里的 `MateClaw` 改为 `QingwenClaws`。这些是 LLM 看到的系统提示词，必须改。

- [ ] **Step 4: 跑测试套件验证**

Run:
```bash
cd mateclaw-server
mvn test 2>&1 | tail -30
```

Expected: BUILD SUCCESS。

> 测试可能因品牌字串期望失败，遵循 Task 3.6 的处理原则。

### Task 4.5: Phase 4 提交

- [ ] **Step 1: 提交种子数据 + 后端字串改造**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
git add mateclaw-server/src/main/resources/db/data-*.sql \
        mateclaw-server/src/main/java/ \
        mateclaw-server/src/main/resources/prompts/ \
        mateclaw-server/src/test/
git status
git commit -m "$(cat <<'EOF'
feat(brand): rebrand DB seed data and backend string literals to QingwenClaws

- Seed SQL (4 files, h2 + mysql, zh + en): default admin name, demo agents,
  built-in tools, skills metadata, MCP server examples
- Java source: user-facing exception messages, log messages, prompt templates,
  HTTP response self-identification strings
- Spring bean names, Java class names, @Value config keys, JavaDoc kept as-is
  (OUT-of-scope)

Verified: mvn test green; grep MateClaw in src/main/resources excluded
expected OUT-of-scope hits only.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5 · 文档改造（Day 3–4）

### Task 5.1: 重写 README.md（英文产品介绍）

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 完全重写 README.md 为 QingwenClaws 产品介绍**

Write `README.md`:

```markdown
# QingwenClaws

> Enterprise Personal Agent Assistant — pull context, knowledge, execution, and memory into one reliable operating surface.

**QingwenClaws** is an enterprise-grade personal AI agent platform that goes beyond chat. It is designed to be the operating surface for knowledge workers, combining context awareness, retrievable memory, executable tooling, and multi-channel delivery into a single deployable product.

Built and operated by 擎问科技 (Qingwen Tech).

---

## What it does

- **Digital Employee runtime** — ReAct + Plan-Execute agent graph with per-request model routing, failover, and health tracking
- **Multi-provider LLM** — DashScope, OpenAI, Anthropic, and more, with key resolution and cooldown
- **LLM Wiki** — chunking, embedding, retrieval, hot cache; map-reduce transformations over raw materials
- **Workspace memory** — `AGENTS.md`, `SOUL.md`, `PROFILE.md`, lifecycle extraction, scheduled consolidation
- **Skill packages** — `SKILL.md` manifest + prompts + tool list + self-evolving `LESSONS.md`
- **Tool Guard** — RBAC + human-in-the-loop approval for sensitive tool calls
- **Workflow engine** — linear DSL with sequential / fan_out / collect / conditional / await_approval / dispatch_channel / write_memory step modes
- **Trigger system** — cron / webhook / channel_message / agent_lifecycle / content_match / workflow_completion patterns with built-in dedup, rate-limit, and recursion guard
- **IM channels** — DingTalk, Feishu, WeCom, WeChat, Telegram, Discord, QQ, Slack
- **Bridge external agents** — Claude Code, Codex, and other ACP-compatible coding agents wrapped as digital employees

## Quick start (Docker)

```bash
cp .env.example .env   # fill in DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET
docker compose up -d   # http://localhost:18080
```

Default login: `admin` / `admin123` — change immediately in production.

## Local development

| Component | Path | Commands |
|---|---|---|
| Backend | `mateclaw-server/` | `mvn spring-boot:run` → http://localhost:18088 |
| Admin SPA | `mateclaw-ui/` | `pnpm install && pnpm dev` → http://localhost:5173 |
| Webchat widget | `mateclaw-webchat/` | `pnpm install && pnpm dev` |

See `CLAUDE.md` and per-subproject `CLAUDE.md` for engineering rules.

## Deployment surfaces

QingwenClaws is one JAR with multiple surfaces:
1. Admin SPA (bundled into JAR static resources)
2. Webchat widget (embeddable into external sites)
3. Desktop app (Electron shell + bundled JRE 21)
4. IM channel adapters
5. Java plugin SDK (for first-party / partner extensions)

## License

QingwenClaws is built on Apache 2.0 open source foundations and is itself
distributed by 擎问科技 (Qingwen Tech). See LICENSE.

---

© 2026 擎问科技 (Qingwen Tech). All rights reserved.
```

> 注意：保留了模块路径 `mateclaw-server/` 等 —— 这些是物理目录名（OUT）。客户读 README 时不会看到这些（只在 dev quick-start 下）。

- [ ] **Step 2: 验证**

Run: `head -40 README.md`

Expected: 第一行是 `# QingwenClaws`，没有 `mateclaw` 字样在用户可见标题/介绍区域。

### Task 5.2: 重写 README_zh.md（中文产品介绍）

**Files:**
- Modify: `README_zh.md`

- [ ] **Step 1: 完全重写为中文版**

Write `README_zh.md`:

```markdown
# QingwenClaws

> 企业级个人 Agent 助手平台 — 把上下文、知识、执行与记忆收拢成一个可靠的工作面。

**QingwenClaws** 是一款面向企业个人用户的 AI Agent 平台。它不是把更多能力堆进聊天框，而是把上下文感知、可检索的记忆、可执行的工具、多渠道交付能力，统一成一个可部署的商业产品。

由擎问科技构建与运营。

---

## 核心能力

- **Digital Employee 运行时** — ReAct + Plan-Execute Agent 图，按请求做模型路由、降级与健康跟踪
- **多模型 Provider 接入** — DashScope、OpenAI、Anthropic 等，支持按请求解析 key、冷却恢复
- **LLM Wiki** — 分块、向量化、检索、热缓存；对原料做 map-reduce 转换
- **工作区记忆** — `AGENTS.md`、`SOUL.md`、`PROFILE.md` 等记忆载体；生命周期抽取、定时整合
- **技能包（SKILL.md）** — manifest + 提示词 + 工具清单 + 自演化 `LESSONS.md`
- **Tool Guard** — 敏感工具调用的 RBAC 授权 + 人工审批
- **工作流引擎** — 线性 DSL，支持顺序 / 扇出 / 汇聚 / 条件 / 等待审批 / 推送渠道 / 写入记忆 七种步骤
- **触发器** — 定时、webhook、渠道消息、Agent 生命周期、内容匹配、工作流完成 六类模式，内置去重、限流、防递归
- **IM 渠道接入** — 钉钉、飞书、企业微信、微信、Telegram、Discord、QQ、Slack
- **外部 Agent 桥接** — 把 Claude Code、Codex 等 ACP 兼容编码 Agent 封装为数字员工

## 快速开始（Docker）

```bash
cp .env.example .env   # 填入 DB_PASSWORD、DB_ROOT_PASSWORD、JWT_SECRET
docker compose up -d   # http://localhost:18080
```

默认登录：`admin` / `admin123` — 生产环境务必立即修改。

## 本地开发

| 组件 | 路径 | 命令 |
|---|---|---|
| 后端 | `mateclaw-server/` | `mvn spring-boot:run` → http://localhost:18088 |
| 管理后台 | `mateclaw-ui/` | `pnpm install && pnpm dev` → http://localhost:5173 |
| Webchat 挂件 | `mateclaw-webchat/` | `pnpm install && pnpm dev` |

详见 `CLAUDE.md` 和各子项目的 `CLAUDE.md`。

## 部署形态

QingwenClaws 是一个 JAR，承载多种部署形态：
1. 管理后台 SPA（打包进 JAR 静态资源）
2. Webchat 挂件（嵌入外部网站）
3. 桌面应用（Electron 壳 + 内置 JRE 21）
4. IM 渠道适配器
5. Java 插件 SDK（一方/合作伙伴扩展）

## 许可

QingwenClaws 构建于 Apache 2.0 开源基础之上，由擎问科技对外发行。详见 LICENSE。

---

© 2026 擎问科技 (Qingwen Tech). 保留所有权利。
```

- [ ] **Step 2: 验证**

Run: `head -40 README_zh.md`

Expected: 第一行 `# QingwenClaws`。

### Task 5.3: 改造 UPGRADING.md

**Files:**
- Modify: `UPGRADING.md`

- [ ] **Step 1: 找命中行**

Run: `grep -n "MateClaw\|mateclaw" UPGRADING.md | head -20`

- [ ] **Step 2: 逐行品牌词替换**

对每个命中行替换：
- `MateClaw` → `QingwenClaws`
- `mateclaw`（非模块路径）→ `qingwenclaws`

> 保留：`mateclaw-server/`、`mateclaw-ui/` 等模块路径引用（OUT）；保留 Java 包名、类名引用。

- [ ] **Step 3: 在文件末尾追加「品牌定制改造」章节**

Append to `UPGRADING.md`:

```markdown

---

## 品牌定制改造说明（QingwenClaws fork）

本仓库是擎问科技 (Qingwen Tech) 基于上游 mateclaw 的商业产品 fork，已做中改档位品牌定制改造（spec 见 `docs/superpowers/specs/2026-05-17-brand-customization-design.md`）。

### 保留 mateclaw 痕迹的位置（OUT-of-scope）

以下内部结构性命名**保留** mateclaw 字样以维持上游 sync 能力：

| 类别 | 例子 |
|---|---|
| Maven groupId | `vip.mate` |
| Java 包根 | `vip.mate.*` |
| Java 类名 | `MateClawException`、`MateClawStateKeys`、`MateClawDocTool` |
| 模块目录名 | `mateclaw-server/`、`mateclaw-ui/`、`mateclaw-webchat/`、`mateclaw-plugin-api/`、`mateclaw-plugin-sample/` |
| Maven artifactId | `mateclaw-server`、`mateclaw-plugin-api` |
| DB 表前缀 | `mate_*` |
| YAML 配置根键 | `application.yml` 里的 `mateclaw:` 根节点 |
| 环境变量前缀 | `MATECLAW_*` |
| Flyway 迁移文件名 | 历史 V*.sql 文件全锁定 |
| LICENSE | Apache 2.0 原文保留 |

### 上游 sync 冲突地图

下次从上游 `baseline/v1.3.x` merge 新代码，**冲突集中在这 8 处**（每次 sync 预估额外 1–2 小时）：

1. `mateclaw-server/pom.xml` 的 `<finalName>`、`<name>`、`<description>`
2. `mateclaw-server/src/main/resources/application.yml` 的 `spring.application.name`、H2 路径、用户数据目录、JWT secret 默认值、品牌注释
3. `mateclaw-server/src/main/resources/application-mysql.yml` 的默认 DB 名/密码
4. `docker-compose.yml` 服务名、镜像、container_name、默认 env
5. `mateclaw-server/Dockerfile` 的 LABEL 元数据
6. UI i18n 文件 `mateclaw-ui/src/i18n/locales/{zh-CN,en-US}.ts` 的品牌词（每次上游新增 key 含 `MateClaw` 都会冲突）
7. Seed SQL 文件 `mateclaw-server/src/main/resources/db/data-{zh,en,mysql-zh,mysql-en}.sql`（上游若改 demo 内容会冲突）
8. `README.md`、`README_zh.md`、`CLAUDE.md`（含各子模块 CLAUDE.md）、`.github/PULL_REQUEST_TEMPLATE.md`

### Sync 操作指引

建议每次 upstream sync 由有改造记忆的工程师操作。流程：

1. `git fetch upstream baseline/v1.3.x`
2. `git checkout -b sync/upstream-v1.3.x baseline/v1.3.0`
3. `git merge upstream/baseline/v1.3.x`
4. 解决冲突：对照"冲突地图"中的 8 个文件，**保留 QingwenClaws 品牌字串**，吸收上游的非品牌改动
5. 跑全量验证：`pnpm build` + `mvn package` + `mvn test` + `docker compose up -d` smoke
6. 跑字串清扫 grep（见下方）确认没有上游字串污染
7. 提 PR 到 `baseline/v1.3.0` 并请有改造记忆的人 review

### 字串清扫 grep（每次 sync 后必跑）

```bash
grep -RIin --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist \
  --exclude-dir=.git --exclude-dir=.superpowers \
  "mateclaw\|mate.claw" . \
  | grep -vE "(vip/mate|vip\.mate|mate_|mateclaw-(server|ui|webchat|plugin-api|plugin-sample)/|MateClaw|MATECLAW_|^[^:]+:[0-9]+:\s*mateclaw:|LICENSE)"
```

输出应为空（或仅有上游新增、需补改的命中）。

### H2 数据库迁移（旧部署升级）

如果你有基于 mateclaw v1.3.0 的存量 H2 部署需要升级到 QingwenClaws：

```bash
# 停服后
mv data/mateclaw.mv.db data/qingwenclaws.mv.db
mv data/mateclaw.trace.db data/qingwenclaws.trace.db  # 如果存在
# 重启服务
```

> 本仓库是 fresh fork，不预期存在存量部署，所以未提供自动迁移脚本。
```

- [ ] **Step 4: 验证**

Run:
```bash
grep -n "QingwenClaws fork\|冲突地图\|字串清扫" UPGRADING.md
```

Expected: 命中 3 行。

### Task 5.4: 改造根 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 找命中**

Run: `grep -n "MateClaw\|mateclaw" CLAUDE.md | head -30`

- [ ] **Step 2: 逐行替换品牌字串**

对每个非模块路径、非 Java 类名引用的命中：
- `MateClaw` → `QingwenClaws`
- `mateclaw`（小写）→ `qingwenclaws`

> **保留**：
> - 模块路径 `mateclaw-server/`、`mateclaw-ui/`、`mateclaw-webchat/`、`mateclaw-plugin-api/`、`mateclaw-plugin-sample/`
> - Java 类名引用 `MateClawXxx`
> - Maven artifactId、Java 包名引用
> - YAML 配置根键引用 `mateclaw:` 和 `mateclaw.*`
> - 环境变量引用 `MATECLAW_*`

> 这里的判断标准：如果改了会让开发同事找不到对应文件/类/配置 → 保留；如果是叙述性介绍/品牌指代 → 改。

- [ ] **Step 3: 验证**

Run: `grep -n "MateClaw\|mateclaw" CLAUDE.md`

Expected: 仅剩 OUT 范围的命中（模块路径、Java 类、配置键引用），其他全替换。

### Task 5.5: 改造子模块 CLAUDE.md

**Files:**
- Modify: `mateclaw-server/CLAUDE.md`
- Modify: `mateclaw-ui/CLAUDE.md`

- [ ] **Step 1: server CLAUDE.md 替换**

Run: `grep -n "MateClaw\|mateclaw" mateclaw-server/CLAUDE.md | head -20`

按 Task 5.4 的标准替换品牌字串。

- [ ] **Step 2: ui CLAUDE.md 替换**

Run: `grep -n "MateClaw\|mateclaw" mateclaw-ui/CLAUDE.md | head -20`

按 Task 5.4 的标准替换品牌字串。

- [ ] **Step 3: 验证**

Run:
```bash
grep -n "MateClaw\|mateclaw" mateclaw-server/CLAUDE.md mateclaw-ui/CLAUDE.md
```

Expected: 仅剩 OUT 命中。

### Task 5.6: 改造 PR template

**Files:**
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`（当前为 untracked，本 task 一并处理）

- [ ] **Step 1: 检查 PR template 现状**

Run: `cat .github/PULL_REQUEST_TEMPLATE.md`

- [ ] **Step 2: 替换品牌字串**

把 PR template 里所有 `MateClaw` / `mateclaw`（非结构性命名）改为 `QingwenClaws` / `qingwenclaws`。如果没有命中，跳过 step 2。

- [ ] **Step 3: 验证**

Run: `grep -n "MateClaw\|mateclaw" .github/PULL_REQUEST_TEMPLATE.md`

Expected: 仅剩 OUT 命中或无输出。

### Task 5.7: Phase 5 提交

- [ ] **Step 1: 提交文档改造**

Run:
```bash
git add README.md README_zh.md UPGRADING.md CLAUDE.md \
        mateclaw-server/CLAUDE.md mateclaw-ui/CLAUDE.md \
        .github/PULL_REQUEST_TEMPLATE.md
git status
git commit -m "$(cat <<'EOF'
docs(brand): rewrite README and refresh internal docs to QingwenClaws

- README.md / README_zh.md: completely rewritten as QingwenClaws product intro
- UPGRADING.md: brand string replacement + new "Customization & Upstream Sync"
  chapter documenting OUT-of-scope kept identifiers, 8-point conflict map,
  sync workflow, and H2 migration note
- CLAUDE.md (root + server + ui): brand string replacement, structural
  identifiers (paths/classes/config keys) intentionally kept
- .github/PULL_REQUEST_TEMPLATE.md: brand string replacement

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6 · 容器编排 + Webchat + Plugin + 架构图（Day 4）

### Task 6.1: 修改 docker-compose.yml

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: 替换服务名、镜像、容器名、默认环境**

Edit `docker-compose.yml`:

| Line | Old | New |
|---|---|---|
| 12 | `container_name: mateclaw-mysql` | `container_name: qingwenclaws-mysql` |
| 16 | `MYSQL_DATABASE: ${DB_NAME:-mateclaw}` | `MYSQL_DATABASE: ${DB_NAME:-qingwenclaws}` |
| 17 | `MYSQL_USER: ${DB_USERNAME:-mateclaw}` | `MYSQL_USER: ${DB_USERNAME:-qingwenclaws}` |
| 38 | `# both of which silently break mateclaw's SearXNGSearchProvider).` | `# both of which silently break qingwenclaws's SearXNGSearchProvider).` |
| 43 | `container_name: mateclaw-searxng` | `container_name: qingwenclaws-searxng` |
| 47 | `SEARXNG_SECRET=${SEARXNG_SECRET:-mateclaw-dev-searxng-secret-change-me}` | `SEARXNG_SECRET=${SEARXNG_SECRET:-qingwenclaws-dev-searxng-secret-change-me}` |
| 59 | `# MateClaw 后端服务` | `# QingwenClaws 后端服务` |
| 60 | `mateclaw-server:` (service name) | `qingwenclaws-server:` |
| 63 | `dockerfile: mateclaw-server/Dockerfile` | （保留 — 这是物理路径 OUT） |
| 66 | `container_name: mateclaw-server` | `container_name: qingwenclaws-server` |
| 77 | `DB_NAME: ${DB_NAME:-mateclaw}` | `DB_NAME: ${DB_NAME:-qingwenclaws}` |
| 78 | `DB_USERNAME: ${DB_USERNAME:-mateclaw}` | `DB_USERNAME: ${DB_USERNAME:-qingwenclaws}` |

> service name 改变会影响 service-to-service 依赖（如 `depends_on:`）。

- [ ] **Step 2: 检查 depends_on 引用**

Run: `grep -n "depends_on\|mateclaw-" docker-compose.yml`

如果有 `depends_on: - mateclaw-server` 之类，把名字一并改为 `qingwenclaws-server`。

- [ ] **Step 3: 检查 image / build 引用**

Run: `grep -n "image:\|build:" docker-compose.yml`

如果有 `image: mateclaw/mateclaw-server:1.3.0` 之类，改为 `image: qingwenclaws/qingwenclaws-server:1.3.0`。
`build:` 子键 `dockerfile: mateclaw-server/Dockerfile` 中的路径**保留**（模块目录 OUT）。

- [ ] **Step 4: 改 healthcheck 中的 db user 引用**

如果 healthcheck 命令里有硬编码 `mateclaw` 用户名（如 `mysqladmin ping -u mateclaw`），改为对应 env var 或硬编码 `qingwenclaws`。

- [ ] **Step 5: 验证**

Run:
```bash
grep -n "mateclaw\|MateClaw" docker-compose.yml | grep -v "mateclaw-server/Dockerfile"
```

Expected: 无输出（除 dockerfile 路径）。

- [ ] **Step 6: yaml lint**

Run:
```bash
docker compose config > /tmp/compose-validated.yml
echo $?
```

Expected: 退出码 0，无配置错误。

### Task 6.2: 修改 .env.example

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: 替换品牌词**

> **重要 OUT**：`MATECLAW_*` 环境变量名是 Java `@Value("${MATECLAW_*}")` 绑定，**保留**。仅改注释和默认值。

Edit `.env.example`:

| Line | Old | New | 备注 |
|---|---|---|---|
| 1 | `# MateClaw 环境变量配置` | `# QingwenClaws 环境变量配置` | 注释 |
| 12 | `DB_NAME=mateclaw` | `DB_NAME=qingwenclaws` | 默认值 |
| 13 | `DB_USERNAME=mateclaw` | `DB_USERNAME=qingwenclaws` | 默认值 |
| 28 | `# CORS 白名单（逗号分隔，如 https://mateclaw.example.com,https://admin.example.com）。` | `# CORS 白名单（逗号分隔，如 https://qingwenclaws.example.com,https://admin.example.com）。` | 注释 |
| 30 | `MATECLAW_CORS_ALLOWED_ORIGINS=` | （保留变量名）| OUT |
| 42-65 | `MATECLAW_*` 等变量名 | （保留变量名）| OUT |

> 变量名 `MATECLAW_*` 不动，注释和默认值改。

- [ ] **Step 2: 验证**

Run: `grep -n "MateClaw\|mateclaw" .env.example | grep -v "^[0-9]*:.*MATECLAW_"`

Expected: 仅剩 OUT-of-scope 的 `MATECLAW_*` env 变量名（不应在过滤后输出）。如果还有命中，是漏改的。

### Task 6.3: 修改 webchat 默认值

**Files:**
- Modify: `mateclaw-webchat/**`（按 grep 结果选定）

- [ ] **Step 1: 找命中**

Run: `grep -rn "MateClaw\|mateclaw" mateclaw-webchat/ --include="*.ts" --include="*.vue" --include="*.html" --include="*.json" 2>/dev/null | head -30`

- [ ] **Step 2: 逐文件替换**

对每个命中：
- 默认 title、widget 自报家门字符串 → 改
- 模块路径引用 `mateclaw-server/...` → 保留
- 类名/包名引用 → 保留

- [ ] **Step 3: 验证 webchat 构建**

Run:
```bash
cd mateclaw-webchat
pnpm install
pnpm build
```

Expected: 构建成功，dist 生成。

### Task 6.4: 修改 plugin sample

**Files:**
- Modify: `mateclaw-plugin-sample/src/main/resources/mateclaw-plugin.json`
- Modify: `mateclaw-plugin-sample/src/main/java/vip/mate/plugin/sample/HelloPlugin.java`

- [ ] **Step 1: 改插件 manifest 显示字串**

Edit `mateclaw-plugin-sample/src/main/resources/mateclaw-plugin.json`:

```json
{
  "name": "qingwenclaws-plugin-hello",
  "version": "1.0.0",
  "type": "tool",
  "displayName": "Hello World Plugin",
  "description": "A sample plugin that demonstrates the QingwenClaws Plugin SDK by registering a simple greeting tool.",
  "entrypoint": "vip.mate.plugin.sample.HelloPlugin",
  "minPlatformVersion": "1.1.0",
  "author": "QingwenClaws (擎问科技)",
  "config": {}
}
```

> **保留** `entrypoint: vip.mate.plugin.sample.HelloPlugin`（Java 类名 OUT）。
> 文件名 `mateclaw-plugin.json` 是插件运行时约定的清单文件名，**保留**。

- [ ] **Step 2: 改 HelloPlugin.java 用户可见字符串**

Run: `grep -n "MateClaw\|mateclaw" mateclaw-plugin-sample/src/main/java/vip/mate/plugin/sample/HelloPlugin.java`

对每个命中：
- 日志消息、用户可见字符串字面量 → 改
- 类引用、bean name、annotation → 保留

- [ ] **Step 3: 构建 plugin sample**

Run:
```bash
cd mateclaw-plugin-sample
mvn clean package -DskipTests
```

Expected: 构建成功。

### Task 6.5: 重做架构图

**Files:**
- Modify: `assets/architecture-biz-zh.svg`
- Modify: `assets/architecture-biz-en.svg`
- Modify: `assets/architecture-tech-zh.svg`
- Modify: `assets/architecture-tech-en.svg`

- [ ] **Step 1: 对每张 SVG 替换品牌字串**

对 4 张 SVG 文件分别：
```bash
for f in assets/architecture-biz-zh.svg assets/architecture-biz-en.svg \
         assets/architecture-tech-zh.svg assets/architecture-tech-en.svg; do
  cp "$f" "$f.bak"
  sed -i.tmp \
    -e 's/MateClaw/QingwenClaws/g' \
    -e 's/mateclaw/qingwenclaws/g' \
    "$f"
  rm -f "$f.tmp" "$f.bak"
done
```

- [ ] **Step 2: 改主题色（如果当前是橙色/棕色系）**

打开每张 SVG，找到 fill/stroke 颜色定义，把品牌色替换为 QingwenClaws 青蓝调色板：
- 旧主色（如 `#d97706` / `#92400e` 等橙棕） → `#00b4d8`
- 旧深色 → `#0d1b2a`
- 旧浅色 → `#48cae4` / `#caf0f8`

> 如果架构图本身是中性灰白配色，可以只换品牌字串，不动颜色。视觉风格统一可以作为后续独立任务。

- [ ] **Step 3: 浏览器验证**

Run: `open assets/architecture-biz-zh.svg`

Expected: 图正常打开，品牌字串显示为 QingwenClaws。

### Task 6.6: Phase 6 提交

- [ ] **Step 1: 提交容器编排 + webchat + plugin + 架构图**

Run:
```bash
git add docker-compose.yml .env.example mateclaw-webchat/ \
        mateclaw-plugin-sample/ assets/
git status
git commit -m "$(cat <<'EOF'
feat(brand): rebrand container orchestration, webchat, plugin sample, and architecture diagrams

- docker-compose.yml: service names, container names, default DB env values
- .env.example: comments + default values (MATECLAW_* env var names kept,
  bound to Java @Value, OUT-of-scope)
- mateclaw-webchat: default widget title and self-identification strings
- mateclaw-plugin-sample: manifest description/author, sample plugin logs
  (entrypoint class FQN kept)
- assets/architecture-*.svg (4 files): brand string + palette refresh

Verified: docker compose config validates, pnpm/mvn builds green.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 7 · 全量验收与 preview 重生（Day 5）

### Task 7.1: 完整字串清扫

- [ ] **Step 1: 跑全仓库清扫 grep**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw

grep -RIin --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist \
  --exclude-dir=.git --exclude-dir=.superpowers \
  "mateclaw\|mate.claw" . \
  | grep -vE "(vip/mate|vip\.mate|mate_|mateclaw-(server|ui|webchat|plugin-api|plugin-sample)/|MateClaw|MATECLAW_|^[^:]+:[0-9]+:\s*mateclaw:|LICENSE)" \
  > /tmp/final-mateclaw-hits.txt

wc -l /tmp/final-mateclaw-hits.txt
cat /tmp/final-mateclaw-hits.txt
```

Expected: 行数为 **0**。

- [ ] **Step 2: 若有命中，分析每条**

对每条命中：
- 判断它该归入 IN（补改）还是 OUT（更新过滤 regex 文档化）
- 如果 IN，回到对应 phase 补改
- 如果 OUT 但 regex 未覆盖，更新 spec 的 5.2 节 grep 规则

### Task 7.2: UI 全量构建

- [ ] **Step 1: UI lint + build**

Run:
```bash
cd mateclaw-ui
pnpm lint
pnpm build
```

Expected: 0 errors。

- [ ] **Step 2: webchat build**

Run:
```bash
cd ../mateclaw-webchat
pnpm build
```

Expected: 0 errors。

### Task 7.3: 后端全量构建 + 测试

- [ ] **Step 1: mvn 全测试**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw/mateclaw-server
mvn clean package 2>&1 | tee /tmp/final-mvn.log | tail -30
```

Expected: `BUILD SUCCESS`, `Tests run: ..., Failures: 0, Errors: 0`。

- [ ] **Step 2: 确认 JAR 名**

Run: `ls target/qingwenclaws-server*.jar`

Expected: 命中 1 个 JAR。

### Task 7.4: Docker 端到端 smoke

- [ ] **Step 1: 准备 .env**

Run:
```bash
cd /Users/justbin/project/2BPro/QingClaws/mateclaw
[ -f .env ] || cp .env.example .env
# 编辑 .env 填入必需的 DB_PASSWORD、DB_ROOT_PASSWORD、JWT_SECRET（任意非空值）
```

- [ ] **Step 2: 启动 stack**

Run:
```bash
docker compose down -v   # 清理旧 volume，确保 fresh
docker compose up -d --build
```

Expected: 三个容器 Up：`qingwenclaws-mysql`、`qingwenclaws-searxng`、`qingwenclaws-server`（容器名不再有 mateclaw 字样）。

- [ ] **Step 3: 等待 server ready**

Run:
```bash
for i in {1..60}; do
  curl -fsS http://localhost:18080/actuator/health > /dev/null 2>&1 && echo "ready after ${i}s" && break
  sleep 1
done
```

Expected: 60 秒内输出 `ready after Ns`。

- [ ] **Step 4: 检查日志无 mateclaw 自报家门**

Run:
```bash
docker logs qingwenclaws-server 2>&1 | grep -i "mateclaw\|MateClaw" | grep -vE "(vip\.mate|MateClaw[A-Za-z]+\.|mate_)"
```

Expected: 无输出（运行时日志只剩 OUT 范围内的 Java 类名/包名）。

> 注意：Java 包名 `vip.mate.*` 会出现在 stack trace、@Component 自动扫描日志里 — 这是预期的（OUT）。

- [ ] **Step 5: 浏览器 smoke**

打开 `http://localhost:18080`：
- 浏览器 tab title 是 `QingwenClaws - AI 助手`
- favicon 是新版青蓝蟹钳
- 登录页 Logo 是新版
- 用 `admin / admin123` 登录
- 切换中英文，确认两种语言下品牌词都是 `QingwenClaws`
- DevTools Network tab 全局搜 `mateclaw`：应只命中 OUT 范围（如 `mate_*` 表名出现在 API 响应、或 `mateclaw:` 配置在 actuator endpoint）
- About 页：显示 `About QingwenClaws`、版权行为 `© 2026 擎问科技`

### Task 7.5: 重新生成 preview.png

**Files:**
- Modify: `assets/images/preview.png`

- [ ] **Step 1: 截图主界面**

打开 `http://localhost:18080` 主聊天界面（或最能代表产品的界面），全屏截图。

> 推荐分辨率：1920×1080 或 2560×1440。注意页面里所有可见品牌词都是 QingwenClaws。

- [ ] **Step 2: 保存到 assets/images/preview.png**

把截图保存为 `assets/images/preview.png`（覆盖旧文件）。

- [ ] **Step 3: 验证**

Run:
```bash
file assets/images/preview.png
ls -lh assets/images/preview.png
```

Expected: PNG image data，文件大小合理（一般 200KB–2MB）。

### Task 7.6: 关停 docker

- [ ] **Step 1: 清理验收环境**

Run:
```bash
docker compose down -v
```

### Task 7.7: 最终提交 + 摘要

- [ ] **Step 1: 提交 preview 重生**

Run:
```bash
git add assets/images/preview.png
git status
git commit -m "$(cat <<'EOF'
chore(brand): regenerate preview.png from QingwenClaws-branded UI

Final acceptance screenshot of the rebranded admin console.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 2: 全分支提交摘要**

Run:
```bash
git log baseline/v1.3.0..feat/brand-customization-qingwenclaws --oneline
```

Expected: 看到 7 个 phase commit（Phase 1–7 各一个，preview 一个）。

- [ ] **Step 3: 推送分支**

Run:
```bash
git push -u origin feat/brand-customization-qingwenclaws
```

Expected: push 成功。

- [ ] **Step 4: （可选）创建 PR**

如果团队约定走 PR：

Run:
```bash
gh pr create --base baseline/v1.3.0 \
  --title "feat(brand): rebrand fork to QingwenClaws (B-level customization)" \
  --body "$(cat <<'EOF'
## Summary

- Implements brand customization spec at \`docs/superpowers/specs/2026-05-17-brand-customization-design.md\`
- Renames all customer-visible MateClaw → QingwenClaws across UI, backend runtime, seed data, docs, container orchestration, and architecture diagrams
- Keeps OUT-of-scope structural identifiers (Java packages, classes, module directories, table prefixes, YAML config root, env var prefix) — see \`UPGRADING.md\` for full conflict map

## Test plan

- [x] pnpm lint + pnpm build (mateclaw-ui)
- [x] pnpm build (mateclaw-webchat)
- [x] mvn clean package (mateclaw-server) — JAR named qingwenclaws-server-*.jar
- [x] mvn test green (all 273 test classes)
- [x] docker compose up -d → all 3 containers Up under qingwenclaws-* names
- [x] Browser smoke at http://localhost:18080: title/favicon/logo/i18n all QingwenClaws-branded
- [x] Final grep cleanup — 0 unscoped MateClaw/mateclaw hits

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## 完整文件清单（变更映射）

仅作 review 用：

### 新建
- `mateclaw-ui/public/logo/qingwenclaws_logo.svg`
- `mateclaw-ui/public/logo/qingwenclaws_logo.png`
- `mateclaw-ui/public/logo/qingwenclaws_logo_s.svg`
- `mateclaw-ui/public/logo/qingwenclaws_logo_s.png`
- `mateclaw-ui/public/logo/favicon.svg`

### 修改
- `mateclaw-ui/index.html`
- `mateclaw-ui/src/i18n/locales/zh-CN.ts`
- `mateclaw-ui/src/i18n/locales/en-US.ts`
- `mateclaw-ui/src/views/ChatConsole.vue`
- `mateclaw-ui/src/App.vue` + 其他引用 logo 的 .vue/.ts
- `mateclaw-ui/src/assets/main.css`
- `mateclaw-ui/public/logo/favicon.ico`（重生）
- `mateclaw-server/pom.xml`
- `mateclaw-server/Dockerfile`
- `mateclaw-server/src/main/resources/application.yml`
- `mateclaw-server/src/main/resources/application-mysql.yml`
- `mateclaw-server/src/main/resources/messages.properties`
- `mateclaw-server/src/main/resources/messages_en.properties`
- `mateclaw-server/src/main/resources/db/data-{zh,en,mysql-zh,mysql-en}.sql`
- `mateclaw-server/src/main/resources/prompts/**/*.txt`（按 grep 结果）
- `mateclaw-server/src/main/java/**/*.java`（按 grep 结果 — 字面量）
- `mateclaw-webchat/src/**`（按 grep 结果）
- `mateclaw-plugin-sample/src/main/resources/mateclaw-plugin.json`
- `mateclaw-plugin-sample/src/main/java/vip/mate/plugin/sample/HelloPlugin.java`
- `docker-compose.yml`
- `.env.example`
- `README.md` `README_zh.md` `UPGRADING.md`
- `CLAUDE.md` `mateclaw-server/CLAUDE.md` `mateclaw-ui/CLAUDE.md`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `assets/architecture-{biz,tech}-{zh,en}.svg`
- `assets/images/preview.png`

### 删除
- `mateclaw-ui/public/logo/mateclaw_logo.png`
- `mateclaw-ui/public/logo/mateclaw_logo_s.png`
