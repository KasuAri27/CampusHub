import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 后端地址可由环境变量注入（例如本机另起一套后端做联调时指向其他端口），缺省为本机 8080。
const apiTarget = process.env.CAMPUSHUB_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 业务接口：前端 baseURL /v1，代理时补上后端 context-path /api
      '/v1': {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
        rewrite: (path) => '/api' + path
      },
      // 上传文件 /api/uploads/** 与 WebSocket /api/ws/chat 原样转发
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        ws: true
      }
    }
  }
})
