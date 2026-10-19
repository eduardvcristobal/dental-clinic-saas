# M1.4.1 Browser E2E Results

**Status: PASS — 3/3 browser tests green, with screenshots and traces saved.**

## Root cause of the earlier failures (corrected diagnosis)

The earlier report attributed the failures to the dev server not receiving `VITE_GOOGLE_CLIENT_ID`. That diagnosis was wrong. Two real defects were found and fixed:

1. **The API fixture glob swallowed the app's own source code.** `page.route('**/api/**')` also matched Vite module URLs such as `/src/api/client.ts`, which were then served as JSON. The browser refused the module scripts (`Expected a JavaScript-or-Wasm module script... MIME type "application/json"`), `#root` stayed empty, and the page was blank. The earlier "missing configuration page" observation came from a separately started server without the variable, not from the Playwright-managed one. Fixed by matching with a URL predicate on the pathname (`/api/...` only) in `frontend/e2e/m1_4_1.spec.ts`.
2. **The loopback host was treated as a clinic subdomain.** `slugFromHost('127.0.0.1')` returned `'127'` (first DNS label), which hid the "Clinic code" input and left tests waiting on `getByLabel('Clinic code')`. Fixed in `frontend/src/lib/tenant.ts`: IP-literal hosts (IPv4 and IPv6) never resolve to a clinic slug. Covered by new unit tests in `frontend/src/lib/tenant.test.ts`.

Additional fixes required to make the suite deterministic and truthful:

- `frontend/playwright.config.ts`: bundled Chromium instead of a machine-specific system Chrome path, `trace: 'on'`, failure screenshots/video, and a `webServer` that starts a fresh Vite dev server with the synthetic client ID on a fixed port (`127.0.0.1:5174`, `--strictPort`, `reuseExistingServer: false`). The earlier environment itself was verified healthy — the port was reachable and the variable was injected whenever the server was actually managed by Playwright.
- Fixture semantics: `/api/auth/refresh` now returns the current synthetic session (a real refresh cookie keeps the session across reloads), and `/api/auth/logout` clears it, so sign-in state behaves like the real contract.
- Sign-in race: the synthetic Google button previously fired the credential callback twice (once from the click, once from the test). The double now reads a credential the test sets before clicking, so exactly one sign-in request is made per iteration.
- `AuthProvider.signOut` clears the local session even if the Google script call throws (`disableAutoSelect` is optional in the double and may be absent in blocked-script environments).
- Product CSS fix found by the new mobile viewport assertions: `.ws-columns>*{min-width:0}` — the schedule table's 550 px min-width previously blew the `1fr` grid track out to 606 px on a 390 px viewport.

## Execution result (this session)

Two consecutive complete runs, both fully green:

```text
✓ 1 clinic workspace, management and public onboarding routes render at desktop, tablet and mobile (30.2s)
✓ 2 platform admin receives directory information and is routed to the separate dashboard (926ms)
✓ 3 Google Identity initializes once while navigating between login screens (729ms)
3 passed
```

Coverage: 12 route/role combinations × 3 viewports (1440×1000, 820×1180, 390×844) with horizontal-overflow assertions; registration disabled state; invitation-token URL stripping; Platform Admin directory-only display; Google Identity initialize-once lifecycle. All network traffic is intercepted; no request reaches a real backend or Google.

## Evidence

Saved under `docs/evidence/m1_4_1/`:

- `desktop-command-center.png`, `mobile-registration-disabled.png`, `tablet-invitation-acceptance.png`, `desktop-platform-admin-directory.png`
- `traces/responsive-routes-trace.zip`, `traces/platform-admin-directory-trace.zip`, `traces/google-identity-lifecycle-trace.zip` (view with `npx playwright show-trace <file>`)

Traces were recorded with `trace: 'on'` during passing runs, so they show real successful executions.

## Still outside this suite

Real Google sign-in with an authorized account, and any request against a live backend, remain separate live-provider checks (see the certification report). The suite uses synthetic credentials only.
