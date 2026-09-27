/* 学员端开发构建配置：加载环境变量，使用 5174 端口并将 API 请求代理到网关。 */
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
// 根据当前 mode 读取对应环境文件，生成开发服务器和构建配置。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: env.VITE_GATEWAY_TARGET || 'http://localhost:63010',
          changeOrigin: true,
          // 去掉前端统一的 /api 前缀，使网关收到 /content 或 /media 开头的路径。
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
