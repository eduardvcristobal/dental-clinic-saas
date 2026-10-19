import { GoogleOAuthProvider } from '@react-oauth/google'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { GOOGLE_CLIENT_ID } from './config'
import './index.css'

function MissingConfig() {
  return (
    <main className="center">
      <section className="card login">
        <h1>Setup needed</h1>
        <p>
          Set <code>VITE_GOOGLE_CLIENT_ID</code> in <code>frontend/.env</code> (see <code>.env.example</code>), then
          restart the dev server.
        </p>
      </section>
    </main>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {GOOGLE_CLIENT_ID ? (
      <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
        <App />
      </GoogleOAuthProvider>
    ) : (
      <MissingConfig />
    )}
  </StrictMode>,
)
