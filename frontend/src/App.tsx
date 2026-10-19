import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { ClinicWorkspace } from './workspace/ClinicWorkspace'
import { LoginPage } from './pages/LoginPage'
import { ClinicRegistrationPage } from './pages/ClinicRegistrationPage'
import { InvitationAcceptancePage } from './pages/InvitationAcceptancePage'
import { PlatformAdminLoginPage } from './pages/PlatformAdminLoginPage'
import { PlatformAdminPage } from './pages/PlatformAdminPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register-clinic" element={<ClinicRegistrationPage />} />
          <Route path="/accept-invitation" element={<InvitationAcceptancePage />} />
          <Route path="/platform-admin/login" element={<PlatformAdminLoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Navigate to="/workspace/command-center" replace />} />
            <Route path="/workspace/:moduleId" element={<ClinicWorkspace />} />
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
