# Trial banner build fix

## Goal

Allow the standard frontend build to complete while preserving the existing license-status behavior.

## Scope

`TrialBanner.vue` receives the application's unwrapped API envelope from the shared `http` interceptor. The component will model that envelope locally as `{ data: LicenseStatus }` and assign only its `data` field to the typed Vue ref.

The fallback cast that treats a complete Axios response as `LicenseStatus` will be removed. The shared HTTP client, backend contract, UI behavior, and unrelated build errors are out of scope.

## Validation

1. `vue-tsc --noEmit` is the regression gate: it currently fails with TS2352.
2. After the change, run `pnpm build`; this executes the type check and Vite production bundle.
