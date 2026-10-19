# M1.4.6 — Live Invitation Acceptance and Replay Evidence

**Date:** 2026-10-19  
**Environment:** local development, temporary backend configured only for disposable E2E database `dental_m146_e2e`; frontend origin `http://localhost:5173`.

## Procedure and observed result

1. The clinic Owner authenticated using real Google sign-in in isolated clinic `m146-owner-20261019`.
2. The Owner created a fresh `FRONT_DESK` invitation for the authorized recipient account, assigned to `Main Test Branch`.
3. To preserve the same one-time link without revealing it, the app’s Copy control put it on the local clipboard and the link was loaded into two browser acceptance forms. Neither browser URL nor token value was recorded in output or report.
4. The recipient completed real Google verification and accepted the link from the first form. The app displayed `Invitation accepted.`.
5. The same recipient completed Google verification again and submitted the second form holding the same link. The application displayed `ApiError: Invalid or expired invitation`; the HTTP response was **403**.
6. The acceptance timestamp and side-effect counts were checked before and after replay. The accepted timestamp remained `2026-10-19 19:58:05.749099+08`; there was exactly one active `FRONT_DESK` membership, one assignment to `Main Test Branch`, one invitation-created event, and one invitation-accepted event.

Sanitized before-replay result:

```text
2026-10-19 19:58:05.749099+08 | 1 active matching membership | 1 matching assignment | 1 created event | 1 accepted event | Main Test Branch
```

Sanitized after-replay result:

```text
accepted_timestamp_unchanged=true | staff_memberships=1 | branch_assignments=1 | created_events=1 | accepted_events=1 | accepted=true
```

No duplicate membership, branch assignment, or event was created. The invitation remained accepted; replay did not change its timestamp. The query did not select or print the stored token hash.

## Tenant isolation cross-check

The same disposable database contained a second clinic `m146-second-20261019`. Using the restricted, non-superuser `dental_app` role with its tenant GUC scoped to the second clinic, a read-only query returned:

```text
second clinic branch visible=1 | first clinic branch visible=0
```

The browser separately showed the second clinic owner’s own Clinic Settings. Existing PostgreSQL cross-clinic integration tests also passed in the final Maven run.

## Automated atomicity evidence

Java 17 Maven integration test `simultaneousHttpInvitationClaimsCreateExactlyOneStaffAccount` submitted two concurrent acceptance requests for one invitation. Result: one HTTP 200 and one HTTP 403, exactly one user, and exactly one accepted invitation. This ran against the fresh isolated integration database. The full M1.4.6 Java suite passed 33/33, including expiry, revocation, replay, membership/branch assignment, and tenant isolation coverage.

## Secret handling

The invitation link was held only in the local browser clipboard and in-memory form state for the test. The Google identity token was handled by the Google sign-in component and backend request. Neither value was logged, copied into the report, or included in screenshots/artifacts. The invitation has been accepted and replayed, so the retained link is spent and rejected by the backend. The test server was subsequently stopped. Chrome became the user’s active app during final form cleanup, so I did not close their browser tabs or overwrite the clipboard; any remaining clipboard value is the already-consumed, invalid invitation link.
