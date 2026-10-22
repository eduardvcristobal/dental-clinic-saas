import { describe, expect, it } from 'vitest'
import {
  axisTicks, blockColor, blockLabel, buildLanes, clinicDateKey, clinicMinutes, dayWindow, formatClock,
  groupLanes, laneUtilization, layoutLane, parseClock, statusTone, upcomingBlocks,
  type BoardBlock, type BoardBranch, type BoardLane,
} from './dayBoard'

const zone = 'Asia/Manila'

const block = (overrides: Partial<BoardBlock> & Pick<BoardBlock, 'id'>): BoardBlock => ({
  starts_at: '2026-10-21T01:00:00Z', ends_at: '2026-10-21T01:30:00Z', status: 'BOOKED', branch_id: 'b1',
  patient_name: 'Ana Reyes', activity: 'Checkup', type_color: null,
  dentist_id: 'd1', dentist_name: 'Dr. Ada', dentist_color: null, chair_id: 'c1', chair_name: 'Chair 1',
  ...overrides,
})

const branch = (overrides: Partial<BoardBranch> & Pick<BoardBranch, 'id'>): BoardBranch => ({
  name: 'Main Branch', opens_at: '08:00:00', closes_at: '17:00:00', ...overrides,
})

const lane = (overrides: Partial<BoardLane> & Pick<BoardLane, 'id'>): BoardLane => ({
  kind: 'CHAIR', name: 'Chair 1', branch_id: 'b1', color: null, ...overrides,
})

describe('clinicMinutes', () => {
  it('reads the wall clock of the clinic timezone, not the browser timezone', () => {
    expect(clinicMinutes('2026-10-21T01:00:00Z', zone)).toBe(9 * 60)
  })
  it('keeps the same wall clock across zones for the same instant', () => {
    expect(clinicMinutes('2026-10-21T01:00:00Z', 'UTC')).toBe(60)
  })
  it('falls back to the browser timezone for an unusable zone', () => {
    const local = new Date('2026-10-21T01:00:00Z')
    expect(clinicMinutes('2026-10-21T01:00:00Z', 'Not/AZone')).toBe(local.getHours() * 60 + local.getMinutes())
  })
})

describe('parseClock', () => {
  it('reads seconds and short forms', () => {
    expect(parseClock('08:00:00')).toBe(480)
    expect(parseClock('8:30')).toBe(510)
  })
  it('rejects missing or impossible values', () => {
    expect(parseClock(null)).toBeNull()
    expect(parseClock('')).toBeNull()
    expect(parseClock('25:00')).toBeNull()
    expect(parseClock('08:70')).toBeNull()
  })
})

describe('formatClock', () => {
  it('renders padded hours and minutes', () => {
    expect(formatClock(0)).toBe('00:00')
    expect(formatClock(9 * 60 + 5)).toBe('09:05')
    expect(formatClock(23 * 60 + 59)).toBe('23:59')
  })
})

describe('dayWindow', () => {
  it('spans the branch opening hours', () => {
    const window = dayWindow([branch({ id: 'b1' })], [], zone)
    expect(window.start).toBe(480)
    expect(window.end).toBe(1020)
  })
  it('widens to cover appointments that fall outside opening hours', () => {
    const window = dayWindow([branch({ id: 'b1' })], [block({ id: 'a', starts_at: '2026-10-21T09:00:00Z', ends_at: '2026-10-21T10:00:00Z' })], zone)
    expect(window.start).toBe(480)
    expect(window.end).toBe(1080)
  })
  it('keeps a minimum eight hour axis when the clinic day is shorter', () => {
    const window = dayWindow([branch({ id: 'b1', opens_at: '09:00:00', closes_at: '12:00:00' })], [], zone)
    expect(window.end - window.start).toBeGreaterThanOrEqual(480)
  })
  it('falls back to a default day when no hours are configured', () => {
    const window = dayWindow([branch({ id: 'b1', opens_at: null, closes_at: null })], [], zone)
    expect(window.start).toBe(480)
    expect(window.end).toBe(1080)
  })
})

