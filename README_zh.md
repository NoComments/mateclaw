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
