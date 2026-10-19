import { googleLogout } from '@react-oauth/google'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { loginWithGoogle, logout } from '../api/auth'
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

  const signOut = useCallback(async () => {
    try {
      await logout()
    } finally {
      googleLogout() // stop Google's auto-select from silently signing the next person in
      clear()
    }
  }, [clear])

  const value = useMemo<AuthContextValue>(
    () => ({ status, session, signInWithGoogle, signOut }),
    [status, session, signInWithGoogle, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
