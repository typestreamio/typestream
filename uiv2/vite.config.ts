import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    watch: {
      usePolling: process.env.CHOKIDAR_USEPOLLING === 'true',
      ignored: ['**/.pnpm-store/**'],
    },
    hmr: {
      host: 'localhost',
    },
    proxy: {
      '/connect/': {
        target: 'http://envoy:8080',
        changeOrigin: true,
      },
    },
  },
})
