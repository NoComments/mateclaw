# Upgrading QingwenClaws

## 1.0.x → 1.1.0

**TL;DR** — Most users have nothing to do. Restart with 1.1.0, Flyway's built-in repair heals known checksum drift, Ollama auto-discovery rewrites the bad `:latest` defaults, and everything else self-converges. Docker Compose deployments need a one-time `.env` update.

See `docs/en/releases/1.1.0.md` for the feature changelog.

---

## For everyone

### ⚠️ What happens automatically (no action)

- **Flyway migration self-heal** — 1.1.0 rewrote all MySQL migrations V2–V14 to replace unsupported `ADD COLUMN IF NOT EXISTS` syntax (Gitee #IIYHLJ). `FlywayRepairConfig` runs `flyway.repair()` on every boot, so the new checksums auto-accept and migration resumes from wherever your schema is.
- **Ollama default model** — if your 1.0.x run auto-picked a model tag Ollama no longer has (commonly `deepseek-r1:latest`), on 1.1.0 restart `OllamaAutoDiscoveryRunner` detects the broken default and re-picks a tag-capable model (e.g. `deepseek-r1:7b`, `qwen3:latest`), preferring one that supports function calling.
- **Stale `mate_model_config` rows** — idempotent seed data reconciles on each startup.

### 📋 Recommended pre-upgrade steps

1. Back up your database — `qingwenclaws` schema on MySQL, or `data/qingwenclaws.mv.db` on H2.
2. Back up `data/` directory (skill workspaces, uploaded files, memory files).
3. Note your current default model in Settings → Models in case you want to switch back.

### 🚀 Upgrade

```bash
git pull
cd mateclaw-server
mvn clean package -DskipTests
# then restart your service per your deployment method
```

Or for Desktop app users: just update to 1.1.0 via the in-app updater or re-download.

---

## For Docker Compose deployments

**One-time migration step required** — 1.1.0 refuses to start with default hardcoded passwords.

### 1. Copy-paste merge the new `.env.example` keys

```bash
cp .env .env.backup
# open .env.example — it has new required keys:
#   DB_PASSWORD=       (was default 'qingwenclaws123', now MUST be overridden)
#   DB_ROOT_PASSWORD=  (new, required for MySQL root)
#   JWT_SECRET=        (new, strongly recommended)
#   MATECLAW_CORS_ALLOWED_ORIGINS=  (new, strongly recommended for prod)
```

### 2. Set strong values in your `.env`

```env
# STRONG passwords — at least 16 chars, mixed case + digits + symbols
DB_PASSWORD=<your-strong-db-user-password>
DB_ROOT_PASSWORD=<different-strong-root-password>

# 32+ char random string — generate with: openssl rand -base64 48
JWT_SECRET=<your-jwt-secret>

# Production CORS allowlist — comma-separated, no wildcards
MATECLAW_CORS_ALLOWED_ORIGINS=https://qingwenclaws.example.com
```

If any of `DB_PASSWORD` / `DB_ROOT_PASSWORD` / `DASHSCOPE_API_KEY` is missing, `docker compose up` will fail fast with a clear error — this is intentional.

### 3. Existing MySQL volume compatibility

If you already ran 1.0.x with the old default password (`qingwenclaws123`), **your existing MySQL volume still has the old root password inside**. You have two options:

**Option A — keep existing password** (fastest, least secure):
Set `DB_ROOT_PASSWORD=qingwenclaws123` and `DB_PASSWORD=qingwenclaws123` in `.env` to match. Upgrade works. Then rotate after upgrade using `ALTER USER ... IDENTIFIED BY ...` inside the MySQL container.

**Option B — fresh volume with new password** (cleanest, loses DB if not backed up):
```bash
docker compose down -v   # ⚠️ deletes mysql_data volume; back up first
# edit .env with new strong password
docker compose up -d
```
Then re-import your backup if you kept one.

### 4. Restart

```bash
docker compose up -d
docker compose logs -f qingwenclaws-server   # watch for "Flyway Successfully applied N migrations"
```

Expected log lines during boot:
- `Flyway Successfully applied N migrations to schema qingwenclaws`
- `Ollama: auto-activated default model '<actual-tag>'` (if you use Ollama — should NOT say `:latest` any more)
- `[Security] Using default JWT secret!` → means you forgot to set `JWT_SECRET` — fix and restart

---

## For local dev / H2 deployments

No action required. `mvn spring-boot:run` picks up the latest migrations on next start, Flyway repair handles checksum drift, H2 file at `data/qingwenclaws.mv.db` is preserved.

---

## Known migration quirks

### 1. If you manually fiddled with `flyway_schema_history`

In 1.0.x some users hit Flyway version collisions (V8/V9 and V9/V10) which 1.1.0 fixes by renumbering. If you manually deleted rows from `flyway_schema_history` you may see `Validate failed` on 1.1.0 startup — run:

```sql
-- MySQL
DELETE FROM flyway_schema_history WHERE success = 0;
```

Then restart. `FlywayRepairConfig` will rebuild history from current schema state.

### 2. If your Ollama models are all in the no-tools family

After upgrade, agents that require tool calling will log a warning on first invocation:

```
Ollama: auto-activated default model '...' but its family does not support tool calling
```

Fix — pull a tool-capable model, or switch default in Settings → Models:

```bash
ollama pull qwen3
# or
ollama pull llama3.1:8b
# or
ollama pull mistral-nemo
```

### 3. If you had custom tools using `extract_document_text` / wiki tools

Wiki chunk schema changed (new `embedding` + `embedding_model` columns on `mate_wiki_chunk`). Your existing wiki pages work unchanged; only semantic search is new and requires an embedding model to be configured in Settings → Models (a default DashScope embedding is seeded).

---

## Rolling back to 1.0.x

Not recommended (some new tables / columns don't exist in 1.0.x), but possible if you backed up the DB before upgrade:

```bash
git checkout v1.0.418
# restore DB backup
docker compose up -d   # or mvn spring-boot:run
```

If you need to keep the new data but downgrade the app, you're in unsupported territory — open a Gitee issue.

---

## Getting help

- **Logs first**: `mateclaw-server/logs/qingwenclaws.log` + `qingwenclaws-error.log` have everything. Flyway decisions are at INFO level in main log.
- **Doctor tab**: in-app Settings → Doctor runs basic health checks
- **Gitee**: https://gitee.com/matevip_admin/qingwenclaws/issues — include your upgrade path (1.0.?? → 1.1.0), profile (H2 / MySQL), and the last 100 lines of startup log

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
6. UI i18n 文件 `mateclaw-ui/src/i18n/locales/{zh-CN,en-US}.ts` 的品牌词
7. Seed SQL 文件 `mateclaw-server/src/main/resources/db/data-{zh,en,mysql-zh,mysql-en}.sql`
8. `README.md`、`README_zh.md`、`CLAUDE.md`（含各子模块 CLAUDE.md）、`.github/PULL_REQUEST_TEMPLATE.md`

### Sync 操作指引

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
