import { Router } from 'express'
import { getDb, nextId, type Tenant } from '../store.js'
import { ok, requireLogin } from '../http.js'
import { page, q, like, eqNum, byNewest, notFound, badRequest, requirePlatform } from '../farm/common.js'

/**
 * 平台端（契约第 1 节）：租户、套餐用芋道自带页面，接口路径按芋道；风控预警、平台总览是新增的。
 * 挂在 /admin-api 下。
 */
export const platformRouter = Router()
platformRouter.use(['/system/tenant', '/system/tenant-package', '/farm/risk-alert', '/farm/platform'], requireLogin('admin'), requirePlatform)

const db = getDb
const OPERATOR_ROLE_MENUS = () => db().roles.find((r) => r.id === 2)!.menuIds

// ---------- 1.1 租户 ----------
function tenantView(t: Tenant) {
  return { ...t, accountCount: t.accountCount ?? 10, websites: t.websites ?? [], packageName: db().tenantPackages.find((p) => p.id === t.packageId)?.name ?? '平台' }
}
platformRouter.get('/system/tenant/page', (req, res) => {
  const rows = db().tenants.filter((t) => like(t.name, q(req, 'name')) && like(t.contactName, q(req, 'contactName')) && like(t.contactMobile, q(req, 'contactMobile')) && eqNum(t.status, q(req, 'status')))
    .slice().sort((a, b) => (b.createTime ?? 0) - (a.createTime ?? 0)).map(tenantView)
  return ok(res, page(req, rows))
})
platformRouter.get('/system/tenant/get', (req, res) => {
  const t = db().tenants.find((x) => x.id === Number(req.query.id))
  return t ? ok(res, tenantView(t)) : notFound(res, '租户')
})
platformRouter.post('/system/tenant/create', (req, res) => {
  const b = req.body ?? {}
  if (!String(b.name ?? '').trim()) return badRequest(res, '租户名不能为空')
  if (db().tenants.some((t) => t.name === String(b.name).trim())) return badRequest(res, '租户名已存在')
  if (!String(b.username ?? '').trim() || !String(b.password ?? '').trim()) return badRequest(res, '请填写管理员账号和密码')
  if (!db().tenantPackages.some((p) => p.id === Number(b.packageId))) return badRequest(res, '请选择套餐')
  const id = nextId(db().tenants)
  const t: Tenant = { id, name: String(b.name).trim(), contactName: b.contactName ?? '', contactMobile: b.contactMobile ?? '', status: Number(b.status) || 0,
    packageId: Number(b.packageId), expireTime: Number(b.expireTime) || Date.now() + 365 * 86400_000, websites: b.websites ?? [], accountCount: Number(b.accountCount) || 10, createTime: Date.now() }
  db().tenants.push(t)
  const roleId = nextId(db().roles)
  db().roles.push({ id: roleId, tenantId: id, code: 'tenant_admin', name: '经营者管理员', menuIds: OPERATOR_ROLE_MENUS() })
  db().users.push({ id: nextId(db().users), tenantId: id, username: String(b.username).trim(), password: String(b.password), nickname: b.contactName || t.name,
    roleIds: [roleId], kind: 'admin', mobile: b.contactMobile ?? '' })
  return ok(res, id)
})
platformRouter.put('/system/tenant/update', (req, res) => {
  const t = db().tenants.find((x) => x.id === Number(req.body?.id))
  if (!t) return notFound(res, '租户')
  if (req.body.name !== undefined && db().tenants.some((x) => x.id !== t.id && x.name === req.body.name)) return badRequest(res, '租户名已存在')
  for (const k of ['name', 'contactName', 'contactMobile', 'status', 'packageId', 'expireTime', 'websites', 'accountCount'] as const) if (req.body[k] !== undefined) (t as unknown as Record<string, unknown>)[k] = req.body[k]
  return ok(res, true)
})

// ---------- 1.1 套餐 ----------
platformRouter.get('/system/tenant-package/page', (req, res) =>
  ok(res, page(req, db().tenantPackages.filter((p) => like(p.name, q(req, 'name')) && eqNum(p.status, q(req, 'status')))))
)
platformRouter.get('/system/tenant-package/get', (req, res) => {
  const p = db().tenantPackages.find((x) => x.id === Number(req.query.id))
  return p ? ok(res, p) : notFound(res, '套餐')
})
for (const path of ['/system/tenant-package/simple-list', '/system/tenant-package/get-simple-list'])
  platformRouter.get(path, (_req, res) => ok(res, db().tenantPackages.filter((p) => p.status === 0).map((p) => ({ id: p.id, name: p.name }))))

// ---------- 1.2 风控预警 ----------
const tenantName = (id: number) => db().tenants.find((t) => t.id === id)?.name ?? ''
platformRouter.get('/farm/risk-alert/page', (req, res) => {
  const rows = db().riskAlerts.filter((a) => eqNum(a.tenantId, q(req, 'tenantId')) && (q(req, 'type') === undefined || a.type === q(req, 'type')) && eqNum(a.status, q(req, 'status')))
    .sort(byNewest).map((a) => ({ ...a, tenantName: tenantName(a.tenantId) }))
  return ok(res, page(req, rows))
})
platformRouter.put('/farm/risk-alert/handle', (req, res) => {
  const a = db().riskAlerts.find((x) => x.id === Number(req.body?.id))
  if (!a) return notFound(res, '预警')
  if (![10, 20].includes(Number(req.body.status))) return badRequest(res, '处理结果只能是已处理或已忽略')
  if (!String(req.body.handleRemark ?? '').trim()) return badRequest(res, '请填写处理说明')
  a.status = Number(req.body.status)
  a.handleRemark = String(req.body.handleRemark).trim()
  a.handleTime = Date.now()
  return ok(res, true)
})

platformRouter.get('/farm/platform/overview', (_req, res) => {
  const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime()
  const business = db().tenants.filter((t) => t.id !== 1)
  const gmvOf = (tid: number) =>
    db().orders.filter((o) => o.tenantId === tid && o.payTime && o.payTime >= monthStart).reduce((s, o) => s + o.amount, 0) +
    db().supplyOrders.filter((o) => o.tenantId === tid && o.createTime >= monthStart).reduce((s, o) => s + o.amount, 0)
  const rank = business.map((t) => ({ tenantId: t.id, tenantName: t.name, packageName: db().tenantPackages.find((p) => p.id === t.packageId)?.name ?? '', gmvMonth: gmvOf(t.id),
    adoptingCount: db().adoptions.filter((a) => a.tenantId === t.id && a.status === 10).length })).sort((a, b) => b.gmvMonth - a.gmvMonth)
  return ok(res, {
    tenantCount: business.length,
    activeTenantCount: business.filter((t) => t.status === 0).length,
    gmvMonth: rank.reduce((s, r) => s + r.gmvMonth, 0),
    orderCountMonth: db().orders.filter((o) => o.tenantId !== 1 && o.payTime && o.payTime >= monthStart).length,
    pendingAlertCount: db().riskAlerts.filter((a) => a.status === 0).length,
    feedbackPendingCount: db().feedbacks.filter((f) => f.status === 0).length,
    tenantRank: rank
  })
})
