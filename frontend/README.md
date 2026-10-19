# Frontend: React + TypeScript + Vite

Google sign-in (Google Identity Services via `@react-oauth/google`), multi-tenant aware.

## Setup
1. Google Cloud Console > APIs & Services > Credentials > Create OAuth client ID (Web application).
   Add `http://localhost:5173` under Authorized JavaScript origins (plus your production origin later).
2. `cp .env.example .env` and set `VITE_GOOGLE_CLIENT_ID`.
3. `npm install && npm run dev` (the backend is expected on `localhost:8080`; `/api` is proxied).

Allowlisted platform administrators can sign in at `/platform-admin/login` without a clinic code. Their Google email must appear in the backend `CLINIC_PLATFORM_ADMIN_EMAILS` setting.

Run the frontend checks with `npm ci && npm test && npm run build && npm run lint`.
Vitest covers concurrent refresh coalescing, restoration after a 401, and session expiry handling.

## How sign-in works
1. User enters a clinic code (or it comes from the subdomain) and clicks the Google button.
2. Google returns an ID token to the browser. The frontend sends `{ clinicSlug, idToken }` to `POST /api/auth/google`.
3. The backend verifies the token, finds the active staff user with that email in that clinic, returns a session
   (short-lived access token in the JSON body) and sets an httpOnly refresh cookie.
4. The access token is kept in memory only. On reload the app calls `POST /api/auth/refresh` to restore the session.

The backend auth endpoints live in `backend/src/main/java/com/dentalapp/auth`.

## Structure
```
src/api/      fetch client (auto-refresh on 401) and auth calls
src/auth/     AuthProvider, useAuth, ProtectedRoute
src/lib/      tenant (clinic slug) helpers
src/pages/    LoginPage, DashboardPage (module placeholders)
```
