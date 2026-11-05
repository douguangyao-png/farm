import { Router, type Request } from 'express'
import { getDb, nextId } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { BIZ, q, byNewest, tenantOf, currentUser, notFound, badRequest, requireSteward, envView, envSnapshot, maskMobile, CARE_MODE_NAME } from '../farm/common.js'
import { taskView, serviceOrderView, recordView } from '../farm/views.js'

/** /admin-api/farm/steward/**：管家端（契约第 3 节）。只能看、只能做分给自己的。 */
export const farmStewardRouter = Router()
farmStewardRouter.use(requireLogin('admin'), requireSteward)

const db = getDb
const me = (req: Request) => currentUser(req)!
const DAY = 86400_000

farmStewardRouter.get('/home', (req, res) => {
  const u = me(req)
  const startToday = new Date(new Date().toDateString()).getTime()
  const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime()
  const mine = db().tasks.filter((t) => t.stewardId === u.id)
  return ok(res, {
    stewardName: u.nickname,
    baseName: db().bases.find((b) => b.id === u.baseId)?.name ?? '',
    todo: {
      taskCount: mine.filter((t) => t.status === 0).length,
      overdueCount: mine.filter((t) => t.status === 0 && t.deadline < Date.now()).length,
      serviceOrderPending: poolOf(req).length,
      serviceOrderMine: db().serviceOrders.filter((s) => s.stewardId === u.id && s.status === 10).length
    },
    todayDone: mine.filter((t) => t.status === 10 && (t.finishTime ?? 0) >= startToday).length,
    monthDone: mine.filter((t) => t.status === 10 && (t.finishTime ?? 0) >= monthStart).length,
    pendingAuditCount: db().records.filter((r) => r.stewardId === u.id && r.status === 0).length,
    rejectedCount: db().records.filter((r) => r.stewardId === u.id && r.status === 20 && (r.auditTime ?? 0) > Date.now() - 7 * DAY).length
  })
})

// ---------- 任务 ----------
farmStewardRouter.get('/task/list', (req, res) => {
  const u = me(req)
  const status = q(req, 'status') === undefined ? 0 : Number(q(req, 'status'))
  const rows = db().tasks.filter((t) => t.stewardId === u.id && t.status === status).map(taskView)
    .sort((a, b) => (status === 0 ? a.deadline - b.deadline : (b.finishTime ?? 0) - (a.finishTime ?? 0)))
  return ok(res, rows)
})
farmStewardRouter.get('/task/get', (req, res) => {
  const t = db().tasks.find((x) => x.id === Number(req.query.id) && x.tenantId === tenantOf(req))
  if (!t) return notFound(res, '任务')
  if (t.stewardId !== me(req).id) return fail(res, BIZ.NOT_MY_TASK, '只能处理分配给自己的任务')
  const a = db().assets.find((x) => x.id === t.assetId)!
  return ok(res, { ...taskView(t), asset: { id: a.id, code: a.code, name: a.name, crop: a.specs.crop ?? a.specs.breed ?? '', areaM2: a.specs.areaM2 ?? null, cameraName: a.cameraName }, env: envView(a).current })
})
farmStewardRouter.post('/task/checkin', (req, res) => {
  const u = me(req)
  const b = req.body ?? {}
  const t = db().tasks.find((x) => x.id === Number(b.taskId) && x.tenantId === tenantOf(req))
  if (!t) return notFound(res, '任务')
  if (t.stewardId !== u.id) return fail(res, BIZ.NOT_MY_TASK, '只能处理分配给自己的任务')
  if (t.status !== 0) return fail(res, BIZ.TASK_STATE, '任务状态已变化，请刷新')
  const photos: string[] = Array.isArray(b.photos) ? b.photos.filter(Boolean) : []
  if (photos.length === 0) return badRequest(res, '至少拍一张现场照片')
  if (photos.length > 9) return badRequest(res, '最多上传 9 张照片')
  const a = db().assets.find((x) => x.id === t.assetId)!
  const base = db().bases.find((x) => x.id === a.baseId)
  const lng = Number(b.lng) || base?.lng || 0
  const lat = Number(b.lat) || base?.lat || 0
  // GPS 围栏：离基地中心 2 公里以内算在基地里（演示用简化算法）
  const inFence = !!base && Math.hypot((lng - base.lng) * 96, (lat - base.lat) * 111) <= 2
  const now = Date.now()
  const record = {
    id: nextId(db().records), tenantId: t.tenantId, adoptionId: t.adoptionId, assetId: t.assetId, stewardId: u.id, taskId: t.id,
    kind: (t.serviceOrderId ? 'service' : 'farming') as 'service' | 'farming',
    title: t.title.replace(/^[A-Z]-\d+\s*/, ''), content: String(b.content ?? '').trim() || t.description, photos, lng, lat,
    env: envSnapshot(a), checks: { exifTime: true, gpsFence: inFence, duplicate: true }, status: 0, auditRemark: null, createTime: now, auditTime: null
  }
  db().records.push(record)
  t.status = 10
  t.finishTime = now
  if (t.serviceOrderId) {
    const so = db().serviceOrders.find((s) => s.id === t.serviceOrderId)
    if (so) { so.status = 20; so.finishTime = now; so.proofPhotos = photos }
  }
  return ok(res, { recordId: record.id, checks: record.checks, env: record.env })
})

