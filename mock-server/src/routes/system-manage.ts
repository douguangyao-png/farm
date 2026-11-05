import { Router, type Request } from 'express'
import { getDb } from '../store.js'
import { ok, fail, ERR, requireLogin } from '../http.js'

/**
 * 芋道自带的「系统管理」列表页（用户、角色、字典）的只读接口。
 * 演示只需要能看到数据；新增、修改、删除走兜底的 501「演示环境暂不支持该操作」。
 * 按登录账号的租户过滤（芋道原生行为：平台管理员在系统管理里也只看平台自己的用户和角色）。
 */
export const systemManageRouter = Router()
systemManageRouter.use(['/role', '/user', '/dict-type', '/dict-data/page'], requireLogin('admin'))

/** 演示数据统一用一个固定的创建时间，避免每次重启都变 */
const CREATED = new Date('2026-10-08T09:30:00+08:00').getTime()

function page<T>(req: Request, rows: T[]) {
  const pageNo = Math.max(1, Number(req.query.pageNo ?? 1) || 1)
  const pageSize = Math.min(200, Math.max(1, Number(req.query.pageSize ?? 10) || 10))
  return { list: rows.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: rows.length }
}
const like = (v: unknown, q: unknown) => !q || String(v ?? '').includes(String(q))
const eqStatus = (v: number, q: unknown) => q === undefined || q === '' || Number(q) === v

// ---------- 角色 ----------
function roleView(r: ReturnType<typeof getDb>['roles'][number]) {
  return {
    id: r.id, name: r.name, code: r.code,
    sort: r.id, status: 0,
    type: r.code === 'super_admin' || r.code === 'tenant_admin' ? 1 : 2, // 1 内置 2 自定义
    remark: r.code === 'steward' ? '只能登录管家端，处理分配给自己的地块任务' : '',
    dataScope: 1, dataScopeDeptIds: [],
    createTime: CREATED
  }
}
const tenantRoles = (req: Request) => getDb().roles.filter((r) => r.tenantId === req.session!.tenantId)

systemManageRouter.get('/role/page', (req, res) =>
  ok(res, page(req, tenantRoles(req).filter((r) => like(r.name, req.query.name) && like(r.code, req.query.code)).map(roleView)
    .filter((r) => eqStatus(r.status, req.query.status))))
)
systemManageRouter.get('/role/simple-list', (req, res) => ok(res, tenantRoles(req).map((r) => ({ id: r.id, name: r.name }))))
systemManageRouter.get('/role/get', (req, res) => {
  const r = tenantRoles(req).find((x) => x.id === Number(req.query.id))
  return r ? ok(res, roleView(r)) : fail(res, ERR.NOT_FOUND, '角色不存在')
})

// ---------- 用户 ----------
function userView(u: ReturnType<typeof getDb>['users'][number]) {
  const tenant = getDb().tenants.find((t) => t.id === u.tenantId)
  return {
    id: u.id, username: u.username, nickname: u.nickname, avatar: u.avatar ?? '',
    mobile: u.mobile ?? '', email: '', sex: 0, status: 0,
    deptId: u.deptId ?? null, deptName: tenant?.name ?? '', postIds: [], roleIds: u.roleIds,
    remark: u.kind === 'steward' ? '管家' : '', loginIp: '', loginDate: null, createTime: CREATED
  }
}
const tenantUsers = (req: Request) => getDb().users.filter((u) => u.tenantId === req.session!.tenantId)

systemManageRouter.get('/user/page', (req, res) =>
  ok(res, page(req, tenantUsers(req).map(userView)
    .filter((u) => like(u.username, req.query.username) && like(u.mobile, req.query.mobile) && eqStatus(u.status, req.query.status))))
)
systemManageRouter.get('/user/simple-list', (req, res) =>
  ok(res, tenantUsers(req).map((u) => ({ id: u.id, nickname: u.nickname, deptId: u.deptId ?? null })))
)
systemManageRouter.get('/user/get', (req, res) => {
  const u = tenantUsers(req).find((x) => x.id === Number(req.query.id))
  return u ? ok(res, userView(u)) : fail(res, ERR.NOT_FOUND, '用户不存在')
})

// ---------- 字典（全局共享，不分租户，和芋道一致） ----------
systemManageRouter.get('/dict-type/page', (req, res) =>
  ok(res, page(req, (getDb().dictTypes ?? [])
    .filter((t) => like(t.name, req.query.name) && like(t.type, req.query.type) && eqStatus(t.status, req.query.status))
    .map((t) => ({ ...t, createTime: CREATED }))))
)
systemManageRouter.get('/dict-type/simple-list', (_req, res) =>
  ok(res, (getDb().dictTypes ?? []).map((t) => ({ id: t.id, name: t.name, type: t.type })))
)
systemManageRouter.get('/dict-type/get', (req, res) => {
  const t = (getDb().dictTypes ?? []).find((x) => x.id === Number(req.query.id))
  return t ? ok(res, { ...t, createTime: CREATED }) : fail(res, ERR.NOT_FOUND, '字典类型不存在')
})
systemManageRouter.get('/dict-data/page', (req, res) =>
  ok(res, page(req, (getDb().dictData ?? [])
    .map((d, i) => ({ id: i + 1, sort: i + 1, status: 0, remark: '', createTime: CREATED, ...d }))
    .filter((d) => (!req.query.dictType || d.dictType === req.query.dictType) && like(d.label, req.query.label) && eqStatus(d.status, req.query.status))))
)

// ---------- 个人中心（芋道「我的」/ 个人信息页） ----------
systemManageRouter.get('/user/profile/get', (req, res) => {
  const u = getDb().users.find((x) => x.id === req.session!.userId)
  if (!u) return fail(res, ERR.NOT_FOUND, '用户不存在')
  const tenant = getDb().tenants.find((t) => t.id === u.tenantId)
  return ok(res, {
    ...userView(u),
    roles: getDb().roles.filter((r) => u.roleIds.includes(r.id)).map((r) => ({ id: r.id, name: r.name })),
    dept: { id: u.tenantId, name: tenant?.name ?? '' },
    posts: [],
    socialUsers: []
  })
})
