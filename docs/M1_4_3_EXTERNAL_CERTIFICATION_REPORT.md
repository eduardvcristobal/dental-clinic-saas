# M1.4.3 — External Integration & Certification Handoff

**Reconciled in M1.4.4. M1 remains OPEN; M2 and production use remain blocked.**

## Work recorded for M1.4.3

- Added `scripts/m1_4_3_preflight.sh` to report external prerequisites without printing credential values.
- Hardened the optional GitHub Actions OWASP job to require its NVD API key when explicitly enabled, fail on scanner errors, and use CVSS 7 as its threshold.
- Preserved the workflow's other security checks, dependency review, and secret scanning.

## Evidence reconciliation

The M1.4 acceptance checklist and a later edit in commit `07cc18f` assert that GitHub Actions run `39232339581` succeeded. However, this checkout contains no run JSON, downloaded logs, or run artifacts that independently establish its exact head SHA, job list, or test results. The local `gh` credential is invalid; the `gh run view` request could not reach GitHub's API; unauthenticated access to the run page returned 404; and `git ls-remote` failed due DNS resolution. The repository's `origin` remote is configured, contrary to the earlier M1.4.3 statement that no remote existed.

**Disposition:** run 39232339581 is **DOCUMENTED BUT NOT VERIFIED**. Do not count its reported jobs or green status as acceptance evidence until an authenticated run record or downloadable logs/artifacts are available. No new hosted run was triggered in M1.4.4.

## External gates as of M1.4.4

- Live Google sign-in and staff invitation acceptance: not yet evidenced with authorized accounts and an isolated clinic.
- Maven OWASP/NVD assessment: formally **DEFERRED — OWNER-ACCEPTED SECURITY EXCEPTION** to M10. It is not PASS. Record the unassessed Maven dependency risk; a completed Maven vulnerability assessment and disposition of applicable findings is mandatory before production deployment.
- Hosted CI: reported run unresolved as described above.
- Earlier M1.4.2 local totals (30 backend, 24 frontend, 3 browser) are prior-run evidence, not tests executed in M1.4.3.

See `docs/M1_4_4_FINAL_ACCEPTANCE_REPORT.md` for the reconciliation record.
