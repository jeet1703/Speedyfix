import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development, /api is proxied to the Spring Boot backend on port 8080
// (override with API_PROXY_TARGET if that port is taken).
export default defineConfig({
  base: process.env.GITHUB_ACTIONS === 'true' ? '/Speedyfix/' : '/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': process.env.API_PROXY_TARGET || 'http://localhost:8080' }
  }
});
