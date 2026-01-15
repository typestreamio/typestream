import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    watch: {
      usePolling: false,
      ignored: ['**/.pnpm-store/**'],
    },
    proxy: {
      '/connect/': {
        target: 'http://envoy:8080',
        changeOrigin: true,
      },
    },
  },
})
