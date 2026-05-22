# Design: Merge Datasource into Analytics Module

**Date:** 2026-05-21
**Branch:** feat/data-analyst-expert
**Scope:** Frontend only (backend zero change)

## Problem

Two data-related features live in separate navigation locations:
- **Analytics** (`/analytics`) — upload Excel, manage datasets/templates, AI analysis
- **Datasources** (`/settings/datasources`) — connect external databases (MySQL/PG/ClickHouse)

Users (especially non-technical) are confused about the relationship. Personal users uploading Excel never need the datasource page, but its existence suggests they should configure a database first. Enterprise users with external databases don't discover the feature easily since it's buried in Settings > Advanced.

## Solution

Move the external datasource management into the Analytics module as a third Tab, and add a scenario guidance area that helps users understand which path to take.

### New Tab Structure

```
/analytics
  Tab: Datasets          (existing — upload Excel)
  Tab: Templates         (existing — define data structure)
  Tab: External Sources  (migrated from Settings)
```

### Scenario Guidance Area

Displayed at the top of the Analytics index page. Two cards side by side:
1. "Upload Files" — for users with Excel/CSV, links to Datasets tab
2. "Connect Database" — for users with existing MySQL/PG, links to External Sources tab

The guidance area collapses to a single-line hint once the user has created at least one dataset or datasource, to avoid noise for returning users.

## File Changes

### Move/Rename
| From | To |
|---|---|
| `views/Datasources.vue` | `views/analytics/DatasourceList.vue` |

### Modify
| File | Change |
|---|---|
| `views/analytics/index.vue` | Add third Tab "External Sources" + guidance area |
| `router/index.ts` | Add `/analytics/datasources` route; change `/settings/datasources` to redirect to `/analytics/datasources` |
| `views/Settings/Layout.vue` | Remove datasources sidebar entry |
| `i18n/locales/zh-CN.ts` | Add guidance area i18n keys under `analytics.*` |
| `i18n/locales/en-US.ts` | Same |
| `views/layout/MainLayout.vue` | No change (Analytics nav entry already exists) |

### Backward Compatibility
- `/settings/datasources` redirects to `/analytics/datasources`
- `/datasources` (existing redirect) updated to point to `/analytics/datasources`
- `datasourceApi` in `api/index.ts` unchanged — same backend endpoints

### NOT in scope
- Backend code changes (zero)
- Agent tool chain unification (deferred)
- SQL validation merge (deferred)
- ECharts logic unification (deferred)

## Guidance Area i18n

```
analytics.guidanceTitle      "Your data, your way"     / "数据，随你所需"
analytics.guidanceUpload     "Upload Files"             / "上传文件"
analytics.guidanceUploadDesc "Excel/CSV files, quick analysis" / "Excel/CSV 文件，快速分析"
analytics.guidanceConnect    "Connect Database"         / "连接数据库"
analytics.guidanceConnectDesc "MySQL, PostgreSQL, ClickHouse" / "MySQL、PostgreSQL、ClickHouse"
analytics.externalSources    "External Sources"         / "外部数据源"
```

## Visual Layout (ASCII)

```
+----------------------------------------------------------+
| Analytics                     Data Management             |
+----------------------------------------------------------+
| Your data, your way                                       |
| +------------------------+ +----------------------------+ |
| | Upload Files           | | Connect Database           | |
| | Excel/CSV, quick       | | MySQL, PG, ClickHouse     | |
| | analysis               | | AI queries your database   | |
| | [Go to Datasets ->]    | | [Go to External Sources ->]| |
| +------------------------+ +----------------------------+ |
+----------------------------------------------------------+
| [Datasets]  [Templates]  [External Sources]               |
+----------------------------------------------------------+
| ... tab content ...                                       |
+----------------------------------------------------------+
```

## Risk

- Low risk: purely UI restructuring, no data model or API changes
- Backward compatibility ensured via redirects
