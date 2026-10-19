import { api, setAccessToken } from './client'
import type { Session } from './client'

/**
 * Backend contract (to implement in Spring Boot):
 *   POST /api/auth/google   { clinicSlug, idToken }  -> Session  (+ Set-Cookie: refresh token, httpOnly, Secure, SameSite)
 *   POST /api/auth/refresh  (cookie)                  -> Session
 *   POST /api/auth/logout   (cookie)                  -> 204, clears the cookie
 *
 * The backend must verify the Google ID token (signature, iss, aud = your client ID, exp, email_verified),
 * then look up an ACTIVE app_user with that email inside the clinic identified by clinicSlug.
 * No matching user = 403. Google sign-in is login only: staff are invited by a clinic admin first.
 */
export async function loginWithGoogle(clinicSlug: string, idToken: string): Promise<Session> {
  const session = await api<Session>('/api/auth/google', {
    method: 'POST',
    body: JSON.stringify({ clinicSlug, idToken }),
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
