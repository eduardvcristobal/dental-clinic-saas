import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In dev, /api is proxied to the Spring Boot backend so the browser sees one origin
// (no CORS, and the httpOnly refresh cookie works). In production, serve both behind one domain
// (or set VITE_API_URL and configure CORS with credentials on the backend).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
