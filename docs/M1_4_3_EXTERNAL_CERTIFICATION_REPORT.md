# M1.4.3 — External Integration & Certification Closure

**Status: BLOCKED — M1 remains OPEN and M2/real patient data remain blocked.**

## Evidence at hand

The M1.4.2 archive records 30 backend, 24 frontend unit, and 3 Playwright checks passing (57 total), a clean npm audit, and isolated PostgreSQL migration/RLS checks. These results were **not rerun** during this handoff. The source archive's Git history has no configured remote. No live Google ID token, Google test account, NVD API key, or hosted CI execution evidence was supplied.

## M1.4.3 changes

- Added `scripts/m1_4_3_preflight.sh`, a read-only environment prerequisite check that never prints credentials.
- Updated the opt-in OWASP Actions job to fail when the required NVD key is missing, fail when the scan itself errors, and gate on CVSS >= 7. This avoids reporting a green advisory scan when NVD data cannot be loaded.

## Required external completion evidence

1. **Live Google sign-in:** authorized OAuth Web client, origin matching the actual frontend URL, identical frontend/backend audience, an authorized test account, and a successful real login + accepted invitation in a disposable clinic. Capture sanitized browser and API result logs; no ID tokens or secrets in evidence.
2. **Maven dependency advisories:** current successful OWASP scan with NVD update, generated report, dated vulnerability decisions for high/critical items, and no unresolved applicable high/critical findings. A scanner download failure is BLOCKED, not PASS.
3. **Hosted GitHub Actions:** authorized private Git remote, workflow run URL/ID, completed Java 17/PostgreSQL 16/frontend/Playwright/security jobs, and downloaded logs/artifact manifest. The optional OWASP job must be explicitly enabled with `RUN_OWASP_SCAN=true` and the secret `NVD_API_KEY` supplied.
4. **Final:** rerun impacted tests, update the acceptance checklist and `PROJECT_STATUS.md`, commit results and deliver new packages. No M1 certification until every gate is evidenced.

## Environment limitations in this execution

No configured Git remote and no OAuth or NVD credentials were available. Java 17, Maven and an isolated PostgreSQL database were also not provided in this execution environment. No remote changes or external authentication were attempted. The M1.4.2 passing test totals must not be attributed to this M1.4.3 run.
