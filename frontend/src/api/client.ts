import { API_URL } from '../config'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export interface SessionUser {
  id: string
  fullName: string
  email: string
  role: 'OWNER' | 'ADMIN' | 'DENTIST' | 'FRONT_DESK' | 'PLATFORM_ADMIN'
  platformAdmin?: boolean
}

export interface SessionClinic {
  id: string
  slug: string
  name: string
}

export interface Session {
  accessToken: string
  user: SessionUser
  clinic: SessionClinic | null
}

// The access token lives in memory only (never localStorage), so XSS cannot read it from storage.
// Persistence across reloads comes from the httpOnly refresh cookie set by the backend.
let accessToken: string | null = null
let onSessionExpired: (() => void) | null = null
let refreshInFlight: Promise<Session | null> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function setSessionExpiredHandler(handler: (() => void) | null): void {
  onSessionExpired = handler
}

async function parseError(res: Response): Promise<ApiError> {
  let message = res.statusText || 'Request failed'
  try {
    const body = await res.json()
    if (typeof body?.detail === 'string') message = body.detail
    else if (typeof body?.message === 'string') message = body.message
    else if (typeof body?.error === 'string') message = body.error
  } catch {
    /* non-JSON body */
  }
  return new ApiError(res.status, message)
}

/** Ask the backend for a new access token using the refresh cookie. Concurrent calls share one request. */
export function refreshSession(): Promise<Session | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const res = await fetch(`${API_URL}/api/auth/refresh`, { method: 'POST', credentials: 'include' })
        if (!res.ok) return null
        const session = (await res.json()) as Session
        accessToken = session.accessToken
        return session
      } catch {
        return null
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const res = await fetch(`${API_URL}${path}`, { ...init, headers, credentials: 'include' })

  if (res.status === 401 && retry && !path.startsWith('/api/auth/')) {
    const session = await refreshSession()
    if (session) return api<T>(path, init, false)
    accessToken = null
    onSessionExpired?.()
  }
  if (!res.ok) throw await parseError(res)
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}
