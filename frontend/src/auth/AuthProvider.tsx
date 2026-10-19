import { googleLogout } from '@react-oauth/google'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { loginPlatformAdminWithGoogle, loginWithGoogle, logout } from '../api/auth'
import { refreshSession, setAccessToken, setSessionExpiredHandler } from '../api/client'
import type { Session } from '../api/client'
import { AuthContext } from './AuthContext'
import type { AuthContextValue, AuthStatus } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [session, setSession] = useState<Session | null>(null)

  const clear = useCallback(() => {
    setAccessToken(null)
    setSession(null)
    setStatus('anonymous')
  }, [])

  // On first load, try to restore the session from the refresh cookie.
  useEffect(() => {
    let cancelled = false
    refreshSession().then((s) => {
      if (cancelled) return
      if (s) {
        setSession(s)
        setStatus('authenticated')
      } else {
        setAccessToken(null)
        setStatus('anonymous')
      }
    })
    setSessionExpiredHandler(clear)
    return () => {
      cancelled = true
      setSessionExpiredHandler(null)
    }
  }, [clear])

  const signInWithGoogle = useCallback(async (clinicSlug: string, idToken: string) => {
    const s = await loginWithGoogle(clinicSlug, idToken)
    setSession(s)
    setStatus('authenticated')
  }, [])

  const signInAsPlatformAdmin = useCallback(async (idToken: string) => {
    const s = await loginPlatformAdminWithGoogle(idToken)
    setSession(s)
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(async () => {
    try {
      await logout()
    } finally {
      // Google's auto-select must never silently sign the next person in, but a failure or absence of
      // the script must not stop the local session from being cleared either.
      try {
        googleLogout()
      } catch {
        /* GSI unavailable: the local session is still ended below */
      }
      clear()
    }
  }, [clear])

  const value = useMemo<AuthContextValue>(
    () => ({ status, session, signInWithGoogle, signInAsPlatformAdmin, signOut }),
    [status, session, signInWithGoogle, signInAsPlatformAdmin, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
