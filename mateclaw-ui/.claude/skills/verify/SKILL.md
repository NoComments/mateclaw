---
name: verify
description: Run the mateclaw-ui Vue app and drive it in a browser to observe a change working. Use when verifying UI changes in mateclaw-ui.
---

# Verifying mateclaw-ui changes

The surface is pixels. Start the dev server, drive it with Playwright, screenshot.

## Launch

```bash
cd mateclaw-ui
./node_modules/.bin/vite --port 5199 --strictPort
```

Playwright is not a project dependency — install it in your scratchpad:

```bash
npm install playwright && npx playwright install chromium
```

## Getting a usable page

Four things block a cold browser. All must be handled or the page never renders:

1. **Auth guard** (`src/router/index.ts`) redirects to `/login` unless a
   `token` exists in localStorage. It only checks presence, so any string works.
   `VITE_SKIP_AUTH=true` as a shell env var does **not** work — Vite reads
   `import.meta.env` from `.env` files only, not `process.env`.
2. **Onboarding wizard** overlays the page and swallows clicks. Set
   `mc-onboarding-done`.
3. **Unstubbed API calls** 401 → `handleAuthFailure()` → redirect to `/login`,
   detaching the DOM mid-click. Stub a catch-all.
4. **Catch-all glob must be `**/api/v1/**`**, not `**/api/**` — the latter also
   matches Vite's own module requests (`/src/api/analytics.ts`) and breaks the
   app with a MIME type error.

```js
await page.addInitScript(() => {
  localStorage.setItem('token', 'fake')
  localStorage.setItem('role', 'admin')
  localStorage.setItem('mc-workspace-id', '1')
  localStorage.setItem('mc-onboarding-done', '1')
})

// Catch-all FIRST — Playwright gives precedence to later-registered routes.
await page.route('**/api/v1/**', (r) => r.fulfill({ json: { code: 200, msg: 'ok', data: [] } }))
// Then per-endpoint stubs. Backend shape is R<T>: { code, msg, data }, code 200 = ok.
await page.route('**/api/v1/analytics/datasets*', (r) =>
  r.fulfill({ json: { code: 200, msg: 'ok', data: [/* ... */] } }))
```

## Proving a bug was real

Drive the flow, then `git stash push -- <paths>`, re-run the same script, and
compare. Restore with `git stash pop`. This turns "it works now" into evidence
that the fix is what changed the behavior.

## Gotchas unrelated to your change

- `npm run lint` is broken repo-wide: ESLint 9 wants `eslint.config.js` and the
  repo has none. Don't chase it.
- `vue-tsc --noEmit` has a pre-existing error in `src/views/layout/TrialBanner.vue`
  (AxiosResponse → LicenseStatus cast). Not yours.
