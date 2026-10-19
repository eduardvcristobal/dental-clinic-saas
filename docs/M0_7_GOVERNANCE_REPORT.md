# M0.7 Feature readiness / navigation governance

## Implemented
- `frontend/src/workspace/governance.ts`: centralized client-side preview decisions; unknown modules or roles denied; permissions separate from feature readiness.
- `frontend/src/workspace/catalog.ts`: explicit active / preview / coming-soon states, plus owner/admin Module Status view.
- `frontend/src/workspace/ModuleStatusPreview.tsx`: searchable module inventory with display roles and truthful readiness status.
- `frontend/src/workspace/ClinicWorkspace.tsx`: navigation demo labels and clinical demonstration authoring decision through governance function.
- `frontend/src/workspace/governance.test.ts`: negative role and module-state tests.

## Non-goals and risks
- **No production clinical/business module is activated.** Preview features use fictional data and do not write to the database.
- No subscription entitlements are issued; future entitlement service must supply server-verified grants; unknown grants must fail closed.
- Client routing/navigation is NOT security; the Java service must enforce per-action permissions and tenant policies on every future API.
- Platform Admin console is separate and requires an independent restricted access review; M0.4 safety blockers remain.

## Validation
Pending execution of frontend test/build/lint in an environment with locked dependencies. The test source is included; do not claim it has passed until run.
