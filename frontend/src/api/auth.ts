import { api, setAccessToken } from './client'
import type { Session } from './client'

/** Backend verifies Google's signed ID token, then admits active clinic staff only. */
export async function loginWithGoogle(clinicSlug: string, idToken: string): Promise<Session> {
  const session = await api<Session>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ clinicSlug, idToken }),
  })
  setAccessToken(session.accessToken)
  return session
}

/** Platform admins authenticate from the email allowlist and receive a tenantless session. */
export async function loginPlatformAdminWithGoogle(idToken: string): Promise<Session> {
  const session = await api<Session>('/api/auth/platform-admin/google', {
    method: 'POST',
    body: JSON.stringify({ idToken }),
  })
  setAccessToken(session.accessToken)
  return session
}

export async function logout(): Promise<void> {
  try {
    await api<void>('/api/auth/logout', { method: 'POST' })
  } finally {
    setAccessToken(null)
  }
}
