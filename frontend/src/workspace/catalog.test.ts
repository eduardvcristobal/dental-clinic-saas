import { describe, expect, it } from 'vitest'
import { WORKSPACE_MODULES, canSeeModule } from './catalog'
describe('clinic workspace module visibility', () => {
  it('exposes the shared patient preview to clinic roles', () => {
    for (const role of ['OWNER','ADMIN','DENTIST','FRONT_DESK']) expect(canSeeModule('patient-360',role)).toBe(true)
  })
  it('does not expose clinical authoring destinations to reception', () => {
    expect(canSeeModule('encounters','FRONT_DESK')).toBe(false)
    expect(canSeeModule('odontogram','FRONT_DESK')).toBe(false)
    expect(canSeeModule('encounters','DENTIST')).toBe(true)
  })
  it('keeps administration away from non-admin clinic roles', () => {
    expect(canSeeModule('staff','DENTIST')).toBe(false)
    expect(canSeeModule('staff','FRONT_DESK')).toBe(false)
    expect(canSeeModule('staff','OWNER')).toBe(true)
  })
  it('does not mistake proposed clinical modules for functional capabilities', () => {
    expect(WORKSPACE_MODULES.find(m => m.id === 'encounters')?.state).toBe('coming-soon')
    expect(WORKSPACE_MODULES.find(m => m.id === 'patient-360')?.state).toBe('preview')
  })
})

describe('M2.1 patient registry activation', () => { it('makes the registry active while patient 360 remains a preview', () => { expect(WORKSPACE_MODULES.find(m=>m.id==='patients')?.state).toBe('active');expect(WORKSPACE_MODULES.find(m=>m.id==='patient-360')?.state).toBe('preview') }) })
