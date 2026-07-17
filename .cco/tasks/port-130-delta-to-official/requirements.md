# Requirements

## User request

Port all tracked changes in `D:\粮食局需求\mateclaw` that exist after the source repo's `origin/baseline/v1.3.0` into `D:\粮食局需求\mateclaw-official\mateclaw`, then commit them locally in the official checkout.

## Normalized scope

- Source checkout: `D:\粮食局需求\mateclaw`, branch `brand-rename-surveymind`.
- Source baseline: `origin/baseline/v1.3.0` (`d3ffd60`).
- Target checkout: `D:\粮食局需求\mateclaw-official\mateclaw`, branch `dev`, starting HEAD `22a61e6`.
- Include tracked source changes only.
- Exclude source untracked local junk such as local env files, editor folders, scratch reports, and temporary scripts.
- Preserve target branch work where it had newer structure, especially scheduler pages and current UI capability gating.

## Acceptance criteria

- No unresolved Git conflicts.
- Source delta is applied or intentionally adapted for target branch structure.
- Flyway migration versions remain unique in both H2 and MySQL trees.
- Frontend build succeeds.
- Focused backend analytics/SQL tests succeed.
- Local commit exists in the target checkout.
