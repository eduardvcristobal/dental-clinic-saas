import { describe, it, expect } from 'vitest'
import { FEATURE_REGISTRY, moduleDecision, canPerformLiveBusinessAction } from './governance'

describe('M0.7 navigation and module governance', () => {
  it('registry IDs are unique and all are explicit feature states', () => {
    expect(new Set(FEATURE_REGISTRY.map(m=>m.id)).size).toBe(FEATURE_REGISTRY.length)
    expect(FEATURE_REGISTRY.every(m=>['preview','coming-soon','active'].includes(m.state))).toBe(true)
  })
  it('fails closed for unrecognized users, routes and live business actions', () => {
    expect(moduleDecision('patients','PLATFORM_ADMIN','view-demo').allowed).toBe(false)
    expect(moduleDecision('patients','UNRECOGNIZED','view-demo').allowed).toBe(false)
    expect(moduleDecision('nonexistent','OWNER','view-demo').allowed).toBe(false)
    expect(canPerformLiveBusinessAction()).toBe(false)
  })
  it('prevents reception from editing clinical demos and reaching admin screens', () => {
    expect(moduleDecision('patient-360','FRONT_DESK','view-demo').allowed).toBe(true)
    expect(moduleDecision('patient-360','FRONT_DESK','edit-clinical-demo').allowed).toBe(false)
    expect(moduleDecision('module-status','FRONT_DESK','view-demo').allowed).toBe(false)
    expect(moduleDecision('patient-360','DENTIST','edit-clinical-demo').allowed).toBe(true)
  })
  it('never treats coming soon modules as working screens', () => {
    expect(moduleDecision('encounters','DENTIST','view-demo').allowed).toBe(false)
    expect(moduleDecision('branches','OWNER','manage-clinic-demo').allowed).toBe(false)
  })
  it('does not treat unverified entitlements as grants', () => {
    expect(FEATURE_REGISTRY.every(m => m.requiresEntitlement === false)).toBe(true)
  })
})
