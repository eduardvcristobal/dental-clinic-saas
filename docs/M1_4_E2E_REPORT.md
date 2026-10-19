# M1.4 Browser and Onboarding E2E Report

Date: 2026-10-19

## Executed

- Started Vite at `http://127.0.0.1:5174` with the supplied public Google client ID, and rendered `/register-clinic` in Chrome.
- Confirmed the UI states clinic registration is disabled by default.
- Sent a synthetic registration request to the isolated backend at port 8081; server returned HTTP 403. No clinic was created.
- Sent an unauthenticated request to `/api/platform-admin/clinics`; server returned HTTP 401.
- Reviewed the invitation acceptance page source: it removes the token query parameter at mount and declares a no-referrer policy.

## Not executed

No Google account was signed in. The Chrome profile showed a different account identity than the configured owner account, and the OAuth JavaScript origin/callback configuration was not confirmed. Registration was not enabled even temporarily. No invitation, acceptance, wrong-email, expiry, revocation, parallel acceptance, dentist profile or logout flow was driven through the browser. No complete responsive route review was performed.

A browser screenshot was inspected in-session but could not be saved as a file by the current display capture API. Therefore no screenshot is claimed as a deliverable.

## Result

The UI route and disabled registration boundary received a limited smoke check. M1.4 onboarding E2E acceptance remains blocked; this report does not certify the full workflow.

## Browser console observation

During this development-server smoke, Vite emitted repeated Google Identity Services warnings that `google.accounts.id.initialize()` was called multiple times. This was not investigated against a production build or a completed sign-in, so it is recorded as an unresolved frontend follow-up rather than a confirmed authentication failure.
