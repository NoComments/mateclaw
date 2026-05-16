# 品牌定制改造设计 · QingwenClaws v1.0

> Spec date: 2026-05-17
> Branch base: `baseline/v1.3.0`
> Owner: 擎问科技 工程团队
> Status: 待 user review，approved 后进入 writing-plans 阶段

---

## 1. 背景与目标

擎问科技以上游开源项目 mateclaw（Apache 2.0）为底座，构建商业产品 **QingwenClaws** —— 面向企业级个人的 Agent 助手平台。本 spec 定义**品牌定制改造（第一步）**的范围与执行方案。

### 决策档位

采用 **🅱 中改档位**：消除所有对外可见（UI、JAR、Docker 镜像、日志、HTTP 响应）的 mateclaw 痕迹，但保留 Java 包名、Maven 模块名、artifactId 等结构性命名，以保持上游 sync 能力。

预估工作量 **3–5 天**，预估每次上游 sync 的额外冲突处理 **1–2 小时**。

---

## 2. 品牌身份 SSOT

### 2.1 命名约定

| 用途 | 取值 |
|---|---|
| 产品对外名（唯一英文） | `QingwenClaws` |
| 公司中文名（仅 footer/About/License） | 擎问科技 |
| 公司英文名（footer 英文版） | `Qingwen Tech` |
| Machine ID（目录、artifactId、镜像、CLI） | `qingwenclaws` |
| Spring app name | `qingwenclaws` |
| JAR finalName | `qingwenclaws-server` |
| Docker image | `qingwenclaws/qingwenclaws-server` |
| Docker compose service | `qingwenclaws-server` |
| H2 数据库文件 | `data/qingwenclaws.mv.db` |
| 版权字符串 | `Copyright © 2026 擎问科技 (Qingwen Tech). All rights reserved.` |
| URL | `<TBD-URL>`（待定，文档先用占位符） |

### 2.2 i18n 范围

保留中英双语 UI。`zh-CN.ts` 和 `en-US.ts` 都保留，两种语言下产品名固定显示 `QingwenClaws`（不强行音译）。

### 2.3 视觉系统 · Design Tokens

```css
--qwc-bg-deep:      #0d1b2a   /* 深夜蓝 · 主背景 */
--qwc-bg-mid:       #1b2f4b   /* 中层背景 */
--qwc-cyan-deep:    #0077a8   /* 深青 · 阴影 */
--qwc-cyan-primary: #00b4d8   /* 主品牌色 · 强调 */
--qwc-cyan-light:   #48cae4   /* 浅青 · 高光 */
--qwc-cyan-pale:    #caf0f8   /* 极淡青 · 镜面反射 */
--qwc-white:        #ffffff
```

> 引入策略：UI 现有的 `--mc-*` 变量**不全量改名**，只把品牌强相关（主色、强调色）切到 `--qwc-*`，中性色（gray/black/border）保留原命名，避免散弹改造。

### 2.4 Logo 交付物

| 文件 | 格式 | 用途 |
|---|---|---|
| `qingwenclaws_logo.svg` | 矢量 | 主 logo 横版锁定（图标 + wordmark） |
| `qingwenclaws_logo_s.svg` | 矢量 | 方版仅图标（侧栏、小尺寸） |
| `qingwenclaws_logo.png` | 512×512 | 栅格 fallback |
| `qingwenclaws_logo_s.png` | 128×128 | 栅格 fallback |
| `favicon.svg` | 矢量 | 现代浏览器 favicon |
| `favicon.ico` | 32×32 多帧 | 老浏览器兼容 |

形态基线：本 spec 配套的 brainstorming 浏览器 v2 勾爪版（侧面龙虾大钳，封闭勾形上指，三道甲壳板，斜向 -15° 攻击姿态）。落地时若有设计师最终版，可在实施阶段替换 SVG 源。

### 2.5 上游归属（Apache 2.0 合规）

采用**最小合规**策略：
- 保留 `LICENSE` 文件原文（Apache 2.0 要求）
- 保留源码版权头（若有）
- UI / About / footer 不出现任何上游字样
- 不在产品任何对外可见位置点名 mateclaw 或上游作者

---

## 3. 改造范围

### 3.1 IN 范围（本轮要改）

#### A. UI 表面（`mateclaw-ui/`）
- `index.html` — `<title>`
- `src/i18n/locales/zh-CN.ts` + `en-US.ts` — 品牌词全替换（保持双语，约 10 处/文件）
- `src/assets/main.css` — 新增 `--qwc-*` token，品牌相关样式切换
- `public/logo/` — 替换/新增 6 个 logo 文件 + favicon
- `src/App.vue`、`src/views/ChatConsole.vue` — 移除 `VITE_APP_TITLE` 等硬编码引用，统一指向 i18n

