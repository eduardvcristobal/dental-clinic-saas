export type ToastKind = 'success' | 'error' | 'info'

export const MAX_VISIBLE_TOASTS = 4

/** How long a toast stays on screen before it dismisses itself. */
export function toastDuration(kind: ToastKind): number {
  if (kind === 'error') return 8000
  if (kind === 'info') return 4500
  return 4000
}

/**
 * Turns anything thrown by a request into a message a clinician can act on.
 * `String(error)` would leak the "ApiError: " prefix into the UI, so the
 * message is read from the error itself and the fallback covers unknown shapes.
 */
export function describeError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) return error.message.trim()
  if (typeof error === 'string' && error.trim()) return error.trim()
  return fallback
}

/** Appends a toast and keeps only the newest MAX_VISIBLE_TOASTS entries. */
export function nextToastList<T extends { id: number }>(current: T[], next: T): T[] {
  return [...current, next].slice(-MAX_VISIBLE_TOASTS)
}
