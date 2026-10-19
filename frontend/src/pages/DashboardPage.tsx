import { useAuth } from '../auth/useAuth'
import { Navigate } from 'react-router-dom'

const MODULES = [
  { name: 'Calendar', note: 'Appointments by dentist and chair' },
  { name: 'Patients', note: 'Records, medical history, files' },
  { name: 'Dental chart', note: 'Odontogram and tooth history' },
  { name: 'Treatment plans', note: 'Multi-visit plans and pricing' },
  { name: 'Billing', note: 'Invoices, payments, installments' },
  { name: 'Reports', note: 'Collections, production, no-shows' },
]

export function DashboardPage() {
  const { session, signOut } = useAuth()
  if (!session) return null
  if (session.user.platformAdmin || !session.clinic) return <Navigate to="/platform-admin" replace />

  return (
    <div className="shell">
      <header className="topbar">
        <strong>{session.clinic.name}</strong>
        <div className="topbar-right">
          <span className="muted small">
            {session.user.fullName} · {session.user.role.replace('_', ' ').toLowerCase()}
          </span>
          <button className="btn" onClick={() => void signOut()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="content">
        <h2>Welcome, {session.user.fullName.split(' ')[0]}</h2>
        <div className="grid">
          {MODULES.map((m) => (
            <div key={m.name} className="card module" aria-disabled="true">
              <h3>{m.name}</h3>
              <p className="muted small">{m.note}</p>
              <span className="badge">Coming soon</span>
            </div>
          ))}
        </div>
      </main>
    </div>
  )
}
