import type { Event } from './SchedulingWorkspace'

export type CalendarView = 'Day' | 'Week' | 'Month'

/** ISO window for the selected view starting at the given local date. */
export function calendarRange(date: string, view: CalendarView): { from: Date; to: Date } {
  const from = new Date(`${date}T00:00:00`)
  const to = new Date(from)
  if (view === 'Day') to.setDate(to.getDate() + 1)
  if (view === 'Week') to.setDate(to.getDate() + 7)
  if (view === 'Month') to.setDate(to.getDate() + 30)
  return { from, to }
}

/** Dentist and chair filters applied client-side on top of the branch-scoped API result. */
export function filterEvents(events: Event[], dentistId: string, chairId: string): Event[] {
  return events.filter(e => (!dentistId || e.dentist_name === dentistId) && (!chairId || e.chair_id === chairId))
}

/** Allowed lifecycle buttons per status (mirrors the server transition table). */
export function nextActions(status: string): Array<{ label: string; status: string; reason?: 'prompt' }> {
  if (status === 'BOOKED') return [{ label: 'Confirm', status: 'CONFIRMED' }, { label: 'Check in', status: 'ARRIVED' }, { label: 'No show', status: 'NO_SHOW' }, { label: 'Cancel', status: 'CANCELLED', reason: 'prompt' }]
  if (status === 'CONFIRMED') return [{ label: 'Check in', status: 'ARRIVED' }, { label: 'No show', status: 'NO_SHOW' }, { label: 'Cancel', status: 'CANCELLED', reason: 'prompt' }]
  if (status === 'ARRIVED') return [{ label: 'In chair', status: 'IN_CHAIR' }, { label: 'Cancel', status: 'CANCELLED', reason: 'prompt' }]
  if (status === 'IN_CHAIR') return [{ label: 'Complete', status: 'COMPLETED' }]
  return []
}