describe('clinicDateKey', () => {
  it('resolves the calendar date in the clinic timezone', () => {
    expect(clinicDateKey('2026-10-21T17:30:00Z', zone)).toBe('2026-10-22')
    expect(clinicDateKey('2026-10-21T17:30:00Z', 'UTC')).toBe('2026-10-21')
  })
  it('falls back to the browser timezone for an unusable zone', () => {
    const local = new Date('2026-10-21T17:30:00Z')
    const expected = `${local.getFullYear()}-${String(local.getMonth() + 1).padStart(2, '0')}-${String(local.getDate()).padStart(2, '0')}`
    expect(clinicDateKey('2026-10-21T17:30:00Z', 'Not/AZone')).toBe(expected)
  })
})

describe('layoutLane', () => {
  const window = { start: 480, end: 1020 }

  it('positions a block by start time and duration', () => {
    const positioned = layoutLane([block({ id: 'a', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T02:30:00Z' })], window, zone)
    expect(positioned).toHaveLength(1)
    expect(positioned[0].startMin).toBe(600)
    expect(positioned[0].left).toBeCloseTo((600 - 480) / 540 * 100)
    expect(positioned[0].width).toBeCloseTo(30 / 540 * 100)
  })

  it('splits overlapping blocks into side-by-side tracks', () => {
    const positioned = layoutLane([
      block({ id: 'a', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T03:00:00Z' }),
      block({ id: 'b', starts_at: '2026-10-21T02:30:00Z', ends_at: '2026-10-21T03:30:00Z' }),
    ], window, zone)
    expect(positioned).toHaveLength(2)
    expect(positioned[0].columns).toBe(2)
    expect(positioned[0].column).not.toBe(positioned[1].column)
  })

  it('keeps back-to-back blocks on a single track', () => {
    const positioned = layoutLane([
      block({ id: 'a', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T02:30:00Z' }),
      block({ id: 'b', starts_at: '2026-10-21T02:30:00Z', ends_at: '2026-10-21T03:00:00Z' }),
    ], window, zone)
    expect(positioned[0].columns).toBe(1)
    expect(positioned[1].column).toBe(0)
  })

  it('never lets a block leave the axis', () => {
    const positioned = layoutLane([block({ id: 'a', starts_at: '2026-10-21T00:00:00Z', ends_at: '2026-10-21T02:00:00Z' })], window, zone)
    expect(positioned[0].left).toBeGreaterThanOrEqual(0)
    expect(positioned[0].left + positioned[0].width).toBeLessThanOrEqual(100.001)
  })

  it('keeps the true duration of live blocks when a dead visit overlaps them', () => {
    const live = layoutLane([
      block({ id: 'live', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T03:00:00Z' }),
      block({ id: 'dead', status: 'CANCELLED', starts_at: '2026-10-21T02:30:00Z', ends_at: '2026-10-21T03:30:00Z' }),
    ], window, zone)
    const liveBlock = live.find(item => item.block.id === 'live')!
    const deadBlock = live.find(item => item.block.id === 'dead')!
    expect(liveBlock.width).toBeCloseTo(60 / 540 * 100)
    expect(liveBlock.loose).toBe(false)
    expect(deadBlock.width).toBeCloseTo(60 / 540 * 100)
    expect(deadBlock.loose).toBe(true)
  })
})

describe('laneUtilization', () => {
  const window = { start: 480, end: 1020 }

  it('measures booked minutes against the open day', () => {
    const utilization = laneUtilization([block({ id: 'a', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T02:30:00Z' })], window, zone)
    expect(utilization).toBe(Math.round(30 / 540 * 100))
  })

  it('ignores cancelled appointments', () => {
    const cancelled = block({ id: 'a', status: 'CANCELLED', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T04:00:00Z' })
    expect(laneUtilization([cancelled], window, zone)).toBe(0)
  })
})

describe('buildLanes', () => {
  it('keeps a chair lane on its own bookings and a provider lane on its own list', () => {
    const lanes = buildLanes(
      [lane({ id: 'c1' }), lane({ id: 'd1', kind: 'DENTIST', branch_id: null })],
      [block({ id: 'a', chair_id: 'c1', dentist_id: 'd1' }), block({ id: 'b', chair_id: 'c2', dentist_id: 'd2' })],
      { start: 480, end: 1020 }, zone,
    )
    expect(lanes[0].blocks.map(item => item.id)).toEqual(['a'])
    expect(lanes[1].blocks.map(item => item.id)).toEqual(['a'])
  })
})

describe('groupLanes', () => {
  it('groups chairs under their branch and providers in their own group', () => {
    const groups = groupLanes([lane({ id: 'c1' }), lane({ id: 'd1', kind: 'DENTIST', branch_id: null })], [branch({ id: 'b1' })])
    expect(groups.map(group => group.id)).toEqual(['b1', 'providers'])
    expect(groups[0].hint).toBe('08:00 – 17:00')
  })

  it('reports missing opening hours instead of hiding the branch', () => {
    const groups = groupLanes([lane({ id: 'c1' })], [branch({ id: 'b1', opens_at: null, closes_at: null })])
    expect(groups[0].hint).toBe('Hours not set')
  })
})

describe('upcomingBlocks', () => {
  it('lists the next appointments that still have to run', () => {
    const blocks = [
      block({ id: 'past', starts_at: '2026-10-20T23:00:00Z', ends_at: '2026-10-21T00:00:00Z' }),
      block({ id: 'next', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T03:00:00Z' }),
      block({ id: 'later', starts_at: '2026-10-21T04:00:00Z', ends_at: '2026-10-21T05:00:00Z' }),
      block({ id: 'done', status: 'COMPLETED', starts_at: '2026-10-21T05:00:00Z', ends_at: '2026-10-21T06:00:00Z' }),
    ]
    const upcoming = upcomingBlocks(blocks, 8 * 60, zone, 2)
    expect(upcoming.map(item => item.id)).toEqual(['next', 'later'])
  })
})

describe('statusTone', () => {
  it('maps appointment statuses onto display tones', () => {
    expect(statusTone('IN_CHAIR')).toBe('in-chair')
    expect(statusTone('NO_SHOW')).toBe('no-show')
    expect(statusTone('SOMETHING_NEW')).toBe('booked')
  })
})

describe('blockColor', () => {
  it('uses lifecycle status colour even when an appointment type has a colour', () => {
    expect(blockColor(block({ id: 'a', status: 'BOOKED', type_color: '#123456' }))).toBe('#3b82f6')
    expect(blockColor(block({ id: 'b', status: 'CONFIRMED', type_color: '#123456' }))).toBe('#7c3aed')
  })
  it('falls back to a stable colour for types without one', () => {
    const first = blockColor(block({ id: 'a', activity: 'Cleaning' }))
    const second = blockColor(block({ id: 'b', activity: 'Cleaning' }))
    expect(first).toBe(second)
    expect(first).toMatch(/^#[0-9a-f]{6}$/)
  })
})

describe('axisTicks', () => {
  it('marks every hour of the axis', () => {
    expect(axisTicks({ start: 480, end: 600 }, 60)).toEqual([480, 540, 600])
  })
})

describe('blockLabel', () => {
  it('describes the appointment for assistive technology', () => {
    const label = blockLabel(block({ id: 'a', starts_at: '2026-10-21T02:00:00Z', ends_at: '2026-10-21T02:30:00Z' }), zone)
    expect(label).toContain('10:00 to 10:30')
    expect(label).toContain('Ana Reyes')
    expect(label).toContain('Dr. Ada')
    expect(label).toContain('Booked')
  })
})
