import { describe, expect, it } from 'vitest'
import { JOURNEY_STEPS, nextJourneyIndex, previousJourneyIndex } from './journey'
import { canSeeModule } from './catalog'
describe('M0.6 design-only patient journey', () => {
  it('orders seven distinct patient milestones without repeating a step', () => {
    expect(JOURNEY_STEPS.map(step=>step.id)).toEqual(['registration','booking','arrival','examination','planning','billing','recall'])
    expect(new Set(JOURNEY_STEPS.map(step=>step.id)).size).toBe(7)
  })
  it('links only to declared workspace modules, without bypassing role navigation', () => {
    expect(JOURNEY_STEPS.every(step=>step.checkpoints.length >= 3)).toBe(true)
    expect(canSeeModule('journey','FRONT_DESK')).toBe(true)
    expect(canSeeModule('encounters','FRONT_DESK')).toBe(false)
  })
  it('prevents navigation outside the simulation bounds', () => {
    expect(previousJourneyIndex(0)).toBe(0)
    expect(nextJourneyIndex(JOURNEY_STEPS.length-1)).toBe(JOURNEY_STEPS.length-1)
    expect(nextJourneyIndex(2)).toBe(3)
  })
})
