import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { GoogleCredentialButton } from '../auth/GoogleCredentialButton'
import { getRememberedSlug, normalizeSlug, rememberSlug, slugFromHost } from '../lib/tenant'

export function LoginPage() {
  const { status, signInWithGoogle } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const hostSlug = slugFromHost()
  const [slugInput, setSlugInput] = useState(getRememberedSlug())
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const clinicSlug = hostSlug ?? normalizeSlug(slugInput)

  if (status === 'authenticated') return <Navigate to={from} replace />

  async function handleCredential(idToken: string | undefined) {
    if (!idToken) {
      setError('Google did not return a credential. Please try again.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await signInWithGoogle(clinicSlug, idToken)
      if (!hostSlug) rememberSlug(clinicSlug)
      navigate(from, { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setError('This Google account has no access to that clinic. Ask your clinic administrator to invite you.')
      } else if (e instanceof ApiError && e.status === 404) {
        setError('Clinic not found. Check the clinic code.')
      } else {
        setError('Sign-in failed. Please try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="center">
      <section className="card login">
        <h1>Dental Clinic</h1>
        <p className="muted">Sign in to your clinic</p>

        {!hostSlug && (
          <label className="field">
            <span>Clinic code</span>
            <input
              value={slugInput}
              onChange={(e) => setSlugInput(e.target.value)}
              placeholder="e.g. greenvalley"
              autoComplete="organization"
              autoCapitalize="none"
              spellCheck={false}
            />
          </label>
        )}

        <div className={`google-slot ${busy ? 'busy' : ''}`}>
          {clinicSlug ? (
            <GoogleCredentialButton
              onSuccess={(res) => void handleCredential(res.credential)}
              onError={() => setError('Google sign-in was cancelled or failed.')}
              text="signin_with"
              shape="rectangular"
              size="large"
              width="300"
            />
          ) : (
            <p className="muted small">Enter your clinic code to continue.</p>
          )}
        </div>

        {busy && <p className="muted small">Signing you in…</p>}
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <Link to="/register-clinic" className="muted small">Register a clinic (when enabled)</Link>
        <Link to="/accept-invitation" className="muted small">Accept an invitation</Link>
        <Link to="/platform-admin/login" className="muted small">Platform administrator sign in</Link>
      </section>
    </main>
  )
}
