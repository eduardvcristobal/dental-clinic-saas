import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { GoogleCredentialButton } from '../auth/GoogleCredentialButton'

export function ClinicRegistrationPage() {
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [timezone, setTimezone] = useState('Asia/Manila')
  const [branchName, setBranchName] = useState('Main Branch')
  const [ownerName, setOwnerName] = useState('')
  const [error, setError] = useState('')
  const [created, setCreated] = useState(false)
  const [busy, setBusy] = useState(false)
  const [registrationEnabled, setRegistrationEnabled] = useState<boolean | null>(null)
  const [availabilityError, setAvailabilityError] = useState('')
  useEffect(() => {
    let active = true
    void api<{selfServiceEnabled:boolean}>('/api/onboarding/status', {}, false)
      .then(status => { if (active) setRegistrationEnabled(status.selfServiceEnabled) })
      .catch(() => { if (active) setAvailabilityError('Could not check clinic registration availability. Please try again later.') })
    return () => { active = false }
  }, [])
  async function create(googleIdToken?: string) {
    if (!googleIdToken) { setError('Google verification required'); return }
    setBusy(true);setError('')
    try {
      await api('/api/onboarding/clinics', {method:'POST',body:JSON.stringify({googleIdToken,slug,name,timezone,branchName,ownerName})},false)
      setCreated(true)
    } catch(e) { setError(String(e)) } finally { setBusy(false) }
  }
  return <main className="center"><section className="card login">
    <h1>Register a clinic</h1>
    {availabilityError ? <p role="alert" className="error">{availabilityError}</p> : registrationEnabled === null ? <p className="muted">Checking clinic registration availability…</p> : !registrationEnabled ? <p className="muted">Clinic registration is currently unavailable.</p> : created ? <p role="status">Clinic created. <Link to="/login">Sign in using its clinic code</Link>.</p> : <>
      <label className="field">Clinic code<input required pattern="[a-z0-9][a-z0-9-]{2,62}" value={slug} onChange={e=>setSlug(e.target.value.toLowerCase())}/></label>
      <label className="field">Clinic name<input required value={name} onChange={e=>setName(e.target.value)}/></label>
      <label className="field">First branch<input required value={branchName} onChange={e=>setBranchName(e.target.value)}/></label>
      <label className="field">Timezone<input required value={timezone} onChange={e=>setTimezone(e.target.value)}/></label>
      <label className="field">Owner full name<input required value={ownerName} onChange={e=>setOwnerName(e.target.value)}/></label>
      {error && <p role="alert" className="error">{error}</p>}
      <p className="muted small">Complete all fields, then verify with the first owner's Google account.</p>
      {slug && name && branchName && timezone && ownerName && !busy && <GoogleCredentialButton onSuccess={r=>void create(r.credential)} onError={()=>setError('Google verification failed')}/>}
    </>}
    <Link to="/login">Back to sign in</Link>
  </section></main>
}
