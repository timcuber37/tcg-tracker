import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy API calls to the Spring Boot backend during development so the
    // SPA and API share an origin (no CORS needed in dev).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/card-image': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
