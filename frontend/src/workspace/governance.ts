import { WORKSPACE_MODULES, type WorkspaceRole, type CapabilityState } from './catalog'

/** Presentation governance only. Server-side authorization remains mandatory. */
export type FeatureState = 'active' | CapabilityState
export type FeatureAction = 'view-demo' | 'edit-clinical-demo' | 'manage-clinic-demo'
export type FeatureDecision = { allowed: boolean; reason: 'ok' | 'unknown-module' | 'unknown-role' | 'role-restricted' | 'not-active' | 'entitlement-required' }
export type EntitlementSnapshot = { enabledModules: readonly string[]; verifiedByServer: boolean }

const VALID_ROLES: readonly string[] = ['OWNER', 'ADMIN', 'DENTIST', 'FRONT_DESK']
const CLINICAL_AUTHORS: readonly WorkspaceRole[] = ['OWNER', 'ADMIN', 'DENTIST']
const CLINIC_ADMINS: readonly WorkspaceRole[] = ['OWNER', 'ADMIN']

/** All listed clinic workspaces are previews or future modules, never production business APIs. */
export const FEATURE_REGISTRY = WORKSPACE_MODULES.map(module => ({ ...module, requiresEntitlement: false as boolean }))

export function moduleDecision(id: string, role: string, action: FeatureAction, entitlements?: EntitlementSnapshot): FeatureDecision {
  const module = FEATURE_REGISTRY.find(m => m.id === id)
  if (!module) return { allowed: false, reason: 'unknown-module' }
  if (!VALID_ROLES.includes(role)) return { allowed: false, reason: 'unknown-role' }
  if (!module.roles.includes(role as WorkspaceRole)) return { allowed: false, reason: 'role-restricted' }
  if (module.requiresEntitlement && (!entitlements?.verifiedByServer || !entitlements.enabledModules.includes(id))) return { allowed: false, reason: 'entitlement-required' }
  if (action === 'view-demo') return module.state === 'preview' ? { allowed: true, reason: 'ok' } : { allowed: false, reason: 'not-active' }
  if (module.state !== 'preview') return { allowed: false, reason: 'not-active' }
  if (action === 'edit-clinical-demo' && id === 'patient-360' && CLINICAL_AUTHORS.includes(role as WorkspaceRole)) return { allowed: true, reason: 'ok' }
  if (action === 'manage-clinic-demo' && CLINIC_ADMINS.includes(role as WorkspaceRole)) return { allowed: true, reason: 'ok' }
  return { allowed: false, reason: 'role-restricted' }
}

/** Real write APIs must not use UI preview permissions as an authorization decision. */
export function canPerformLiveBusinessAction(): false { return false }
