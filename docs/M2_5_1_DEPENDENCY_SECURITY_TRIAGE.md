# M2.5.1 — Dependency Security Triage

Date: 2026-10-20
Scope: backend Maven dependency tree (Java 17, Spring Boot 3.5.x)
Scanner: OWASP Dependency-Check Maven 12.1.6, NVD 2.0 feed via the Dependency-Check maintained mirror + CISA KEV. Reports (local build artifacts, not committed): `backend/target/dependency-check-report.html`.

All scan data on this milestone dates from 2026-10-19/20. Residual findings must be re-scanned against current data before any production deployment (M10 gate retains this requirement).

## 1. Baseline and history

The M2.5 review reported an OWASP scan over the previous tree (Spring Boot 3.3.5): 84 dependencies scanned (54 unique), 13 vulnerable, 179 dependency/CVE matches, 114 unique CVEs (25 critical / 44 high / 39 medium / 6 low by unique highest severity). That scan ran with **no** applicability review — every match was treated as unresolved (`docs/evidence/m2_5/nvd-scan-summary.txt`).

Two remediation steps were applied in M2.5.1:

1. Spring Boot parent **3.3.5 → 3.5.16** (latest patch of the 3.5 line; Boot 4 / Framework 7 requires a Java-17-compatible major migration that is out of M2 scope and would need its own regression cycle). PostgreSQL JDBC driver pinned at **42.7.14** (latest 42.x).
2. Patch pins inside the Boot 3.5.16 BOM (see §3).

## 2. Verification method

For every vulnerable artifact: group/artifact/resolved version, the CVE/GHSA advisory and CVSS, direct-vs-transitive position, affected-version range, and whether this application actually maps an HTTP route to the vulnerable code path (source inspection and controller-level routing review over `backend/src/main`). The remediated dependency tree was re-verified with the scanner after each change.

## 3. Remediations (confirmed applicable, patch in-place — no major jumps)