#### B. 后端运行时（`mateclaw-server/`）
- `pom.xml` — `<finalName>qingwenclaws-server</finalName>`
- `src/main/resources/application.yml` — `spring.application.name: qingwenclaws`、H2 路径
- `src/main/resources/application-mysql.yml` — 同上
- `Dockerfile` — `LABEL` + 构建产物路径
- `messages.properties` + `messages_en.properties` — 品牌词
- Java 代码品牌字符串：错误消息、prompt 模板、日志输出、HTTP 响应自报家门字段（**不动类名/包名**）

#### C. 种子数据（DB seed SQL）
- `db/data-zh.sql` / `db/data-en.sql` / `db/data-mysql-zh.sql` / `db/data-mysql-en.sql` — 完整品牌化：默认 agent 名、工作区名、demo 对话内容
- 同步检查 `db/schema.sql` / `db/schema-mysql.sql`（应该只有 DDL，无品牌字串，确认即可）

#### D. 容器与编排
- `docker-compose.yml` — service name、image tag、environment 默认值
- `.env.example` — 注释和示例值
- `.dockerignore`、`.gitignore` — 若有品牌路径引用

#### E. 嵌入式 widget（`mateclaw-webchat/`）
- 默认 title、widget 自报家门字段
- 构建产物注入路径不变（仍写入 `mateclaw-server/src/main/resources/static/webchat/`，因模块目录名不变）

#### F. 插件（`mateclaw-plugin-sample/`）
- `src/main/resources/mateclaw-plugin.json` — 仅插件描述里的品牌词
- 示例类的日志输出
- **不动** `mateclaw-plugin-api` 的 artifactId（保持第三方插件二进制兼容）

#### G. 公开文档
- `README.md` — 重写为 QingwenClaws 产品介绍（产品定位、核心能力、部署指南）
- `README_zh.md` — 中文版同步重写
- `UPGRADING.md` — 品牌词替换 + 新增「品牌定制改造」章节，说明 fork 边界和上游 sync 冲突区
- `LICENSE` — **保留原样**（Apache 2.0 要求）

