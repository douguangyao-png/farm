import { Router } from 'express'
import { getDb, type Menu, type User } from '../store.js'
import { ok, fail, ERR, createSession, dropSession, findByRefresh, requireLogin } from '../http.js'

/** /admin-api/system/**：登录、权限菜单、租户、字典。后台和管家端共用。 */
export const systemRouter = Router()

const EXPIRES_MS = 30 * 60 * 1000

function tokenPayload(s: { userId: number; token: string; refreshToken: string }) {
  return { userId: s.userId, accessToken: s.token, refreshToken: s.refreshToken, expiresTime: Date.now() + EXPIRES_MS }
}

/** 菜单平铺列表 → 树（芋道 get-permission-info 返回树形 menus） */
function buildTree(menus: Menu[]) {
  const byParent = new Map<number, Menu[]>()
  for (const m of menus) byParent.set(m.parentId, [...(byParent.get(m.parentId) ?? []), m])
  const walk = (pid: number): unknown[] =>
    (byParent.get(pid) ?? []).map((m) => {
      const children = walk(m.id)
      return { ...m, children: children.length ? children : null }
    })
  return walk(0)
}

function userMenus(u: User) {
  const db = getDb()
  const roles = db.roles.filter((r) => u.roleIds.includes(r.id))
  const all = roles.some((r) => r.menuIds === '*')
  const ids = new Set(roles.flatMap((r) => (r.menuIds === '*' ? [] : r.menuIds)))
  const menus = db.menus.filter((m) => all || ids.has(m.id))
  return { roles, menus, all }
}

// ---------- 租户 ----------
systemRouter.get('/tenant/get-id-by-name', (req, res) => {
  const t = getDb().tenants.find((x) => x.name === req.query.name)
  return ok(res, t?.id ?? null)
})

/**
 * 按用户名找租户（用户 2026-11-05：登录页不选租户，只填用户名密码，登录后进对应平台）。
 * 前提：用户名全平台唯一（开通租户时校验）。返回租户 id，找不到返回 null，前端提示账号或密码不正确。
 * 真后端实现时建议把这一步放进登录接口内部，避免单独暴露「用户名 → 租户」查询。
 */
systemRouter.get('/tenant/get-id-by-username', (req, res) => {
  const u = getDb().users.find((x) => x.username === String(req.query.username ?? '').trim())
  return ok(res, u?.tenantId ?? null)
})

systemRouter.get('/tenant/get-by-website', (req, res) => {
  const w = String(req.query.website ?? '')
  const t = getDb().tenants.find((x) => x.websites?.includes(w))
  return ok(res, t ? { id: t.id, name: t.name } : null)
})

systemRouter.get('/tenant/simple-list', (_req, res) =>
  ok(res, getDb().tenants.filter((t) => t.status === 0).map((t) => ({ id: t.id, name: t.name })))
)

// ---------- 登录 ----------
systemRouter.post('/auth/login', (req, res) => {
  const { username, password } = req.body ?? {}
  const tenantId = req.tenantId
  if (!tenantId || !getDb().tenants.some((t) => t.id === tenantId)) return fail(res, ERR.TENANT_NOT_EXISTS, '租户不存在')
  const u = getDb().users.find((x) => x.tenantId === tenantId && x.username === username)
  if (!u || u.password !== password) return fail(res, ERR.AUTH_LOGIN_BAD_CREDENTIALS, '登录失败，账号密码不正确')
  return ok(res, tokenPayload(createSession('admin', u.id, u.tenantId)))
})

systemRouter.post('/auth/refresh-token', (req, res) => {
  const s = findByRefresh(String(req.query.refreshToken ?? ''))
  if (!s) return fail(res, ERR.UNAUTHORIZED, '刷新令牌已过期')
  dropSession(s.token)
  return ok(res, tokenPayload(createSession(s.scope, s.userId, s.tenantId)))
})

systemRouter.post('/auth/logout', (req, res) => {
  dropSession(req.session?.token)
  return ok(res, true)
})

systemRouter.get('/auth/get-permission-info', requireLogin('admin'), (req, res) => {
  const u = getDb().users.find((x) => x.id === req.session!.userId)
  if (!u) return fail(res, ERR.UNAUTHORIZED, '账号未登录')
  const { roles, menus, all } = userMenus(u)
  // 演示：按钮权限全开（芋道页面的新增、编辑按钮靠 v-hasPermi 控制显示），看得到哪些页面由菜单决定。
  // 真后端按角色返回权限标识。
  const permissions = all ? ['*:*:*'] : ['*:*:*', ...menus.map((m) => m.permission).filter(Boolean)]
  return ok(res, {
    user: { id: u.id, nickname: u.nickname, avatar: u.avatar ?? '', deptId: u.deptId ?? null, username: u.username },
    roles: roles.map((r) => r.code),
    permissions,
    menus: buildTree(menus.filter((m) => !m.permission))
  })
})

// ---------- 字典、通知 ----------
systemRouter.get('/dict-data/simple-list', (_req, res) => ok(res, getDb().dictData ?? []))
systemRouter.get('/notify-message/get-unread-count', requireLogin('admin'), (_req, res) => ok(res, 0))
systemRouter.get('/notify-message/get-unread-list', requireLogin('admin'), (_req, res) => ok(res, []))
