import { Router } from 'express'
import { getDb } from '../store.js'
import { ok, fail, ERR, createSession, dropSession, requireLogin } from '../http.js'

/** /app-api/**：C 端（认养用户）用到的接口 */
export const appRouter = Router()

appRouter.get('/system/tenant/get-by-website', (req, res) => {
  const w = String(req.query.website ?? '')
  const t = getDb().tenants.find((x) => x.websites?.includes(w))
  return ok(res, t ? { id: t.id, name: t.name } : null)
})

appRouter.get('/promotion/diy-template/used', (req, res) => {
  const list = getDb().diyTemplates
  const tpl = list.find((t) => t.tenantId === req.tenantId) ?? list[0]
  return ok(res, tpl ?? null)
})

appRouter.get('/trade/config/get', (_req, res) => ok(res, { brokerageEnabled: false }))

appRouter.post('/member/auth/login', (req, res) => {
  const { mobile, password } = req.body ?? {}
  const m = getDb().members.find((x) => x.tenantId === req.tenantId && x.mobile === mobile)
  if (!m || m.password !== password) return fail(res, ERR.AUTH_LOGIN_BAD_CREDENTIALS, '登录失败，账号密码不正确')
  const s = createSession('member', m.id, m.tenantId)
  return ok(res, { userId: m.id, accessToken: s.token, refreshToken: s.refreshToken, expiresTime: Date.now() + 30 * 60 * 1000 })
})

appRouter.post('/member/auth/logout', (req, res) => {
  dropSession(req.session?.token)
  return ok(res, true)
})

appRouter.get('/member/user/get', requireLogin('member'), (req, res) => {
  const m = getDb().members.find((x) => x.id === req.session!.userId)
  if (!m) return fail(res, ERR.UNAUTHORIZED, '账号未登录')
  return ok(res, {
    id: m.id, nickname: m.nickname, avatar: m.avatar ?? '', mobile: m.mobile, address: m.address ?? '',
    point: 0, experience: 0, level: null, brokerageEnabled: false
  })
})

// 芋道商城启动时会拉购物车；认养 Demo 没有购物车，按芋道格式返回空，避免前端 store 报错
appRouter.get('/trade/cart/list', (_req, res) => ok(res, { validList: [], invalidList: [] }))
