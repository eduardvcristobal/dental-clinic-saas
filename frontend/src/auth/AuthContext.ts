import { createContext } from 'react'
import type { Session } from '../api/client'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  session: Session | null
  signInWithGoogle: (clinicSlug: string, idToken: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
