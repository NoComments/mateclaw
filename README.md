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
