import { GoogleLogin } from '@react-oauth/google'
import { useLayoutEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'

export function InvitationAcceptancePage() {
  const [params] = useSearchParams()
  const [token,setToken] = useState(params.get('token') ?? '')
  // Remove the bearer credential from the address bar even when acceptance fails.
  useLayoutEffect(() => {
    if (params.has('token')) window.history.replaceState(window.history.state, '', '/accept-invitation')
  }, [params])
  const [fullName,setFullName] = useState('')
  const [error,setError] = useState('')
  const [accepted,setAccepted] = useState(false)
  const [busy,setBusy] = useState(false)
  async function accept(googleIdToken?: string) {
    if(!googleIdToken) return setError('Google verification required')
    setBusy(true);setError('')
    try {
      await api('/api/onboarding/accept',{method:'POST',body:JSON.stringify({googleIdToken,token,fullName})},false)
      setAccepted(true);setToken('')
      window.history.replaceState({},'', '/accept-invitation')
    } catch(e) { setError(String(e)) } finally { setBusy(false) }
  }
  return <main className="center"><section className="card login">
    <h1>Join your dental clinic</h1>
    {accepted ? <p role="status">Invitation accepted. <Link to="/login">Sign in to your clinic</Link>.</p> : <>
      <p className="muted">Use the Google account matching the invited email. Invitations expire and can only be used once.</p>
      <label className="field">Invitation token<input type="password" required autoComplete="off" value={token} onChange={e=>setToken(e.target.value)}/></label>
      <label className="field">Your full name<input required value={fullName} onChange={e=>setFullName(e.target.value)}/></label>
      {error && <p role="alert" className="error">{error}</p>}
      {token && fullName && !busy && <GoogleLogin onSuccess={r=>void accept(r.credential)} onError={()=>setError('Google verification failed')}/>}
    </>}
    <Link to="/login">Back to sign in</Link>
  </section></main>
}
