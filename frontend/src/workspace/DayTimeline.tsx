import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import { NavLink } from 'react-router-dom'
import { api } from '../api/client'
import { describeError } from './toast'
import {
  axisTicks, blockColor, blockLabel, buildLanes, clinicDateKey, clinicMinutes, dayWindow, formatClock,
  groupLanes, STATUS_LABELS, statusTone, upcomingBlocks,
  type BoardBlock, type BoardPayload, type LaneKind,
} from './dayBoard'

const POLL_MS = 60_000

function dateLabel(date: string): string {
  const parsed = new Date(`${date}T12:00:00`)
  if (Number.isNaN(parsed.getTime())) return date
  return new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(parsed)
}

function timeLabel(iso: string, timezone: string): string {
  return formatClock(clinicMinutes(iso, timezone))
}

/** Screen reader friendly list of the same appointments the timeline draws. */
function BoardTable({ rows, timezone }: { rows: BoardBlock[]; timezone: string }) {
  if (rows.length === 0) return <p className="ws-dayboard-empty">No appointments are scheduled for this clinic date.</p>
  return <div className="ws-scroll"><table className="ws-table"><thead><tr><th>Time</th><th>Patient</th><th>Visit</th><th>Provider / Chair</th><th>Status</th></tr></thead><tbody>
    {rows.map(row => <tr key={row.id}>
      <td><b>{timeLabel(row.starts_at, timezone)} – {timeLabel(row.ends_at, timezone)}</b></td>
      <td>{row.patient_name}</td>
      <td>{row.activity}</td>
      <td>{row.dentist_name}<small>{row.chair_name || 'No chair'}</small></td>
      <td><span className="ws-status">{STATUS_LABELS[statusTone(row.status)]}</span></td>
    </tr>)}
  </tbody></table></div>
}

/**
 * Whole-day resource timeline. Used on the command center for today, and inside the calendar
 * workspace for any date (where the branch comes from the workspace header and the date and
 * dentist/chair filters come from the scheduling filters row).
 * Lanes are chairs (the scarce physical resource) or providers, grouped by branch, with every
 * appointment placed on a shared clinic-local time axis. The board is read-only: blocks drill
 * through to the calendar, where the appointment itself is managed.
 */
