export type LaneKind = 'CHAIR' | 'DENTIST'

export interface BoardBranch { id: string; name: string; opens_at: string | null; closes_at: string | null }
export interface BoardLane { id: string; kind: LaneKind; name: string; branch_id: string | null; color: string | null }
export interface BoardBlock {
  id: string; starts_at: string; ends_at: string; status: string; branch_id: string
  patient_name: string; activity: string; type_color: string | null
  dentist_id: string; dentist_name: string; dentist_color: string | null
  chair_id: string | null; chair_name: string
}

export interface BoardPayload {
  date: string; timezone: string
  branches: BoardBranch[]; lanes: BoardLane[]; blocks: BoardBlock[]
}

const MINUTES_PER_DAY = 1440
const MIN_WINDOW_MINUTES = 8 * 60

const FALLBACK_COLORS = ['#0e7c86', '#2f6fed', '#8b5cf6', '#c2750a', '#c0367a', '#0f8a5f', '#c0362f', '#4f46e5']

export type StatusTone = 'booked' | 'confirmed' | 'arrived' | 'in-chair' | 'completed' | 'cancelled' | 'no-show'

export const STATUS_LABELS: Record<StatusTone, string> = {
  booked: 'Booked', confirmed: 'Confirmed', arrived: 'Arrived', 'in-chair': 'In chair',
  completed: 'Completed', cancelled: 'Cancelled', 'no-show': 'No show',
}

/** Status tone used for block accents; unknown statuses fall back to the booked tone. */
export function statusTone(status: string): StatusTone {
  switch (status) {
    case 'CONFIRMED': return 'confirmed'
    case 'ARRIVED': return 'arrived'
    case 'IN_CHAIR': return 'in-chair'
    case 'COMPLETED': return 'completed'
    case 'CANCELLED': return 'cancelled'
    case 'NO_SHOW': return 'no-show'
    default: return 'booked'
  }
}

/**
 * Minutes since local midnight in the clinic timezone for an ISO instant.
 * Falls back to the browser timezone when the clinic timezone cannot be resolved.
 */
export function clinicMinutes(iso: string, timezone: string): number {
  try {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, hourCycle: 'h23', hour: '2-digit', minute: '2-digit' })
      .formatToParts(new Date(iso))
    const read = (type: string) => Number(parts.find(part => part.type === type)?.value ?? 0)
    return read('hour') * 60 + read('minute')
  } catch {
    const local = new Date(iso)
    return local.getHours() * 60 + local.getMinutes()
  }
}

/** 'YYYY-MM-DD' calendar date of an instant in the clinic timezone. */
export function clinicDateKey(iso: string, timezone: string): string {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(iso))
  } catch {
    const local = new Date(iso)
    return `${local.getFullYear()}-${String(local.getMonth() + 1).padStart(2, '0')}-${String(local.getDate()).padStart(2, '0')}`
  }
}

/** Minutes since midnight for a clock value such as '08:00' or '08:00:00'. */
export function parseClock(value: string | null | undefined): number | null {
  if (!value) return null
  const match = /^(\d{1,2}):(\d{2})/.exec(value.trim())
  if (!match) return null
  const hours = Number(match[1]); const minutes = Number(match[2])
  if (hours > 23 || minutes > 59) return null
  return hours * 60 + minutes
}

