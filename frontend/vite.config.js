import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server on :5173 forwards /api/* to the Spring Boot app on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
