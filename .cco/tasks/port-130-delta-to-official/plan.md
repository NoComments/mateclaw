# Plan

1. Fetch the source branch and source 1.3.0 baseline into the target checkout.
2. Generate a binary-safe patch from source `origin/baseline/v1.3.0` to source working tree.
3. Apply with `git apply --3way`, excluding target-incompatible old UI entry files:
   - `mateclaw-ui/public/logo/*`
   - `mateclaw-ui/src/views/CronJobs.vue`
   - `mateclaw-ui/src/views/Triggers.vue`
4. Resolve conflicts by preserving target branch's newer UI structure and manually re-adding the source analytics/license entry points.
5. Rename imported source migrations from `V113`-`V123` to `V170`-`V180` because target already owns `V113`-`V169`.
6. Restore the missing `scripts/check-snowflake-precision.sh` required by the target package build script.
7. Validate:
   - conflict marker scan
   - duplicate Flyway version scan
   - `git diff --check`
   - `pnpm build`
   - focused Maven analytics/SQL tests
8. Record acceptance and commit.

## Notes

Claude planning was used for the broad port strategy. OpenCode could not execute implementation work in this environment, so Codex performed the fallback implementation and recorded it in `opencode-report.md`.