export function DayTimeline({ date, branchId: lockedBranch, filterDentist = '', filterChair = '' }: {
  date?: string
  branchId?: string | null
  filterDentist?: string
  filterChair?: string
} = {}) {
  const [payload, setPayload] = useState<BoardPayload | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [laneKind, setLaneKind] = useState<LaneKind>('CHAIR')
  const [branchChoice, setBranchChoice] = useState('all')
  const [view, setView] = useState<'timeline' | 'list'>('timeline')
  const [now, setNow] = useState(() => new Date())
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)

  const lockedBranchId = lockedBranch != null && lockedBranch !== '' ? lockedBranch : null
  const load = useCallback(() => {
    const params = new URLSearchParams()
    if (date) params.set('date', date)
    const branch = lockedBranchId ?? (branchChoice === 'all' ? null : branchChoice)
    if (branch) params.set('branchId', branch)
    const query = params.toString()
    return api<BoardPayload>(`/api/workspace/day-board${query ? `?${query}` : ''}`)
      .then(result => { setPayload(result); setError(''); setUpdatedAt(new Date()) })
      .catch(e => setError(describeError(e, 'Could not load the clinic day board')))
      .finally(() => setLoading(false))
  }, [date, lockedBranchId, branchChoice])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const ticker = window.setInterval(() => setNow(new Date()), 30_000)
    const poller = window.setInterval(() => { void load() }, POLL_MS)
    const onVisible = () => { if (document.visibilityState === 'visible') void load() }
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('clinic-seed-data', load)
    return () => {
      window.clearInterval(ticker); window.clearInterval(poller)
      document.removeEventListener('visibilitychange', onVisible); window.removeEventListener('clinic-seed-data', load)
    }
  }, [load])

  const timezone = payload?.timezone ?? 'UTC'
  const branches = useMemo(() => payload?.branches ?? [], [payload])
  const blocks = useMemo(() => payload?.blocks ?? [], [payload])
  // Dentist/chair filters mirror the calendar's semantics: dentist by display name, chair by id.
  // The axis stays anchored on every appointment of the day, only the drawn blocks are filtered.
  const filteredBlocks = useMemo(() => blocks.filter(block =>
    (filterDentist === '' || block.dentist_name === filterDentist) &&
    (filterChair === '' || block.chair_id === filterChair)), [blocks, filterDentist, filterChair])
  const lanes = useMemo(() => {
    const all = (payload?.lanes ?? []).filter(lane => lane.kind === laneKind)
    return filterChair && laneKind === 'CHAIR' ? all.filter(lane => lane.id === filterChair) : all
  }, [payload, laneKind, filterChair])
  const groups = useMemo(() => groupLanes(lanes, branches), [lanes, branches])
  const window_ = useMemo(() => dayWindow(branches, blocks, timezone), [branches, blocks, timezone])
  const models = useMemo(() => buildLanes(lanes, filteredBlocks, window_, timezone), [lanes, filteredBlocks, window_, timezone])
  const ticks = useMemo(() => axisTicks(window_), [window_])
  const span = Math.max(1, window_.end - window_.start)
  const nowMinutes = clinicMinutes(now.toISOString(), timezone)
  // A "now" line only makes sense on the clinic's own current date; past and future days are
  // shown without it, their upcoming list anchored at the start of the day instead.
  const boardDate = payload?.date ?? null
  const todayDate = clinicDateKey(now.toISOString(), timezone)
  const upcomingFrom = boardDate === null ? nowMinutes : boardDate === todayDate ? nowMinutes : boardDate > todayDate ? -1 : Number.MAX_SAFE_INTEGER
  const upcoming = useMemo(() => upcomingBlocks(filteredBlocks, upcomingFrom, timezone, 3), [filteredBlocks, upcomingFrom, timezone])
  const showNow = boardDate === todayDate && nowMinutes >= window_.start && nowMinutes <= window_.end
  const minutesAgo = updatedAt ? Math.max(0, Math.round((now.getTime() - updatedAt.getTime()) / 60000)) : null
  const filtersActive = filterDentist !== '' || filterChair !== ''

  const percentOf = (minute: number) => ((minute - window_.start) / span) * 100

  return <section className="ws-panel ws-dayboard" aria-label="Clinic day board">
    <header className="ws-dayboard-head">
      <div className="ws-dayboard-heading">
        <span className="ws-eyebrow">Day board</span>
        <h3>Clinic day timeline</h3>
        <p>{payload ? `${dateLabel(payload.date)} · ${filtersActive ? `${filteredBlocks.length} of ${blocks.length} appointment${blocks.length === 1 ? '' : 's'}` : `${blocks.length} appointment${blocks.length === 1 ? '' : 's'}`}` : 'Loading the clinic day…'}</p>
      </div>
      <div className="ws-dayboard-controls">
        <div className="ws-segmented" role="group" aria-label="Lane view">
          <button type="button" className={laneKind === 'CHAIR' ? 'is-active' : ''} aria-pressed={laneKind === 'CHAIR'} onClick={() => setLaneKind('CHAIR')}>Chairs</button>
          <button type="button" className={laneKind === 'DENTIST' ? 'is-active' : ''} aria-pressed={laneKind === 'DENTIST'} onClick={() => setLaneKind('DENTIST')}>Providers</button>
        </div>
        <button type="button" className="ws-icon-button ws-dayboard-refresh" onClick={() => { void load() }} aria-label="Refresh the day board">↻</button>
      </div>
    </header>

    {error && <p role="alert" className="ws-global-warning">{error}</p>}

    {upcoming.length > 0 && <div className="ws-dayboard-next">
      <span className="ws-dayboard-next-label">Up next</span>
      <ul>
        {upcoming.map(block => <li key={block.id}>
          <b>{timeLabel(block.starts_at, timezone)}</b>
          <span>{block.patient_name}<small>{block.activity} · {block.dentist_name}</small></span>
        </li>)}
      </ul>
    </div>}

    <div className="ws-dayboard-filters">
      {!lockedBranchId
        ? <div className="ws-chips" role="group" aria-label="Branch filter">
          <button type="button" className={branchChoice === 'all' ? 'is-active' : ''} aria-pressed={branchChoice === 'all'} onClick={() => setBranchChoice('all')}>All branches</button>
          {branches.map(branch => <button key={branch.id} type="button" className={branchChoice === branch.id ? 'is-active' : ''} aria-pressed={branchChoice === branch.id} onClick={() => setBranchChoice(branch.id)}>{branch.name}</button>)}
        </div>
        : <div className="ws-chips ws-dayboard-branch-lock" role="group" aria-label="Branch filter">
          {branches.map(branch => <span key={branch.id} className="ws-chips-lock">{branch.name}</span>)}
        </div>}
      <div className="ws-segmented" role="group" aria-label="Board view">
        <button type="button" className={view === 'timeline' ? 'is-active' : ''} aria-pressed={view === 'timeline'} onClick={() => setView('timeline')}>Timeline</button>
        <button type="button" className={view === 'list' ? 'is-active' : ''} aria-pressed={view === 'list'} onClick={() => setView('list')}>List</button>
      </div>
    </div>

    {loading && <p className="ws-dayboard-empty">Loading the clinic day…</p>}

    {!loading && view === 'list' && <BoardTable rows={filteredBlocks} timezone={timezone} />}

    {!loading && view === 'timeline' && (filteredBlocks.length === 0
      ? <p className="ws-dayboard-empty">{blocks.length === 0 ? 'No appointments are scheduled for this clinic date.' : 'No appointments match the current filters.'}</p>
      : <div className="ws-dayboard-scroll">
        <div className="ws-dayboard-grid">
          <div className="ws-dayboard-axis" aria-hidden="true">
            <span className="ws-dayboard-axis-label" />
            <span className="ws-dayboard-axis-track">{ticks.map(tick => <i key={tick} style={{ left: `${percentOf(tick)}%` }}>{formatClock(tick)}</i>)}</span>
          </div>
          {groups.map(group => <section className="ws-dayboard-group" key={group.id}>
            <header className="ws-dayboard-group-head"><strong>{group.name}</strong><span>{group.hint}</span></header>
            {models.filter(model => group.lanes.some(lane => lane.id === model.lane.id)).map(model => <div className="ws-dayboard-lane" key={model.lane.id}>
              <div className="ws-dayboard-lane-label">
                <strong>{model.lane.name}</strong>
                <span>{model.utilization}% booked</span>
              </div>
              <div className="ws-dayboard-track">
                {showNow && <i className="ws-dayboard-now" style={{ left: `${percentOf(nowMinutes)}%` }} aria-hidden="true"><span>now</span></i>}
                {model.positioned.map(position => {
                  const tone = statusTone(position.block.status)
                  const colour = blockColor(position.block)
                  const narrow = position.width < 9
                  const tiny = position.width < 5
                  return <NavLink
                    key={position.block.id}
                    to={`/workspace/calendar?appointment=${encodeURIComponent(position.block.id)}`}
                    className={`ws-dayboard-block${position.loose ? ' is-loose' : ''}${narrow ? ' is-narrow' : ''}${tiny ? ' is-tiny' : ''}`}
                    data-status={tone}
                    style={{ left: `${position.left}%`, width: `${position.width}%`, '--ws-block-colour': colour } as CSSProperties}
                    aria-label={blockLabel(position.block, timezone)}
                  >
                    <span className="ws-dayboard-block-time">{timeLabel(position.block.starts_at, timezone)}</span>
                    <span className="ws-dayboard-block-patient">{position.block.patient_name}</span>
                    <span className="ws-dayboard-block-activity">{position.block.activity}</span>
                    {position.block.dentist_color && <i className="ws-dayboard-block-dentist" style={{ background: position.block.dentist_color }} aria-hidden="true" />}
                  </NavLink>
                })}
              </div>
            </div>)}
          </section>)}
        </div>
        <div className="ws-sr-only">
          {/* The clip wrapper matters: a bare table ignores width:1px and would widen the page. */}
          <table role="presentation">
            <caption>Appointments for {payload ? dateLabel(payload.date) : 'today'}</caption>
            <thead><tr><th>Time</th><th>Patient</th><th>Visit</th><th>Provider</th><th>Chair</th><th>Status</th></tr></thead>
            <tbody>{blocks.map(row => <tr key={row.id}>
              <td>{timeLabel(row.starts_at, timezone)} – {timeLabel(row.ends_at, timezone)}</td>
              <td>{row.patient_name}</td><td>{row.activity}</td><td>{row.dentist_name}</td><td>{row.chair_name}</td><td>{STATUS_LABELS[statusTone(row.status)]}</td>
            </tr>)}</tbody>
          </table>
        </div>
      </div>)}

    {!loading && updatedAt && <footer className="ws-dayboard-foot">
      <span>Updated {minutesAgo === 0 ? 'just now' : `${minutesAgo} min ago`} · {timezone.replaceAll('_', ' ')}</span>
      <NavLink to="/workspace/calendar">Open the calendar →</NavLink>
    </footer>}
  </section>
}