| Group:artifact | Was | Now | Key advisories resolved | Basis |
|---|---|---|---|---|
| org.apache.tomcat.embed:tomcat-embed-core | 10.1.55 (Boot BOM) | **10.1.61** | CVE-2026-32990 (9.8), CVE-2026-65637 (9.8), CVE-2026-65905 (9.8), CVE-2026-65182, CVE-2026-55276, CVE-2026-59083, CVE-2026-59084, CVE-2026-68525 (all 9.1), CVE-2026-65183/66422/68569 (8.1), CVE-2026-53404/65927/68763 (7.5–7.7), CVE-2026-55955/55956/66299/73180, CVE-2026-50229 | Direct concern (embedded HTTP server = primary entry point). Advisories affecting 10.1.x ≤10.1.57 (≤10.1.59 for some) state "upgrade to 10.1.58" or later; 10.1.61 is the newest 10.1 line release from Apache Tomcat. Tomcat 11 requires Servlet 6.1 — 10.1.x is the compatible line for Boot 3.5 (Servlet 6.0). |
| com.fasterxml.jackson.core:jackson-databind (via jackson-bom) | 2.21.4 | **2.21.8** | CVE-2026-54515 (5.3; "from 2.8.0 until 2.18.9, 2.25.1, and 3.1.3" per report; fixed by ≥2.21.5) | Direct concern (serialization of every request/response body). 2.21.8 is a patch within the same 2.21 minor line the Boot 3.5.16 BOM manages. |
| org.apache.logging.log4j:log4j-api | 2.24.3 | **2.26.1** | CVE-2026-34479 (6.9), CVE-2026-34477/34481/49844 (6.3; MapMessage XML/JSON edge cases) | Direct concern (logging service used for all application logs; the app's own "no sensitive logging" rule leans on it). 2.26.1 is the newest 2.x release; log4j 3.x is a different API line. |

No dependency was upgraded to a new major version. Flyway (11.7.2), Spring Security (6.5.11), and Hibernate (6.6.x) remain BOM-managed; no compatible newer patches existed at scan time. No suppression file was added in this remediation.

## 4. Remaining matches after remediation (upstream: no fixed release available)

Scanner still fails the `failBuildOnCVSS=7` policy on **six artifacts / 23 unique CVEs**, all of which are supported by the **newest releases of their lines** — no patched version exists at scan time. These are therefore not "upgradeable" findings.

### 4.1 Spring Framework 6.2.19 (`spring-core`, `spring-web` — transitive via Boot BOM `spring-webmvc/servlet`)

* CVE-2026-47884 (CRIT 9.8): SSRF/RCE via `XsltView` when an app maps `/**` to view-name-driven template views. **Not reachable here** — the app serves JSON REST endpoints with no view resolution (`spring-boot-starter-web` used for REST only; no XSLT/Freemarker/thymeleaf dependency, no view controllers; `grep` over `backend/src/main` finds no XsltView.class).
* CVE-2026-47890/47891/47892/59313 (CRIT 9.8/9.1): WebFlux/SSE/Aalto XML/WebSocket/Reactor pipelines. The app is a servlet MVC deployment; `spring-boot-starter-webflux` and Spring WebFlux, RSocket, Aalto XML and SSE controllers are **not present in the classpath**.
* CVE-2026-47885 (7.5) PartEventHttpMessageReader (WebFlux); CVE-2026-47888 (7.5) RSocket SETUP-frame leak; CVE-2026-47889 (7.5) Jetty 12 WebFlux cookie serialization — all **unreachable** (same stack absence).
* CVE-2026-47886 (7.5) SpEL DoS via BigDecimal power; CVE-2026-59283 (9.1) `SimpleEvaluationContext` compiler guard bypass. The app contains **no `SpelExpressionParser` / `ExpressionParser` / `SimpleEvaluationContext` usage**; SpEL is never evaluated on user-supplied input.
* CVE-2026-47883 (6.1) UrlHandlerFilter, CVE-2026-47887 (6.1) UrlFileNameViewController open redirects — these types are not present in the app (UrlHandlerFilter is not configured; no view controllers).
* CVE-2026-59281 (6.1) Errors.hasFieldErrors() rendering with HTML escaping — app renders validation failures as JSON status responses, never via JSP/WebFlux templates (medium residual).
* CVE-2026-59282 (7.5) data-binding DoS via malicious property paths — the app binds **JSON bodies to typed DTO records** through Jackson, not `WebDataBinder` property-path binding from `@ModelAttribute`; the exposure is narrow (medium residual, monitored).

**Fix status: pending upstream** — NVD marks 6.2.0–6.2.19 (and 7.0.0–7.0.8) affected; Spring Framework 6.2.20 does not exist as a Maven release yet, and Spring Framework 7.0.9 has not been published. **Decision: deferred, will be resolved via the next Spring Boot patch release that upgrades Framework ≥6.2.20, before M10.** These findings **cannot be suppressed away**; they are recorded here with the applicability analysis above, and before production deployment each advisory's "is the affected component used?" conclusion must be re-verified against the code.

### 4.2 Spring Security 6.5.11 (`spring-security-core`, `*.web`, `*.oauth2-resource-server` — transitive via Boot BOM)

* CVE-2026-59270 (9.1): the `UnboundIdContainer` embedded LDAP test container binds on all interfaces with a default admin credential. The app uses **no LDAP at all** (authentication is Google-issued OAuth2 JWTs against a JWKS endpoint; `UnboundId` is not on the classpath — never imported).
* CVE-2026-41707 (7.4): DPoP proof replay cache bypass. The app does **not** consume DPoP proofs (`OAuth2JwtDecoder` is configured for a conventional JWKS Bearer token); DPoP decoders are unused.
* CVE-2026-47841 (7.4): WebAuthn distributed-session user-verification bypass. WebAuthn features (navigators, dependency and dependency property `spring-security-*` WebAuthn module) are not present.
* CVE-2026-47842 (6.5): AES/CBC with an all-zero IV in `AesBytesEncryptor` when the two-argument constructor is used. The app **never imports `.crypto.AesBytesEncryptor`**; tokens are random and hashed (`SecureRandom` cycles, see refresh-token handling).
* CVE-2026-59276 (5.9): recommendation that Spring Security components use constant-time comparison for security-sensitive values. This project's own security-sensitive comparisons run in application code (SHA-256 refresh-token digests, invitation keys) and are covered by code review and unit tests; the upstream item is a hygiene advisory, not an exploitable defect in this app.

**Fix status: pending upstream** — no patched 6.5.x exists beyond 6.5.11; a fix is expected in the next Spring Security 6.5.x release, which a future Spring Boot 3.5.x patch will consume. **Decision: deferred; re-scanned before M10 production deployment.** No suppression file is used; the re-verification instructions are the same as §4.1.

### 4.3 Spring Data JPA 3.5.13 (`spring-data-jpa`, transitive)

* CVE-2026-47834 (6.5): Sort validation bypass when a client injects a `Sort` with crafted payloads. The app queries via `JpaRepository` with **fixed server-side ordering** (`Pageable` parameter exists in `PatientRepository.search` but no controller passes a client-provided sort — repository method is not reachable from user input as shown by routing review [see above]). **Not applicable in this application as written.**

**Fix status:** pending upstream; same M10 recheck applies. **Decision: DEFERRED (3 dep-artifacts × CVEs above, all elective-scope: monitor upstream releases)**.

## 5. Residual-risk register (lifted into M10 production gates)

| # | Advisory | Severity | Applicability conclusion | Action required before production (M10) |
|---|---|---|---|---|
| R1 | Spring Framework 6.2.19 set (17 CVEs) | up to 9.8 | Servlet/MVC-only app; no SpEL, no XSLT, no reactive stack | Verify a Boot/Framework patch consuming 6.2.20+ exists and apply it; re-run scan |
| R2 | Spring Security 6.5.11 set (5 CVEs) | up to 9.1 | No LDAP/DPoP/WebAuthn/AES usage | Same: confirm fix release, re-verify applicability |
| R3 | Spring Data JPA Sort validation | 6.5 | No client-supplied comparator | Same |
| R4 | 'any matching CVEs (KEV)' | n/a | Scan completed with KEV updated | Re-scan at deploy time |

## 6. What is NOT counted as remediation

* No suppression XML entries were created.
* No residual finding was marked false positive — all retain applicability analysis (§4.1–§4.3), are tracked in §5, and count as unresolved for the M10 gate.
* PostgreSQL/Flyway versions were not downgraded or re-pinned beyond driver 42.7.14; tenant isolation tests were re-run and pass.

## 7. Scan reproduction

```sh
cd backend
mvn -B -ntp org.owasp:dependency-check-maven:12.1.6:check \
  -DfailBuildOnCVSS=7 -DfailOnError=true \
  -DconnectionTimeout=10000 -DreadTimeout=60000 -DnvdMaxRetryCount=5
```
(Exit 1 while §4–§5 advisories remain unfixed upstream; the HTML lives at `backend/target/dependency-check-report.html`.)
