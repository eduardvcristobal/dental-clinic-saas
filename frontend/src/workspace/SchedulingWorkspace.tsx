import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import { calendarRange, filterEvents, nextActions } from './scheduling'
import type { WorkspaceRole } from './catalog'

type Branch = { id: string; name: string }
type Resource = { id: string; name?: string; full_name?: string }
type Type = { id: string; name: string; default_minutes: number; active: boolean }
export type Event = { id: string; patient_id: string; first_name: string; last_name: string; dentist_name: string; chair_id: string | null; starts_at: string; ends_at: string; status: string; reason: string | null; version: number }
type Patient = { id: string; first_name: string; last_name: string; chart_no: string }
type Queue = { id: string; first_name: string; last_name: string; starts_at: string; status: string }
type AppointmentEvent = { id: string; event_type: string; old_status: string | null; new_status: string | null; old_starts_at: string | null; new_starts_at: string | null; reason: string | null; created_at: string }
const json = (data: unknown) => ({ headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) })
const today = () => new Date().toISOString().slice(0, 10)
const endpoint = '/api/scheduling'

/** Synthetic-data only until production security approval. No clinical data is fabricated. */
export function SchedulingWorkspace({ role, mode = 'calendar' }: { role: WorkspaceRole; mode?: 'calendar' | 'check-in' }) {
  const [branches, setBranches] = useState<Branch[]>([])
  const [branch, setBranch] = useState('')
  const [date, setDate] = useState(today())
  const [view, setView] = useState<'Day' | 'Week' | 'Month'>('Day')
  const [events, setEvents] = useState<Event[]>([])
  const [calendarLoading, setCalendarLoading] = useState(false)
  const [queue, setQueue] = useState<Queue[]>([])
  const [dentists, setDentists] = useState<Resource[]>([])
  const [chairs, setChairs] = useState<Resource[]>([])
  const [types, setTypes] = useState<Type[]>([])
  const [patients, setPatients] = useState<Patient[]>([])
  const [search, setSearch] = useState('')
  const [patientId, setPatientId] = useState('')
  const [dentistId, setDentistId] = useState('')
  const [chairId, setChairId] = useState('')
  const [typeId, setTypeId] = useState('')
  const [starts, setStarts] = useState('')
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [typeName, setTypeName] = useState('')
  const [typeMinutes, setTypeMinutes] = useState(30)
  const [availabilityDay, setAvailabilityDay] = useState(1)
  const [availabilityStart, setAvailabilityStart] = useState('09:00')
  const [availabilityEnd, setAvailabilityEnd] = useState('17:00')
  const [filterDentist, setFilterDentist] = useState('')
  const [filterChair, setFilterChair] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [selected, setSelected] = useState<Event | null>(null)
  const [history, setHistory] = useState<AppointmentEvent[]>([])
  const [rescheduleStart, setRescheduleStart] = useState('')
  const [cancelReason, setCancelReason] = useState('')
  const manager = role === 'OWNER' || role === 'ADMIN'
  useEffect(() => { let active = true; void api<Branch[]>('/api/clinic-management/branches').then(rows => { if (active) { setBranches(rows); setBranch(rows[0]?.id ?? '') } }).catch(e => { if (active) setNotice(String(e)) }); return () => { active = false } }, [])
  useEffect(() => { if (!branch) return; let active = true
    void Promise.all([api<{ dentists: Resource[]; chairs: Resource[] }>(`${endpoint}/resources?branchId=${branch}`), api<Type[]>(`${endpoint}/types`)]).then(([r, t]) => { if (active) { setDentists(Array.isArray(r?.dentists) ? r.dentists : []); setChairs(Array.isArray(r?.chairs) ? r.chairs : []); setTypes(Array.isArray(t) ? t : []); if (!Array.isArray(r?.dentists) || !Array.isArray(r?.chairs)) setNotice('Scheduling resource response is unavailable or invalid') } }).catch(e => { if (active) setNotice(String(e)) }); return () => { active = false }
  }, [branch, refresh])
  useEffect(() => { if (!branch) return; let active = true
    const timer = setTimeout(() => {
      if (!active) return
      setCalendarLoading(true)
      const { from, to } = calendarRange(date, view)
      void Promise.all([api<Event[]>(`${endpoint}/calendar?branchId=${branch}&from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`), api<Queue[]>(`${endpoint}/queue?branchId=${branch}`)]).then(([list, q]) => { if (active) { setEvents(list); setQueue(q); setCalendarLoading(false) } }).catch(e => { if (active) { setNotice(String(e)); setCalendarLoading(false) } })
    }, 0)
    return () => { active = false; clearTimeout(timer) }
  }, [branch, date, view, refresh])
  useEffect(() => { if (search.trim().length < 2) return; let active = true; void api<Patient[]>(`/api/patients?q=${encodeURIComponent(search)}&limit=15`).then(r => { if (active) setPatients(r) }).catch(e => { if (active) setNotice(String(e)) }); return () => { active = false } }, [search])
  useEffect(() => {
    if (!selectedId) return
    let active = true
    void api<Event>(`${endpoint}/appointments/${selectedId}`).then(ap => { if (active) { setSelected(ap); setHistory([]) } }).catch(e => { if (active) { setSelected(null); setNotice(String(e)) } })
    void api<AppointmentEvent[]>(`${endpoint}/appointments/${selectedId}/events`).then(rows => { if (active) setHistory(rows) }).catch(() => { })
    return () => { active = false }
  }, [selectedId, refresh])
  const visible = useMemo(() => filterEvents(events, filterDentist, filterChair), [events, filterDentist, filterChair])
  async function change(action: () => Promise<unknown>, success: string) { setBusy(true); setNotice(''); try { await action(); setNotice(success); setRefresh(n => n + 1) } catch (e) { setNotice(String(e)) } finally { setBusy(false) } }
  async function book() {
    const start = new Date(starts); if (Number.isNaN(start.getTime())) { setNotice('Choose a valid start time'); return }
    const duration = types.find(t => t.id === typeId)?.default_minutes ?? 30
    const end = new Date(start.getTime() + duration * 60000)
    await change(() => api(`${endpoint}/appointments`, { method: 'POST', ...json({ patientId, branchId: branch, dentistId, chairId: chairId || null, appointmentTypeId: typeId || null, startsAt: start.toISOString(), endsAt: end.toISOString(), reason }) }), 'Appointment created')
  }
  async function reschedule() {
    if (!selected) return
    const start = new Date(rescheduleStart)
    if (Number.isNaN(start.getTime())) { setNotice('Choose a valid new start time'); return }
    const durationMinutes = Math.max(5, Math.round((new Date(selected.ends_at).getTime() - new Date(selected.starts_at).getTime()) / 60000))
    const end = new Date(start.getTime() + durationMinutes * 60000)
    await change(() => api(`${endpoint}/appointments/${selected.id}/reschedule`, { method: 'POST', ...json({ startsAt: start.toISOString(), endsAt: end.toISOString(), reason: cancelReason.trim() ? cancelReason : 'Rescheduled by staff' }) }), 'Appointment rescheduled')
    setRescheduleStart(''); setCancelReason('')
  }
  async function cancel() {
    if (!selected) return
    await change(() => api(`${endpoint}/appointments/${selected.id}/status`, { method: 'POST', ...json({ status: 'CANCELLED', reason: cancelReason.trim() || 'Cancelled without recorded reason' }) }), 'Appointment cancelled')
    setCancelReason('')
  }
  async function transition(id: string, status: string, why?: string) { await change(() => api(`${endpoint}/appointments/${id}/status`, { method: 'POST', ...json({ status, reason: why ?? null }) }), `Appointment: ${status}`) }
  const format = (s: string) => new Date(s).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
  return <div className="ws-management">
    <div className="ws-heading"><div><span className="ws-eyebrow">Workspace / Scheduling</span><h1>{mode === 'check-in' ? 'Patient arrival & queue' : 'Appointment Calendar'}</h1><p>Live clinic-scoped scheduling · Synthetic patients only until production approval</p></div></div>
    <div aria-live="polite">{notice && <div role="status" className="ws-panel" style={{ padding: 12 }}>{notice}</div>}</div>
    <div className="ws-panel" style={{ padding: 16 }}><label>Branch <select value={branch} onChange={e => setBranch(e.target.value)}>{branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></label>
      {mode === 'calendar' && <><label>Date <input type="date" value={date} onChange={e => setDate(e.target.value)} /></label> <div className="ws-toggle">{(['Day', 'Week', 'Month'] as const).map(v => <button key={v} className={v === view ? 'active' : ''} onClick={() => setView(v)}>{v}</button>)}</div>
      <label>Dentist filter <select value={filterDentist} onChange={e => setFilterDentist(e.target.value)}><option value="">All dentists</option>{dentists.map(d => <option key={d.id} value={d.full_name ?? ''}>{d.full_name}</option>)}</select></label>
      <label>Chair filter <select value={filterChair} onChange={e => setFilterChair(e.target.value)}><option value="">All chairs</option>{chairs.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label></>}
    </div>
    {mode === 'check-in' && <section className="ws-panel" style={{ padding: 16 }}><h3>Today's waiting queue</h3>{calendarLoading ? <p>Loading queue…</p> : queue.length === 0 ? <p>No checked-in patients.</p> : queue.map(q => <div className="ws-management-row" key={q.id}><b>{q.first_name} {q.last_name}</b><span>{format(q.starts_at)} · {q.status}</span>{q.status === 'ARRIVED' && <button disabled={busy} onClick={() => void transition(q.id, 'IN_CHAIR')}>Seat patient</button>}{q.status === 'IN_CHAIR' && <button disabled={busy} onClick={() => void transition(q.id, 'COMPLETED')}>Complete</button>}</div>)}</section>}
    {mode === 'calendar' && <>
      <section className="ws-panel" style={{ padding: 16 }} aria-busy={calendarLoading}><h3>Appointments</h3>{calendarLoading ? <p>Loading appointments…</p> : visible.length === 0 ? <p>No appointments in selected range.</p> : <div className="ws-scroll"><table className="ws-table"><thead><tr><th>Start</th><th>Patient</th><th>Dentist</th><th>Status / Actions</th></tr></thead><tbody>{visible.map(e => <tr key={e.id} data-testid="appointment-row"><td>{format(e.starts_at)}</td><td>{e.first_name} {e.last_name}</td><td>{e.dentist_name}</td><td>{e.status}{nextActions(e.status).map(a => <span key={a.status} style={{ marginRight: 6 }}><button disabled={busy} onClick={() => { if (a.reason === 'prompt') { setSelectedId(e.id) } else void transition(e.id, a.status) }}>{a.label}</button></span>)}</td></tr>)}</tbody></table></div>}</section>
      {selected && <section className="ws-panel" style={{ padding: 16 }} data-testid="appointment-details"><h3>Appointment details · {selected.first_name} {selected.last_name}</h3><p>{format(selected.starts_at)} → {format(selected.ends_at)} · {selected.dentist_name}{selected.status} · version {selected.version}</p>{selected.reason && <p>Reason: {selected.reason}</p>}
        <h4>Lifecycle history</h4>{history.length === 0 ? <p>No events recorded.</p> : <ul>{history.map(h => <li key={h.id}>{h.event_type}{h.old_status && ` ${h.old_status} → `}{h.new_status ?? ''}{h.reason ? ` · ${h.reason}` : ''}</li>)}</ul>}
        <h4>Reschedule</h4><form onSubmit={e => { e.preventDefault(); void reschedule() }}><label>New start <input type="datetime-local" required value={rescheduleStart} onChange={e => setRescheduleStart(e.target.value)} /></label><button disabled={busy}>Reschedule appointment</button></form>
        <h4>Cancel (optional reason)</h4><form onSubmit={e => { e.preventDefault(); void cancel() }}><label>Reason <input maxLength={500} value={cancelReason} onChange={e => setCancelReason(e.target.value)} placeholder="Optional cancellation reason" /></label><button disabled={busy}>Cancel appointment</button></form>
        <button type="button" onClick={() => { setSelectedId(''); setSelected(null); setHistory([]) }}>Close details</button>
      </section>}
      <section className="ws-panel" style={{ padding: 16 }}><h3>Book appointment</h3><form onSubmit={e => { e.preventDefault(); void book() }}><label>Find patient <input placeholder="Search by name or chart number" value={search} onChange={e => { setSearch(e.target.value); setPatientId('') }} /></label><label>Patient <select required value={patientId} onChange={e => setPatientId(e.target.value)}><option value="">Select existing patient</option>{patients.map(p => <option key={p.id} value={p.id}>{p.chart_no} — {p.first_name} {p.last_name}</option>)}</select></label><label>Dentist <select required value={dentistId} onChange={e => setDentistId(e.target.value)}><option value="">Select dentist</option>{dentists.map(d => <option key={d.id} value={d.id}>{d.full_name}</option>)}</select></label><label>Chair <select value={chairId} onChange={e => setChairId(e.target.value)}><option value="">Unassigned</option>{chairs.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label><label>Appointment type <select value={typeId} onChange={e => setTypeId(e.target.value)}><option value="">Standard (30 min)</option>{types.filter(t => t.active).map(t => <option key={t.id} value={t.id}>{t.name} · {t.default_minutes} min</option>)}</select></label><label>Start date/time <input required type="datetime-local" value={starts} onChange={e => setStarts(e.target.value)} /></label><label>Reason <input maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label><button className="ws-primary-link" disabled={busy || !branch}>Book appointment</button></form></section>
      {manager && <section className="ws-panel" style={{ padding: 16 }}><h3>Scheduling configuration</h3><form onSubmit={e => { e.preventDefault(); void change(() => api(`${endpoint}/types`, { method: 'POST', ...json({ name: typeName, defaultMinutes: typeMinutes }) }), 'Appointment type created'); setTypeName('') }}><label>New appointment type <input required maxLength={120} value={typeName} onChange={e => setTypeName(e.target.value)} /></label><label>Duration (minutes) <input type="number" min={5} max={480} value={typeMinutes} onChange={e => setTypeMinutes(Number(e.target.value))} /></label><button disabled={busy}>Add type</button></form><h3>Dentist weekly availability</h3><form onSubmit={e => { e.preventDefault(); void change(() => api(`${endpoint}/dentist-availability`, { method: 'POST', ...json({ dentistId, branchId: branch, dayOfWeek: availabilityDay, startsAt: availabilityStart, endsAt: availabilityEnd }) }), 'Availability saved') }}><label>Dentist <select required value={dentistId} onChange={e => setDentistId(e.target.value)}><option value="">Select dentist</option>{dentists.map(d => <option key={d.id} value={d.id}>{d.full_name}</option>)}</select></label><label>Weekday <select value={availabilityDay} onChange={e => setAvailabilityDay(Number(e.target.value))}>{['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'].map((d,i) => <option key={d} value={i+1}>{d}</option>)}</select></label><label>From <input type="time" value={availabilityStart} onChange={e => setAvailabilityStart(e.target.value)} /></label><label>To <input type="time" value={availabilityEnd} onChange={e => setAvailabilityEnd(e.target.value)} /></label><button disabled={busy}>Save hours</button></form><p>Chair blocks, time off, waitlist and notification outbox are available through tenant-scoped APIs. Delivery of messages belongs to M8.</p></section>}
    </>}
  </div>
}
