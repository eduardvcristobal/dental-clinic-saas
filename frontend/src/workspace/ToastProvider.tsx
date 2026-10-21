import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { nextToastList, toastDuration, type ToastKind } from './toast'
import { ToastContext, type ToastApi } from './toastContext'

type Toast = { id: number; kind: ToastKind; message: string }

function ToastIcon({ kind }: { kind: ToastKind }) {
  if (kind === 'success') {
    return <svg viewBox="0 0 20 20" fill="none"><path d="m5 10.5 3.2 3.2L15 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
  }
  if (kind === 'error') {
    return <svg viewBox="0 0 20 20" fill="none"><path d="M10 6v5M10 14h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
  }
  return <svg viewBox="0 0 20 20" fill="none"><path d="M10 9v5M10 6h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const timers = useRef(new Map<number, number>())
  const nextId = useRef(1)

  const clearTimer = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timers.current.delete(id)
    }
  }, [])

  const dismiss = useCallback((id: number) => {
    clearTimer(id)
    setToasts(current => current.filter(toast => toast.id !== id))
  }, [clearTimer])

  const schedule = useCallback((id: number, kind: ToastKind) => {
    clearTimer(id)
    timers.current.set(id, window.setTimeout(() => dismiss(id), toastDuration(kind)))
  }, [clearTimer, dismiss])

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId.current++
    setToasts(current => nextToastList(current, { id, kind, message }))
    schedule(id, kind)
  }, [schedule])

  useEffect(() => {
    const pending = timers.current
    return () => {
      pending.forEach(timer => window.clearTimeout(timer))
      pending.clear()
    }
  }, [])

  const api = useMemo<ToastApi>(() => ({
    success: (message: string) => push('success', message),
    error: (message: string) => push('error', message),
    info: (message: string) => push('info', message),
    dismiss,
  }), [push, dismiss])

  return <ToastContext.Provider value={api}>
    {children}
    <div className="ws-toast-stack" role="region" aria-label="Notifications">
      {toasts.map(toast => <div key={toast.id} className="ws-toast" data-kind={toast.kind} role={toast.kind === 'error' ? 'alert' : 'status'} onMouseEnter={() => clearTimer(toast.id)} onMouseLeave={() => schedule(toast.id, toast.kind)}>
        <span className="ws-toast-icon" aria-hidden="true"><ToastIcon kind={toast.kind} /></span>
        <p className="ws-toast-message">{toast.message}</p>
        <button type="button" className="ws-toast-close" aria-label="Dismiss notification" onClick={() => dismiss(toast.id)}>×</button>
      </div>)}
    </div>
  </ToastContext.Provider>
}
