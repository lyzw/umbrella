import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理：/api → 后端 19100，前端无需处理 CORS
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5180,
    proxy: {
      '/api': {
        target: 'http://localhost:19100',
        changeOrigin: true
      }
    }
  }
})
