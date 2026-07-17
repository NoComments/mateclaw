# Acceptance

## Result

Accepted.

The tracked source delta from `D:\粮食局需求\mateclaw` after `origin/baseline/v1.3.0` was ported into `D:\粮食局需求\mateclaw-official\mateclaw`.

## Important adaptations

- Preserved target branch's current UI structure for the conflicted chat, settings, agents, memory, skill, workflow, and layout files.
- Added analytics routes/sidebar/i18n into target's current router and `MainLayout`.
- Redirected legacy datasources routes to `/analytics/datasources` after `Datasources.vue` moved to `views/analytics/DatasourceList.vue`.
- Renumbered imported source migrations from `V113`-`V123` to `V170`-`V180` for both H2 and MySQL because target already had migrations through `V169`.
- Excluded old source `CronJobs.vue` and `Triggers.vue` because target already folds them into the Scheduler page.
- Restored `scripts/check-snowflake-precision.sh`, which target `mateclaw-ui/package.json` already referenced but the file was missing.
- Installed root Maven POM and `mateclaw-plugin-api` locally for validation only.

## Validation

- `rg -n "^(<<<<<<<|=======|>>>>>>>)" --glob "!*.patch"`: no matches.
- Duplicate Flyway version scan for H2/MySQL: no duplicate versions.
- `git diff --check`: passed.
- `pnpm install --frozen-lockfile`: passed.
- `mvn install -N -DskipTests`: passed.
- `mvn install -DskipTests` in `mateclaw-plugin-api`: passed.
- `pnpm build` in `mateclaw-ui`: passed.
- `mvn test "-Dtest=DatasetControllerTest,DatasetFileValidatorTest,DatasetUploadControllerTest,DatasetCreateFromFileTest,DatasetFieldRepositoryTest,DatasetReservedWordsTest,DatasetServiceTest,DynamicTableServiceTest,AnalyticsChartToolTest,AnalyticsComputeToolTest,AnalyticsExportToolTest,AnalyticsProfileToolTest,AnalyticsQueryToolTest,AnalyticsSchemaToolTest,SqlGuardTest,ExcelHeaderMatcherTest,ExcelIngestServiceTest,ExcelInspectServiceTest,ExcelParseServiceTest,SafeSqlRewriterTest"` in `mateclaw-server`: passed.

## Residual notes

- Some source documents and trial packaging scripts still contain SurveyMind wording because they are part of the requested source delta. Runtime UI license copy was made generic during conflict adaptation.
- `node_modules`, Maven `target`, and generated frontend static build output were not added as separate source changes.
