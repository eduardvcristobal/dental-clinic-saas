import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

export function ProtectedRoute({ platformAdminOnly = false }: { platformAdminOnly?: boolean }) {
  const { status, session } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <div className="center muted">Loading…</div>
  if (status === 'anonymous') return <Navigate to={platformAdminOnly ? '/platform-admin/login' : '/login'} replace state={{ from: location.pathname }} />
  if (platformAdminOnly && session?.user.platformAdmin !== true) return <Navigate to="/" replace />
  return <Outlet />
}
