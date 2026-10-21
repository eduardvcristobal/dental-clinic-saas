import { createContext, useContext } from 'react'

export type ToastApi = {
  success: (message: string) => void
  error: (message: string) => void
  info: (message: string) => void
  dismiss: (id: number) => void
}

export const ToastContext = createContext<ToastApi | null>(null)

/** Announces save, update, delete and failure outcomes to the person at the keyboard. */
export function useToast(): ToastApi {
  const api = useContext(ToastContext)
  if (!api) throw new Error('useToast must be used inside a ToastProvider')
  return api
}
