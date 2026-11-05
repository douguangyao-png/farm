import express from 'express'
import cors from 'cors'
import { attachContext, ok, fail, ERR, clearSessions } from './http.js'
import { resetDb } from './store.js'
import { systemRouter } from './routes/system.js'
import { appRouter } from './routes/app.js'

/** 没实现的接口记下来，方便补齐：GET /__mock/unhandled 查看 */
const unhandled = new Map<string, number>()

export function createApp() {
  const app = express()
  app.use(cors({ origin: true, credentials: true }))
  app.use(express.json({ limit: '5mb' }))
  app.use(attachContext)

  app.use('/admin-api/system', systemRouter)
  app.use('/app-api', appRouter)

  // ---- mock 管理接口 ----
  app.post('/__mock/reset', (_req, res) => {
    resetDb()
    clearSessions()
    unhandled.clear()
    return ok(res, true)
  })
  app.get('/__mock/unhandled', (_req, res) => ok(res, Object.fromEntries(unhandled)))
  app.get('/__mock/health', (_req, res) => ok(res, 'up'))

  // ---- 兜底：芋道页面会顺带调很多接口 ----
  // 查询类返回空结果，页面不报错；写操作明确提示「没实现」，不假装成功
  app.use((req, res) => {
    const key = `${req.method} ${req.path}`
    unhandled.set(key, (unhandled.get(key) ?? 0) + 1)
    if (req.method === 'GET') {
      if (/\/page$/.test(req.path)) return ok(res, { list: [], total: 0 })
      if (/(list|tree)$/.test(req.path)) return ok(res, [])
      return ok(res, null)
    }
    return fail(res, ERR.NOT_IMPLEMENTED, '演示环境暂不支持该操作')
  })
  return app
}
