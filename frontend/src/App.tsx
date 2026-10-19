import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PlatformAdminLoginPage } from './pages/PlatformAdminLoginPage'
import { PlatformAdminPage } from './pages/PlatformAdminPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/platform-admin/login" element={<PlatformAdminLoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<DashboardPage />} />
          </Route>
          <Route element={<ProtectedRoute platformAdminOnly />}>
            <Route path="/platform-admin" element={<PlatformAdminPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
