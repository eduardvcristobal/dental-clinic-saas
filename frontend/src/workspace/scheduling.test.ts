import { describe, expect, it } from 'vitest'
import { calendarRange, filterEvents, nextActions } from './scheduling'
import type { Event } from './SchedulingWorkspace'

const ev = (overrides: Partial<Event> & Pick<Event, 'id'>): Event => ({
  patient_id: 'p1', first_name: 'A', last_name: 'B', dentist_name: 'Dr. Ada',
  chair_id: null, starts_at: '2026-10-26T10:00:00Z', ends_at: '2026-10-26T10:30:00Z', status: 'BOOKED',
  reason: null, version: 0, ...overrides,
})

describe('calendarRange', () => {
  it('covers one day for the Day view', () => {
    const r = calendarRange('2026-10-20', 'Day')
    expect(r.from.toISOString()).toMatch(/^2026-10-(19|20)/)
    expect((r.to.getTime() - r.from.getTime()) / 86400000).toBe(1)
  })
  it('covers seven days for the Week view', () => {
    const r = calendarRange('2026-10-20', 'Week')
    expect((r.to.getTime() - r.from.getTime()) / 86400000).toBe(7)
  })
  it('covers thirty days for the Month view', () => {
    const r = calendarRange('2026-10-20', 'Month')
    expect((r.to.getTime() - r.from.getTime()) / 86400000).toBe(30)
  })
})

describe('filterEvents', () => {
  const rows = [
    ev({ id: '1', dentist_name: 'Dr. Ada', chair_id: 'chair-1' }),
    ev({ id: '2', dentist_name: 'Dr. Abel', chair_id: 'chair-2' }),
    ev({ id: '3', dentist_name: 'Dr. Ada', chair_id: 'chair-2' }),
  ]
  it('returns all rows without filters', () => {
    expect(filterEvents(rows, '', '')).toHaveLength(3)
  })
  it('filters by dentist name', () => {
    expect(filterEvents(rows, 'Dr. Ada', '').map(e => e.id)).toEqual(['1', '3'])
  })
  it('filters by chair id', () => {
    expect(filterEvents(rows, '', 'chair-2').map(e => e.id)).toEqual(['2', '3'])
  })
  it('combines both filters and keeps unscheduled chairs excluded', () => {
    expect(filterEvents(rows, 'Dr. Ada', 'chair-2').map(e => e.id)).toEqual(['3'])
  })
  it('never matches a null chair against a chair filter', () => {
    expect(filterEvents([ev({ id: '4' })], '', 'chair-1')).toHaveLength(0)
  })
})

describe('nextActions', () => {
  it('allows confirming, arrival, no-show and cancellation from BOOKED', () => {
    expect(nextActions('BOOKED').map(a => a.status)).toEqual(['CONFIRMED', 'ARRIVED', 'NO_SHOW', 'CANCELLED'])
  })
  it('requires a cancellation prompt reason', () => {
    expect(nextActions('BOOKED').find(a => a.status === 'CANCELLED')?.reason).toBe('prompt')
  })
  it('has no further actions for COMPLETED, CANCELLED and NO_SHOW', () => {
    expect(nextActions('COMPLETED')).toHaveLength(0)
    expect(nextActions('CANCELLED')).toHaveLength(0)
    expect(nextActions('NO_SHOW')).toHaveLength(0)
  })
  it('offers completion only from IN_CHAIR', () => {
    expect(nextActions('IN_CHAIR').map(a => a.status)).toEqual(['COMPLETED'])
  })
})