#### H. 内部开发文档
- `CLAUDE.md`（根） — 品牌词全改
- `mateclaw-server/CLAUDE.md` + `mateclaw-ui/CLAUDE.md` — 品牌词全改（**文件路径仍叫 mateclaw-server/、mateclaw-ui/**，不改）
- `.github/PULL_REQUEST_TEMPLATE.md` — 品牌词

#### I. 视觉资产
- `assets/architecture-{biz,tech}-{zh,en}.svg` — 4 张架构图重新配色 + 品牌词替换
- `assets/images/preview.png` — 标记 **TBD**，待 UI 改造完成后截图重新生成

### 3.2 OUT 范围（本轮不改）

| 类别 | 例子 | 不改原因 |
|---|---|---|
| Maven `groupId` | `vip.mate` | 中改档不动 Maven 坐标 |
| Java 包根 | `vip.mate.*` | 改包名 = 273 测试类全部重构 + 上游 sync 灾难 |
| 模块目录名 | `mateclaw-server/`、`mateclaw-ui/`、`mateclaw-webchat/`、`mateclaw-plugin-api/`、`mateclaw-plugin-sample/` | 同上 |
| Java 类名 | `MateClawXxx` 等 | 类名重命名波及面太大 |
| DB 表前缀 | `mate_*` | 需要写 Flyway 迁移，破坏上游迁移文件兼容 |
| `mateclaw-plugin-api` artifactId | `vip.mate:mateclaw-plugin-api` | 第三方插件已编译依赖此坐标 |
| Flyway 迁移文件名 | `V36__skill_i18n_name.sql` 等 | 历史迁移文件全锁定，禁动 |
| LICENSE | Apache 2.0 文本 | 法律要求保留原文 |
| 内部测试夹具品牌字串 | 测试 fixture 里的字符串 | 与运行无关，改它无收益 |

---

## 4. 构建产物命名映射

| 类型 | Before | After |
|---|---|---|
| JAR | `mateclaw-server-1.3.0.jar` | `qingwenclaws-server-1.3.0.jar` |
| Docker image | `mateclaw/mateclaw-server:1.3.0` | `qingwenclaws/qingwenclaws-server:1.3.0` |
| Compose service | `mateclaw-server` | `qingwenclaws-server` |
| Spring app name | `mateclaw` | `qingwenclaws` |
| H2 数据库文件 | `data/mateclaw.mv.db` | `data/qingwenclaws.mv.db` |
| Log 文件名（若有） | `mateclaw.log` | `qingwenclaws.log` |
| HTTP response 自报家门字段 | `mateclaw/1.3.0` | `qingwenclaws/1.3.0` |

> 数据库迁移：H2 文件路径变了，旧部署升级需要手动 `mv data/mateclaw.mv.db data/qingwenclaws.mv.db`。本仓库是 fresh fork，没有存量部署，不写迁移脚本，但在 `UPGRADING.md` 加一行说明。

---

## 5. 验收标准（Definition of Done）

### 5.1 自动化检查
```bash
# 1. 前端构建零错误
cd mateclaw-ui && pnpm build
# 2. 前端 lint 零错误
cd mateclaw-ui && pnpm lint
# 3. 后端构建零错误
cd mateclaw-server && mvn clean package -DskipTests
# 4. 后端测试全绿（特别关注 SQL seed 相关测试）
cd mateclaw-server && mvn test
```

### 5.2 字串清扫验证
```bash
# 项目根目录运行，预期：只有 OUT-of-scope 范围的命中
grep -RIin --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist --exclude-dir=.git --exclude-dir=.superpowers \
  "mateclaw\|mate.claw" . \
  | grep -vE "(vip/mate|vip\.mate|mate_|mateclaw-(server|ui|webchat|plugin-api|plugin-sample)/|MateClaw|LICENSE)"
```
输出应为空。若有命中，要么补改，要么追加到 OUT 范围里说明。

### 5.3 人工烟雾测试
- `docker compose up -d` 从 clean state 起，访问 `http://localhost:18080`
- 浏览器 DevTools Network/Elements 全局搜 `mateclaw`，应无任何出现
- 登录 `admin / admin123`，切换中/英文 UI，确认两种语言下品牌词都是 `QingwenClaws`
- 看 `docker logs qingwenclaws-server`，确认 Spring banner / 启动日志不再出现 `mateclaw`
- 访问 `/api/` 根接口，确认响应自报家门字段是 `qingwenclaws`

---

## 6. 工作量分解（3–5 天）

| Day | 任务 | 验收点 |
|---|---|---|
| Day 1 | 视觉资产生成（SVG/PNG/favicon × 6 个）+ Design Token 定义 | logo 文件就位、main.css 加 `--qwc-*` |
| Day 1–2 | UI 改造：i18n 双语品牌词、index.html、logo 引用、ChatConsole/App.vue 硬编码清理 | `pnpm build` 绿、浏览器 demo 全 QingwenClaws |
| Day 2 | 后端运行时改造：pom.xml、application.yml、Dockerfile、messages | `mvn package` 绿、JAR 名正确 |
| Day 3 | 种子数据完整品牌化（4 个 SQL 文件） + 后端品牌字串（错误消息/prompt/日志） | `mvn test` 全绿 |
| Day 3–4 | 文档：README/README_zh 重写、UPGRADING.md 加冲突地图、CLAUDE.md ×3、PR 模板 | grep 字串清扫通过 |
| Day 4 | webchat widget、plugin sample、架构 SVG ×4、docker-compose、.env.example | docker compose smoke 通过 |
| Day 5 | 全量验收：grep 清扫、构建、测试、Docker 全链路 smoke、preview.png 重生 | 验收清单全打钩 |

桌面端 / Electron 壳的品牌定制不在此仓库范围（项目级 CLAUDE.md 说明它在另一个仓库），后续单独建 issue 跟进。

---

## 7. 上游 sync 冲突地图

下次从上游 `baseline/v1.3.x` merge 新代码，冲突集中在这 8 处，预计每次 sync 额外花 1–2 小时：

1. `pom.xml` 的 `<finalName>`
2. `application.yml` 的 `spring.application.name`
3. `docker-compose.yml` 服务名 + 镜像
4. `Dockerfile` LABEL
5. UI i18n 文案（每次上游若新增 i18n key 含 mateclaw 字样）
6. Seed SQL 默认数据（上游若改 demo 内容会冲突）
7. `README.md` / `README_zh.md` / `UPGRADING.md`
8. 内部 CLAUDE.md（上游若更新开发规范）

此冲突地图将作为 `UPGRADING.md` 的「定制改造说明」章节，给将来做 sync 的同事一份操作指引。

---

## 8. 风险与缓解

| 风险 | 概率 | 缓解 |
|---|---|---|
| 漏掉某些字符串导致客户运维看到 mateclaw | 中 | 自动化 grep 清扫脚本 + Code review 双重把关 |
| H2 数据库文件名变更影响 existing dev DB | 低（fresh fork） | UPGRADING.md 文档化迁移命令 |
| 上游 sync 冲突频繁 | 中 | 冲突地图写入 UPGRADING.md；建议每次 sync 由有改造记忆的人操作 |
| 第三方插件兼容（`mateclaw-plugin-api`） | 低 | artifactId 保持不变，向后兼容 |
| logo 实施版与设计师最终版不一致 | 高 | 落地用 brainstorming v2 占位，实施阶段开口替换 SVG 源 |

---

## 9. 后续工作（不在本 spec 范围）

- 视觉资产升级：设计师最终版 logo / 主题色微调（独立任务，到位后追加 0.5 天 PR）
- 桌面端 Electron 壳的品牌定制（另一个仓库）
- 域名 / 主站 URL 确定后回填 `<TBD-URL>` 占位（README、文档、可能的 OAuth callback 等处）
- 营销物料（preview.png、产品宣传图、PPT 模板）由市场团队产出，本 spec 不覆盖
