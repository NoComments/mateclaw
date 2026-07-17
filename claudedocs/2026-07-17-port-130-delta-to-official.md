# Port 1.3.0 Delta To Official

## User request

Merge all tracked changes from the current source code after its 1.3.0 baseline into `D:\粮食局需求\mateclaw-official\mateclaw`.

## Implementation summary

- Applied the source branch delta from `brand-rename-surveymind` relative to source `origin/baseline/v1.3.0`.
- Resolved target-branch conflicts while preserving the official checkout's newer UI structure.
- Ported analytics backend, analytics UI, dataset upload/preview/history, safe SQL helpers, license gate, skill seeds, model provider updates, docs, and packaging scripts.
- Renumbered imported migrations to `V170`-`V180` to avoid collisions with target `V113`-`V169`.
- Added the missing `scripts/check-snowflake-precision.sh` required by the existing frontend build script.
- Fixed the imported multimodal model settings build error by importing `ElMessage`.

## Changed files

- Backend analytics/license/SQL/service/test additions under `mateclaw-server`.
- Frontend analytics views/API/types/router/sidebar/i18n additions under `mateclaw-ui`.
- Flyway migrations for H2/MySQL, renumbered to `V170`-`V180`.
- Source docs and packaging scripts.
- CCO task records under `.cco/tasks/port-130-delta-to-official`.

## Validation results

- `git diff --check`: passed.
- Conflict marker scan: passed.
- Duplicate Flyway version scan: passed.
- `pnpm install --frozen-lockfile`: passed.
- `mvn install -N -DskipTests`: passed.
- `mvn install -DskipTests` in `mateclaw-plugin-api`: passed.
- `pnpm build` in `mateclaw-ui`: passed.
- Focused Maven analytics/SQL test set in `mateclaw-server`: passed.

## Working directory

- `D:\粮食局需求\mateclaw-official\mateclaw`
- Branch: `dev`
- No worktree was used.

## Unresolved issues

None blocking. Source docs/scripts still contain some SurveyMind wording from the requested delta.
