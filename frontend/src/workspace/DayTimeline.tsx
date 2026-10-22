import { useCallback, useEffect, useMemo, useState } from 'react'
import type { CSSProperties } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { api } from '../api/client'
import { describeError } from './toast'
import { nextActions } from './scheduling'
import {
  axisTicks, blockColor, blockLabel, buildLanes, clinicDateKey, clinicMinutes, dayWindow, formatClock,
  groupLanes, STATUS_LABELS, statusColor, statusTone, upcomingBlocks,
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
      <td><span className={`ws-status ws-dayboard-list-status is-${statusTone(row.status)}`}><i aria-hidden="true"/>{STATUS_LABELS[statusTone(row.status)]}</span></td>
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
export function DayTimeline({ date, branchId: lockedBranch, filterDentist = '', filterChair = '', expanded: expandedProp, onExpandedChange, expandedControls }: {
  date?: string
  branchId?: string | null
  filterDentist?: string
  filterChair?: string
  expanded?: boolean
  onExpandedChange?: (expanded: boolean) => void
  expandedControls?: ReactNode
} = {}) {
  const [payload, setPayload] = useState<BoardPayload | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [laneKind, setLaneKind] = useState<LaneKind>('CHAIR')
  const [branchChoice, setBranchChoice] = useState('all')
  const [view, setView] = useState<'timeline' | 'list'>('timeline')
  const [filtersModalOpen, setFiltersModalOpen] = useState(false)
  const [localExpanded, setLocalExpanded] = useState(false)
  const expanded = expandedProp ?? localExpanded
  const setExpanded = (value: boolean) => {
    setLocalExpanded(value)
    onExpandedChange?.(value)
    if (!value) setFiltersModalOpen(false)
  }
  const [now, setNow] = useState(() => new Date())
  const [selectedBlock, setSelectedBlock] = useState<BoardBlock | null>(null)
  const [hoveredBlock, setHoveredBlock] = useState<{ block: BoardBlock; rect: DOMRect } | null>(null)
  const [actionBusy, setActionBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [cancelPrompt, setCancelPrompt] = useState(false)
  const [cancelReason, setCancelReason] = useState('')

  const lockedBranchId = lockedBranch != null && lockedBranch !== '' ? lockedBranch : null
  const load = useCallback(() => {
    const params = new URLSearchParams()
    if (date) params.set('date', date)
    const branch = lockedBranchId ?? (branchChoice === 'all' ? null : branchChoice)
    if (branch) params.set('branchId', branch)
    const query = params.toString()
    return api<BoardPayload>(`/api/workspace/day-board${query ? `?${query}` : ''}`)
      .then(result => { setPayload(result); setError('') })
      .catch(e => setError(describeError(e, 'Could not load the clinic day board')))
      .finally(() => setLoading(false))
  }, [date, lockedBranchId, branchChoice])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!expanded) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setExpanded(false)
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [expanded])

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
  const filtersActive = filterDentist !== '' || filterChair !== ''
  const futureSelectedAppointment=selectedBlock!==null&&clinicDateKey(selectedBlock.starts_at,timezone)>todayDate

  const applyAppointmentAction=async(status:string,reason?:string)=>{
    if(!selectedBlock)return
    setActionBusy(true);setActionError('')
    try{
      await api(`/api/scheduling/appointments/${selectedBlock.id}/status`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({status,reason:reason?.trim()||null})})
      setSelectedBlock(current=>current?.id===selectedBlock.id?{...current,status,reason:reason?.trim()||current.reason}:current)
      setCancelPrompt(false);setCancelReason('')
      await load()
    }catch(e){setActionError(describeError(e,'Could not update the appointment'))}
    finally{setActionBusy(false)}
  }

  const percentOf = (minute: number) => ((minute - window_.start) / span) * 100

  return <><section className={`ws-panel ws-dayboard${expanded ? ' is-expanded' : ''}`} aria-label="Clinic day board">
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
        {expanded&&<button type="button" className="ws-icon-button ws-dayboard-toolbar-toggle" onClick={()=>setFiltersModalOpen(true)} aria-label="Open day board filters" title="Filters"><svg viewBox="0 0 20 20" aria-hidden="true" fill="none"><path d="M3 5h14M5.5 10h9M8 15h4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg></button>}
        <button type="button" className="ws-icon-button ws-dayboard-expand" onClick={() => setExpanded(!expanded)} aria-label={expanded ? 'Exit full screen day board' : 'View day board full screen'} title={expanded ? 'Exit full screen' : 'Full screen'}>
          {expanded
            ? <svg viewBox="0 0 20 20" aria-hidden="true" fill="none"><path d="M7 3v4H3m10-4v4h4M3 13h4v4m10-4h-4v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
            : <svg viewBox="0 0 20 20" aria-hidden="true" fill="none"><path d="M3 7V3h4m6 0h4v4M17 13v4h-4m-6 0H3v-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>}
        </button>
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
              <div className="ws-dayboard-track" onMouseLeave={()=>setHoveredBlock(null)}>
                {showNow && <i className="ws-dayboard-now" style={{ left: `${percentOf(nowMinutes)}%` }} aria-hidden="true"><span>now</span></i>}
                {model.positioned.map(position => {
                  const tone = statusTone(position.block.status)
                  const colour = blockColor(position.block)
                  const narrow = position.width < 9
                  const tiny = position.width < 5
                  return <button
                    key={position.block.id}
                    type="button"
                    className={`ws-dayboard-block${position.loose ? ' is-loose' : ''}${narrow ? ' is-narrow' : ''}${tiny ? ' is-tiny' : ''}`}
                    data-status={tone}
                    style={{ left: `${position.left}%`, width: `${position.width}%`, '--ws-block-colour': colour } as CSSProperties}
                    aria-label={blockLabel(position.block, timezone)}
                    aria-haspopup="dialog"
                    aria-describedby={hoveredBlock?.block.id===position.block.id?`dayboard-hover-${position.block.id}`:undefined}
                    onMouseEnter={event=>setHoveredBlock({block:position.block,rect:event.currentTarget.getBoundingClientRect()})}
                    onFocus={event=>setHoveredBlock({block:position.block,rect:event.currentTarget.getBoundingClientRect()})}
                    onBlur={()=>setHoveredBlock(current=>current?.block.id===position.block.id?null:current)}
                    onClick={()=>{setHoveredBlock(null);setSelectedBlock(position.block)}}
                  >
                    <span className="ws-dayboard-block-time">{timeLabel(position.block.starts_at, timezone)}</span>
                    <span className="ws-dayboard-block-patient">{position.block.patient_name}</span>
                    <span className="ws-dayboard-block-activity">{position.block.activity}</span>
                    {position.block.dentist_color && <i className="ws-dayboard-block-dentist" style={{ background: position.block.dentist_color }} aria-hidden="true" />}
                  </button>
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

    {selectedBlock&&<div className="ws-modal-backdrop ws-dayboard-modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget){setSelectedBlock(null);setCancelPrompt(false)}}}><section className="ws-modal ws-dayboard-detail-modal" role="dialog" aria-modal="true" aria-labelledby="dayboard-appointment-title" onMouseDown={event=>event.stopPropagation()} onKeyDown={event=>{if(event.key==='Escape'){setSelectedBlock(null);setCancelPrompt(false)}}}><header className="ws-modal-header"><div><span className="ws-eyebrow">Appointment details</span><h2 id="dayboard-appointment-title">{selectedBlock.patient_name}</h2><p>{selectedBlock.activity}</p></div><button type="button" className="ws-icon-button" aria-label="Close appointment details" onClick={()=>{setSelectedBlock(null);setCancelPrompt(false)}}>×</button></header><div className="ws-dayboard-detail-status"><i style={{background:statusColor(statusTone(selectedBlock.status))}} aria-hidden="true"/><span>{STATUS_LABELS[statusTone(selectedBlock.status)]}</span></div><dl className="ws-dayboard-detail-grid"><div><dt>Date</dt><dd>{dateLabel(clinicDateKey(selectedBlock.starts_at,timezone))}</dd></div><div><dt>Time</dt><dd>{timeLabel(selectedBlock.starts_at,timezone)} – {timeLabel(selectedBlock.ends_at,timezone)}</dd></div><div><dt>Dentist</dt><dd>{selectedBlock.dentist_name}</dd></div><div><dt>Chair</dt><dd>{selectedBlock.chair_name||'Unassigned'}</dd></div>{selectedBlock.reason&&<div className="ws-dayboard-detail-reason"><dt>Reason</dt><dd>{selectedBlock.reason}</dd></div>}</dl>{actionError&&<p role="alert" className="ws-dayboard-action-error">{actionError}</p>}{cancelPrompt?<form className="ws-dayboard-cancel-form" onSubmit={event=>{event.preventDefault();void applyAppointmentAction('CANCELLED',cancelReason)}}><label>Cancellation reason (optional)<input maxLength={500} value={cancelReason} onChange={event=>setCancelReason(event.target.value)} placeholder="e.g. Patient requested cancellation"/></label><div><button type="button" className="ws-care-secondary" disabled={actionBusy} onClick={()=>{setCancelPrompt(false);setCancelReason('')}}>Keep appointment</button><button type="submit" className="ws-care-danger" disabled={actionBusy}>{actionBusy?'Cancelling…':'Confirm cancellation'}</button></div></form>:nextActions(selectedBlock.status).length>0&&<div className="ws-dayboard-modal-actions" aria-label="Appointment actions">{nextActions(selectedBlock.status).map(action=>{const disabledByDate=futureSelectedAppointment&&['ARRIVED','NO_SHOW'].includes(action.status);return <button key={action.status} type="button" className={action.status==='CANCELLED'?'ws-care-danger':'ws-care-secondary'} disabled={actionBusy||disabledByDate} title={disabledByDate?'This action is available on the appointment date':undefined} onClick={()=>{if(action.reason==='prompt'){setCancelPrompt(true);setActionError('')}else void applyAppointmentAction(action.status)}}><svg className="ws-action-icon" viewBox="0 0 20 20" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{action.status==='CONFIRMED'?<path d="m4 10 4 4 8-8"/>:action.status==='ARRIVED'?<><circle cx="10" cy="6" r="3"/><path d="M4 17a6 6 0 0 1 12 0m1-8v5m-2.5-2.5h5"/></>:action.status==='NO_SHOW'?<><circle cx="10" cy="10" r="7"/><path d="m7.5 7.5 5 5m0-5-5 5"/></>:action.status==='CANCELLED'?<path d="m5 5 10 10M15 5 5 15"/>:action.status==='IN_CHAIR'?<path d="M5 12h14v3H5zm2 3v3m10-3v3M7 12V7a2 2 0 0 1 2-2h6"/>:<path d="m4 10 4 4 8-8"/>}</svg>{actionBusy?'Updating…':action.label}</button>})}</div>}</section></div>}

    <div className="ws-dayboard-legend" role="list" aria-label="Appointment status legend">
      {(Object.entries(STATUS_LABELS) as [keyof typeof STATUS_LABELS, string][]).map(([tone, label])=><span role="listitem" key={tone}><i style={{background:statusColor(tone)}} aria-hidden="true"/>{label}</span>)}
    </div>

    {expanded&&filtersModalOpen&&<div className="ws-modal-backdrop ws-dayboard-filters-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setFiltersModalOpen(false)}}><section className="ws-modal ws-dayboard-filters-modal" role="dialog" aria-modal="true" aria-labelledby="dayboard-filters-title" onMouseDown={event=>event.stopPropagation()} onKeyDown={event=>{if(event.key==='Escape')setFiltersModalOpen(false)}}><header className="ws-modal-header"><div><span className="ws-eyebrow">Day board</span><h2 id="dayboard-filters-title">Filters</h2></div><button type="button" className="ws-icon-button" aria-label="Close filters" onClick={()=>setFiltersModalOpen(false)}>×</button></header>{expandedControls}</section></div>}
  </section>{hoveredBlock&&createPortal(<div id={`dayboard-hover-${hoveredBlock.block.id}`} className="ws-dayboard-hover-card is-layered" role="tooltip" style={{left:`${Math.max(12,Math.min(hoveredBlock.rect.left,window.innerWidth-292))}px`,top:`${hoveredBlock.rect.top>220?hoveredBlock.rect.top-10:hoveredBlock.rect.bottom+10}px`,transform:hoveredBlock.rect.top>220?'translateY(-100%)':undefined}}><div className="ws-dayboard-hover-head"><strong>{hoveredBlock.block.patient_name}</strong><span><i style={{background:statusColor(statusTone(hoveredBlock.block.status))}}/>{STATUS_LABELS[statusTone(hoveredBlock.block.status)]}</span></div><p>{hoveredBlock.block.activity}</p><dl><div><dt>Time</dt><dd>{timeLabel(hoveredBlock.block.starts_at,timezone)} – {timeLabel(hoveredBlock.block.ends_at,timezone)}</dd></div><div><dt>Dentist</dt><dd>{hoveredBlock.block.dentist_name}</dd></div><div><dt>Chair</dt><dd>{hoveredBlock.block.chair_name||'Unassigned'}</dd></div></dl></div>,document.body)}</>
}