/** 'HH:MM' label for a minute count inside the day. */
export function formatClock(minutes: number): string {
  const wrapped = ((Math.round(minutes) % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
  return `${String(Math.floor(wrapped / 60)).padStart(2, '0')}:${String(wrapped % 60).padStart(2, '0')}`
}

export interface DayWindow { start: number; end: number }

/**
 * Shared time axis for the board: the earliest branch opening through the latest closing,
 * widened to cover every appointment that overlaps the day and rounded to whole hours.
 */
export function dayWindow(branches: BoardBranch[], blocks: BoardBlock[], timezone: string): DayWindow {
  let open = Number.POSITIVE_INFINITY
  let close = Number.NEGATIVE_INFINITY
  for (const branch of branches) {
    const from = parseClock(branch.opens_at)
    const until = parseClock(branch.closes_at)
    if (from !== null) open = Math.min(open, from)
    if (until !== null) close = Math.max(close, until)
  }
  if (!Number.isFinite(open)) open = 8 * 60
  if (!Number.isFinite(close)) close = 18 * 60
  let start = open
  let end = close
  for (const block of blocks) {
    const from = clinicMinutes(block.starts_at, timezone)
    const until = clinicMinutes(block.ends_at, timezone)
    if (until <= from) continue
    start = Math.min(start, from)
    end = Math.max(end, until)
  }
  start = Math.max(0, Math.floor(start / 60) * 60)
  end = Math.min(MINUTES_PER_DAY, Math.ceil(end / 60) * 60)
  if (end - start < MIN_WINDOW_MINUTES) {
    end = Math.min(MINUTES_PER_DAY, start + MIN_WINDOW_MINUTES)
    if (end - start < MIN_WINDOW_MINUTES) start = Math.max(0, end - MIN_WINDOW_MINUTES)
  }
  return { start, end }
}

export interface PositionedBlock {
  block: BoardBlock; startMin: number; endMin: number
  left: number; width: number; column: number; columns: number
  /** Drawn outside the overlap packing (dead visits that may sit under a live one). */
  loose: boolean
}

interface Interval { block: BoardBlock; startMin: number; endMin: number }

/** Blocks that belong to a lane: a chair lane takes chair bookings, a provider lane takes their own list. */
export function blocksForLane(lane: BoardLane, blocks: BoardBlock[]): BoardBlock[] {
  return blocks.filter(block => (lane.kind === 'CHAIR' ? block.chair_id === lane.id : block.dentist_id === lane.id))
}

/** Cancelled and no-show visits no longer hold the resource, so they never force others into side-by-side tracks. */
const isDead = (status: string) => status === 'CANCELLED' || status === 'NO_SHOW'

/** Greedy interval partitioning: the smallest number of side-by-side tracks for the given blocks. */
function assignColumns(items: Interval[]): Map<string, number> {
  const columnEnds: number[] = []
  const assignment = new Map<string, number>()
  for (const item of items) {
    let column = columnEnds.findIndex(end => end <= item.startMin)
    if (column === -1) { column = columnEnds.length; columnEnds.push(item.endMin) }
    else columnEnds[column] = item.endMin
    assignment.set(item.block.id, column)
  }
  return assignment
}

/**
 * Positions the blocks of a single lane across the shared axis.
 * Overlapping active blocks are split into side-by-side tracks so nothing is drawn on top of
 * anything else; cancelled and no-show visits keep their true duration underneath.
 */
export function layoutLane(blocks: BoardBlock[], window: DayWindow, timezone: string): PositionedBlock[] {
  const span = Math.max(1, window.end - window.start)
  const items: Interval[] = blocks
    .map(block => {
      const startMin = clinicMinutes(block.starts_at, timezone)
      const endMin = clinicMinutes(block.ends_at, timezone)
      return { block, startMin, endMin: Math.max(endMin, startMin + 15) }
    })
    .sort((a, b) => a.startMin - b.startMin || a.block.id.localeCompare(b.block.id))

  const positioned: PositionedBlock[] = []
  let cluster: Interval[] = []
  let clusterEnd = Number.NEGATIVE_INFINITY

  const flush = () => {
    if (cluster.length === 0) return
    const columns = assignColumns(cluster)
    const columnCount = Math.max(1, columns.size)
    const clusterStart = Math.min(...cluster.map(item => item.startMin))
    const clusterSpan = Math.max(1, Math.max(...cluster.map(item => item.endMin)) - clusterStart)
    const slotMinutes = clusterSpan / columnCount
    for (const item of cluster) {
      const column = columns.get(item.block.id) ?? 0
      const slotStart = clusterStart + column * slotMinutes
      const drawStart = Math.max(item.startMin, slotStart)
      const drawEnd = Math.min(item.endMin, slotStart + slotMinutes)
      const left = ((drawStart - window.start) / span) * 100
      const width = ((Math.max(drawEnd, drawStart + 10) - drawStart) / span) * 100
      positioned.push({
        block: item.block, startMin: item.startMin, endMin: item.endMin,
        left: Math.min(100, Math.max(0, left)),
        width: Math.min(100 - Math.max(0, left), Math.max(0.9, width)),
        column, columns: columnCount, loose: false,
      })
    }
    cluster = []
    clusterEnd = Number.NEGATIVE_INFINITY
  }

  for (const item of items) {
    if (isDead(item.block.status)) {
      const left = ((item.startMin - window.start) / span) * 100
      const width = ((item.endMin - item.startMin) / span) * 100
      positioned.push({
        block: item.block, startMin: item.startMin, endMin: item.endMin,
        left: Math.min(100, Math.max(0, left)),
        width: Math.min(100 - Math.max(0, left), Math.max(0.9, width)),
        column: 0, columns: 1, loose: true,
      })
      continue
    }
    if (cluster.length > 0 && item.startMin >= clusterEnd) flush()
    cluster.push(item)
    clusterEnd = Math.max(clusterEnd, item.endMin)
  }
  flush()
  return positioned
}

/** Share of the open day already booked on a lane, in percent. */
export function laneUtilization(blocks: BoardBlock[], window: DayWindow, timezone: string): number {
  const span = Math.max(1, window.end - window.start)
  let booked = 0
  for (const block of blocks) {
    if (block.status === 'CANCELLED') continue
    const from = clinicMinutes(block.starts_at, timezone)
    const until = clinicMinutes(block.ends_at, timezone)
    booked += Math.max(0, Math.min(until, window.end) - Math.max(from, window.start))
  }
  return Math.round((booked / span) * 100)
}

export interface LaneModel { lane: BoardLane; blocks: BoardBlock[]; positioned: PositionedBlock[]; utilization: number }

/** Lane models for the given lane list, ready for rendering. */
export function buildLanes(lanes: BoardLane[], blocks: BoardBlock[], window: DayWindow, timezone: string): LaneModel[] {
  return lanes.map(lane => {
    const own = blocksForLane(lane, blocks)
    return { lane, blocks: own, positioned: layoutLane(own, window, timezone), utilization: laneUtilization(own, window, timezone) }
  })
}

export interface LaneGroup { id: string; name: string; hint: string; lanes: BoardLane[] }

/** Groups lanes under their branch, then collects the clinic-wide provider lanes. */
export function groupLanes(lanes: BoardLane[], branches: BoardBranch[]): LaneGroup[] {
  const groups: LaneGroup[] = []
  for (const branch of branches) {
    const owned = lanes.filter(lane => lane.kind === 'CHAIR' && lane.branch_id === branch.id)
    if (owned.length === 0) continue
    const from = parseClock(branch.opens_at)
    const until = parseClock(branch.closes_at)
    groups.push({ id: branch.id, name: branch.name, hint: from === null || until === null ? 'Hours not set' : `${formatClock(from)} – ${formatClock(until)}`, lanes: owned })
  }
  const providers = lanes.filter(lane => lane.kind === 'DENTIST')
  if (providers.length > 0) groups.push({ id: 'providers', name: 'Providers', hint: 'All branches', lanes: providers })
  const other = lanes.filter(lane => lane.kind === 'CHAIR' && !branches.some(branch => branch.id === lane.branch_id))
  if (other.length > 0) groups.push({ id: 'other', name: 'Other chairs', hint: 'Outside the selected branch', lanes: other })
  return groups
}

/** Upcoming appointments that still need to run, soonest first. */
export function upcomingBlocks(blocks: BoardBlock[], nowMinutes: number, timezone: string, limit = 3): BoardBlock[] {
  return blocks
    .filter(block => block.status !== 'CANCELLED' && block.status !== 'COMPLETED')
    .map(block => ({ block, at: clinicMinutes(block.starts_at, timezone) }))
    .filter(entry => entry.at >= nowMinutes)
    .sort((a, b) => a.at - b.at)
    .slice(0, limit)
    .map(entry => entry.block)
}

/** Appointment type colour when set, otherwise a stable colour derived from the visit name. */
export function blockColor(block: BoardBlock): string {
  const declared = (block.type_color ?? '').trim()
  if (/^#[0-9a-f]{3,8}$/i.test(declared)) return declared
  let hash = 0
  for (const character of block.activity) hash = (hash * 31 + character.charCodeAt(0)) >>> 0
  return FALLBACK_COLORS[hash % FALLBACK_COLORS.length]
}

/** Axis tick marks in minutes for the shared time axis. */
export function axisTicks(window: DayWindow, stepMinutes = 60): number[] {
  const ticks: number[] = []
  for (let minute = window.start; minute <= window.end; minute += stepMinutes) ticks.push(minute)
  return ticks
}

/** Screen reader description for a block. */
export function blockLabel(block: BoardBlock, timezone: string): string {
  const from = formatClock(clinicMinutes(block.starts_at, timezone))
  const until = formatClock(clinicMinutes(block.ends_at, timezone))
  const chair = block.chair_name ? `, ${block.chair_name}` : ''
  return `${from} to ${until}, ${block.activity}, ${block.patient_name}, ${block.dentist_name}${chair}, ${STATUS_LABELS[statusTone(block.status)]}`
}
