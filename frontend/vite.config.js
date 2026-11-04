import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 개발 서버 포트는 백엔드 CORS 허용 Origin(http://localhost:5173)과 맞춘다.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // seed-design CLI 스니펫 import (`seed-design/ui/...`)를 frontend/seed-design 으로 연결한다.
      'seed-design': fileURLToPath(new URL('./seed-design', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
});
