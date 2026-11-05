import { createApp } from './app.js'

const port = Number(process.env.MOCK_PORT ?? 48090)
createApp().listen(port, () => {
  console.log(`[mock-server] 认养农场 SaaS 演示后端已启动：http://localhost:${port}`)
  console.log('[mock-server] 后台/管家端 /admin-api，C 端 /app-api，重置数据 POST /__mock/reset')
})
