# Trial Banner Build Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the standard frontend build pass without changing the license-status behavior.

**Architecture:** Keep the shared Axios client unchanged. `TrialBanner.vue` will state the response-envelope shape that its existing response interceptor returns, then assign the envelope's typed `data` property to the license-status ref.

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Axios, vue-tsc, Vite.

---

### Task 1: Capture the existing type-check regression

**Files:**
- Test: `mateclaw-ui/src/views/layout/TrialBanner.vue:74-78`

- [ ] **Step 1: Run the type check before changing production code**

Run:

```bash
cd mateclaw-ui && node ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit
```

Expected: exit code `2` with TS2352 at `TrialBanner.vue:77`, where an `AxiosResponse` is cast to `LicenseStatus`.

### Task 2: Type the interceptor response at its component boundary

**Files:**
- Modify: `mateclaw-ui/src/views/layout/TrialBanner.vue:27-33,74-78`

- [ ] **Step 1: Add the local response-envelope type below `LicenseStatus`**

```ts
interface ApiEnvelope<T> {
  data: T
}
```

- [ ] **Step 2: Replace the unsafe fallback assignment**

```ts
const res = await http.get('/license/status')
const payload = res as unknown as ApiEnvelope<LicenseStatus>
status.value = payload.data
```

This matches the existing interceptor contract, which returns the backend `R<T>` object and exposes the actual license status in its `data` field.

- [ ] **Step 3: Re-run the regression gate**

Run:

```bash
cd mateclaw-ui && node ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit
```

Expected: exit code `0`; TS2352 is absent.

- [ ] **Step 4: Commit the focused change**

```bash
git add mateclaw-ui/src/views/layout/TrialBanner.vue
git commit -m "fix(ui): type trial license response"
```

### Task 3: Verify the release build

**Files:**
- Verify: `mateclaw-ui/package.json:7-9`

- [ ] **Step 1: Run the standard production build**

Run:

```bash
cd mateclaw-ui && pnpm build
```

Expected: exit code `0`, first completing `vue-tsc --noEmit` and then emitting Vite assets to `mateclaw-server/src/main/resources/static`.

- [ ] **Step 2: Inspect final repository state**

Run:

```bash
git status --short --branch
git log --oneline -2
```

Expected: no uncommitted files; branch contains the design, plan, and focused fix commits.
