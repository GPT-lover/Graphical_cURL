import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite config. The React plugin enables JSX + Fast Refresh.
// The dev server runs on port 5173 (Vite's default) - the backend's CORS rules
// allow exactly this origin. `strictPort` makes Vite fail loudly instead of
// silently picking another port (which would then be blocked by CORS).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
})
