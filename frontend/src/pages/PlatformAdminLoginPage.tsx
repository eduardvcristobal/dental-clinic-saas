import { GoogleLogin } from '@react-oauth/google'
import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'

export function PlatformAdminLoginPage() {
  const { status, session, signInAsPlatformAdmin } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/platform-admin'
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to={session?.user.platformAdmin ? from : '/'} replace />
  }

  async function handleCredential(idToken: string | undefined) {
    if (!idToken) {
      setError('Google did not return a credential. Please try again.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await signInAsPlatformAdmin(idToken)
      navigate(from, { replace: true })
    } catch (e) {
      setError(e instanceof ApiError && e.status === 403
        ? 'This Google account is not on the platform administrator allowlist.'
        : 'Sign-in failed. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="center">
      <section className="card login">
        <h1>Platform administration</h1>
        <p className="muted">Sign in with an allowlisted Google account. No clinic code is required.</p>
        <div className={`google-slot ${busy ? 'busy' : ''}`}>
          <GoogleLogin
            onSuccess={(res) => void handleCredential(res.credential)}
            onError={() => setError('Google sign-in was cancelled or failed.')}
            text="signin_with"
            shape="rectangular"
            size="large"
            width="300"
          />
        </div>
        {busy && <p className="muted small">Signing you in…</p>}
        {error && <p role="alert" className="error">{error}</p>}
        <Link to="/login" className="muted small">Clinic staff sign in</Link>
      </section>
    </main>
  )
}
