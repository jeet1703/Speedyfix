import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development, /api is proxied to the Spring Boot backend on port 8080
// (override with API_PROXY_TARGET if that port is taken).
export default defineConfig({
  base: process.env.GITHUB_ACTIONS === 'true' ? '/Speedyfix/' : '/',
  plugins: [react()],
  server: {
    // Always 5173 (it's the origin registered for Google sign-in and CORS): fail rather than move to another port.
    port: 5173,
    strictPort: true,
    proxy: { '/api': process.env.API_PROXY_TARGET || 'http://localhost:8080' }
  }
});