// ---------- 代管工单 ----------
function poolOf(req: Request) {
  const u = me(req)
  return db().serviceOrders.filter((s) => s.tenantId === u.tenantId && s.status === 0 && db().assets.find((a) => a.id === s.assetId)?.baseId === u.baseId)
}
farmStewardRouter.get('/service-order/list', (req, res) => {
  const u = me(req)
  const rows = q(req, 'scope') === 'mine'
    ? db().serviceOrders.filter((s) => s.stewardId === u.id)
    : poolOf(req)
  return ok(res, rows.slice().sort(byNewest).map(serviceOrderView))
})
farmStewardRouter.put('/service-order/accept', (req, res) => {
  const u = me(req)
  const so = db().serviceOrders.find((s) => s.id === Number(req.body?.id) && s.tenantId === u.tenantId)
  if (!so) return notFound(res, '代管工单')
  if (so.status !== 0) return fail(res, BIZ.TASK_STATE, '工单已被接走，请刷新')
  const now = Date.now()
  so.status = 10
  so.stewardId = u.id
  so.acceptTime = now
  const a = db().assets.find((x) => x.id === so.assetId)!
  db().tasks.push({ id: nextId(db().tasks), tenantId: so.tenantId, adoptionId: so.adoptionId, assetId: so.assetId, owner: 'steward', stewardId: u.id,
    title: `${a.code} ${so.serviceType}（代管工单）`, description: so.remark || `认养人委托代办：${so.taskTitle}`, source: 'service', serviceOrderId: so.id,
    deadline: now + DAY, status: 0, entrustPrice: null, finishTime: null, createTime: now })
  return ok(res, true)
})

// ---------- 标的详情 ----------
farmStewardRouter.get('/asset/get', (req, res) => {
  const a = db().assets.find((x) => x.id === Number(req.query.id) && x.tenantId === tenantOf(req))
  if (!a) return notFound(res, '标的')
  const ad = db().adoptions.find((x) => x.assetId === a.id && x.status === 10)
  const m = ad ? db().members.find((x) => x.id === ad.memberId) : undefined
  const weekAgo = Date.now() - 7 * DAY
  const selfTasks = ad ? db().tasks.filter((t) => t.adoptionId === ad.id && t.owner === 'member' && t.deadline >= weekAgo) : []
  return ok(res, {
    asset: { ...a, baseName: db().bases.find((b) => b.id === a.baseId)?.name ?? '' },
    adoption: ad ? { id: ad.id, careMode: ad.careMode, careModeName: CARE_MODE_NAME[ad.careMode], startAt: ad.startAt, endAt: ad.endAt, promisedJin: ad.promisedJin, harvestedJin: ad.harvestedJin } : null,
    member: m ? { name: m.nickname, mobile: maskMobile(m.mobile) } : null,
    env: envView(a),
    records: db().records.filter((r) => r.assetId === a.id).sort(byNewest).slice(0, 5).map(recordView),
    selfTaskWeek: { total: selfTasks.length, done: selfTasks.filter((t) => t.status === 10).length, entrusted: selfTasks.filter((t) => t.status === 20).length,
      overdue: selfTasks.filter((t) => t.status === 0 && t.deadline < Date.now()).length }
  })
})
