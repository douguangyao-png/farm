import { createApp } from './app.js'

const port = Number(process.env.MOCK_PORT ?? 48090)
// 服务器上只监听本机，由 nginx 反代（部署方案 T3）；本地开发默认也只监听本机
const host = process.env.MOCK_HOST ?? '127.0.0.1'
createApp().listen(port, host, () => {
  console.log(`[mock-server] 认养农场 SaaS 演示后端已启动：http://${host}:${port}`)
  console.log('[mock-server] 后台/管家端 /admin-api，C 端 /app-api，重置数据 POST /__mock/reset')
})
