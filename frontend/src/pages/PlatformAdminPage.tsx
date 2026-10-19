import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/useAuth'

interface ClinicSummary {
  id: string
  slug: string
  name: string
  planCode: string
  subscriptionStatus: string
  active: boolean
  createdAt: string
  staffCount: number
  patientCount: number
}

export function PlatformAdminPage() {
  const { session, signOut } = useAuth()
  const [clinics, setClinics] = useState<ClinicSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api<ClinicSummary[]>('/api/platform-admin/clinics')
      .then(setClinics)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Could not load clinics.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="shell">
      <header className="topbar">
        <strong>Platform administration</strong>
        <div className="topbar-right">
          <span className="muted small">{session?.user.email} · no clinic selected</span>
          <button className="btn" onClick={() => void signOut()}>Sign out</button>
        </div>
      </header>
      <main className="content">
        <h2>Clinics</h2>
        <p className="muted">Global overview across all clinics. This session has no clinic ID.</p>
        {loading && <p className="muted">Loading clinics…</p>}
        {error && <p role="alert" className="error">{error}</p>}
        {!loading && !error && clinics.length === 0 && <section className="card">No clinics have been created yet.</section>}
        {!loading && clinics.length > 0 && (
          <div className="card" style={{ overflowX: 'auto', marginTop: '1rem' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead><tr><th>Clinic</th><th>Slug</th><th>Status</th><th>Plan</th><th>Staff</th><th>Patients</th></tr></thead>
              <tbody>{clinics.map((clinic) => (
                <tr key={clinic.id}>
                  <td>{clinic.name}</td><td><code>{clinic.slug}</code></td>
                  <td>{clinic.active ? clinic.subscriptionStatus : 'INACTIVE'}</td><td>{clinic.planCode}</td>
                  <td>{clinic.staffCount}</td><td>{clinic.patientCount}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  )
}
