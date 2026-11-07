import { Router, type Request } from 'express'
import { getDb, nextId } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { BIZ, q, byNewest, tenantOf, currentUser, notFound, badRequest, requireSteward, makeNo, maskMobile, CARE_MODE_NAME } from '../farm/common.js'
import { recordView, shipmentView } from '../farm/views.js'

/**
 * /admin-api/farm/steward/**（第二期补充）：配送确认、自管指导、我的记录、标的管理、农资代办、异常上报、消息、绩效。
 * 与 farm-steward.ts 同一前缀、另开一个 Router（app.ts 里再 use 一次，见 docs/plans/第二期-登记/管家端.md）。
 * 范围：管家只能看、只能做「自己负责的认养」（adoption.stewardId === 当前账号）。
 * 异常上报、消息、自管提醒三类是新数据，mock 里放在本文件的内存数组中（服务重启清空；Java 镜像落表）。
 */
export const farmStewardExtraRouter = Router()
farmStewardExtraRouter.use(requireLogin('admin'), requireSteward)

const db = getDb
const me = (req: Request) => currentUser(req)!
const DAY = 86400_000

/** 我负责的认养（进行中） */
const myAdoptions = (req: Request) => db().adoptions.filter((a) => a.tenantId === tenantOf(req) && a.stewardId === me(req).id)
const myAdoptionIds = (req: Request) => new Set(myAdoptions(req).map((a) => a.id))
const memberName = (id: number) => db().members.find((m) => m.id === id)?.nickname ?? ''
const assetCode = (id: number) => db().assets.find((a) => a.id === id)?.code ?? ''

// ================= 配送确认 =================
/** 返回：stats（待发货/已发货/已签收），pending = 我负责的、已录入收成但没发货的，list = 我负责的配送单 */
farmStewardExtraRouter.get('/shipment/list', (req, res) => {
  const ids = myAdoptionIds(req)
  const status = q(req, 'status')
  const all = db().shipments.filter((s) => s.tenantId === tenantOf(req) && ids.has(s.adoptionId)).sort(byNewest)
  const pending = db().harvests.filter((h) => h.tenantId === tenantOf(req) && ids.has(h.adoptionId) && !h.shipmentId).sort((a, b) => b.harvestDate - a.harvestDate)
    .map((h) => ({ harvestId: h.id, crop: h.crop, weightJin: h.weightJin, harvestDate: h.harvestDate, remark: h.remark,
      assetCode: assetCode(h.assetId), memberName: memberName(db().adoptions.find((a) => a.id === h.adoptionId)?.memberId ?? 0) }))
  const rows = all.filter((s) => status === undefined || s.status === Number(status)).map(shipmentView)
  return ok(res, {
    stats: { pending: pending.length, shipped: all.filter((s) => s.status === 10).length, done: all.filter((s) => s.status === 20).length },
    pending: status === undefined || status === '0' ? pending : [],
    list: status === '0' ? [] : rows
  })
})

/** 管家为自己负责的收成发货（复用后台 shipment 的建单口径） */
farmStewardExtraRouter.post('/shipment/create', (req, res) => {
  const t = tenantOf(req)
  const ids = myAdoptionIds(req)
  const h = db().harvests.find((x) => x.id === Number(req.body?.harvestId) && x.tenantId === t)
  if (!h) return badRequest(res, '请选择收成')
  if (!ids.has(h.adoptionId)) return fail(res, BIZ.NOT_MY_TASK, '只能处理自己负责的认养')
  if (h.shipmentId) return badRequest(res, '这批收成已经发过货了')
  if (!String(req.body.carrier ?? '').trim()) return badRequest(res, '请填写承运商')
  if (!String(req.body.logisticsNo ?? '').trim()) return badRequest(res, '请填写物流单号')
  const ad = db().adoptions.find((a) => a.id === h.adoptionId)!
  const o = db().orders.find((x) => x.id === ad.orderId)
  const m = db().members.find((x) => x.id === ad.memberId)!
  const base = db().bases.find((b) => b.id === db().assets.find((a) => a.id === ad.assetId)?.baseId)
  const now = Date.now()
  const s = { id: nextId(db().shipments), tenantId: t, no: makeNo('PS'), harvestId: h.id, adoptionId: ad.id, memberId: m.id,
    receiverName: o?.receiverName || m.nickname, receiverMobile: o?.receiverMobile || m.mobile, address: o?.address || m.address || '',
    weightJin: h.weightJin, carrier: String(req.body.carrier).trim(), logisticsNo: String(req.body.logisticsNo).trim(), coldChain: req.body.coldChain !== false, status: 10,
    tracks: [{ time: now, content: `${base?.name ?? '基地'}已打包，${req.body.carrier} 揽收` }, { time: now + 60_000, content: '运输中，冷链温度 3.8℃' }], createTime: now }
  db().shipments.push(s)
  h.shipmentId = s.id
  return ok(res, s.id)
})

/** 自提核销 / 确认签收：传 id（列表里点）或 no（扫码得到的配送单号） */
farmStewardExtraRouter.put('/shipment/sign', (req, res) => {
  const ids = myAdoptionIds(req)
  const b = req.body ?? {}
  const s = db().shipments.find((x) => x.tenantId === tenantOf(req) && (b.id !== undefined ? x.id === Number(b.id) : x.no === String(b.no ?? '').trim()))
  if (!s) return notFound(res, '配送单')
  if (!ids.has(s.adoptionId)) return fail(res, BIZ.NOT_MY_TASK, '只能处理自己负责的认养')
  if (s.status !== 10) return fail(res, BIZ.ORDER_STATE, '只有运输中的配送单能确认签收')
  s.status = 20
  s.tracks.push({ time: Date.now(), content: `管家核销签收，签收人：${s.receiverName}` })
  return ok(res, true)
})

// ================= 自管认养人指导 =================
/** 已提醒/已推送委托的任务：taskId -> 时间。演示数据，不入库 */
const reminded = new Map<number, { time: number, entrust: boolean }>()

farmStewardExtraRouter.get('/self-guide/list', (req, res) => {
  const ids = myAdoptionIds(req)
  const now = Date.now()
  const weekStart = now - 7 * DAY
  const tasks = db().tasks.filter((t) => t.tenantId === tenantOf(req) && t.owner === 'member' && t.adoptionId !== null && ids.has(t.adoptionId))
  const view = (t: typeof tasks[number]) => {
    const ad = db().adoptions.find((a) => a.id === t.adoptionId)!
    const r = reminded.get(t.id)
    // over 已逾期、todo 需提醒、reminded 已提醒（还没做）、done 已完成/已转委托
    const state = t.status !== 0 ? 'done' : t.deadline < now ? 'over' : r ? 'reminded' : 'todo'
    return { taskId: t.id, assetId: t.assetId, assetCode: assetCode(t.assetId), memberId: ad.memberId, memberName: memberName(ad.memberId),
      careMode: ad.careMode, careModeName: CARE_MODE_NAME[ad.careMode], title: t.title, description: t.description, deadline: t.deadline,
      status: t.status, state, entrustPrice: t.entrustPrice, remindedAt: r?.time ?? null, entrusted: t.status === 20 }
  }
  const all = tasks.map(view)
  const week = all.filter((x) => tasks.find((t) => t.id === x.taskId)!.deadline >= weekStart)
  const state = q(req, 'state') ?? 'todo' // todo 需提醒 / reminded 已提醒 / over 已逾期
  const list = all.filter((x) => x.state === state).sort((a, b) => a.deadline - b.deadline)
  return ok(res, {
    stats: {
      plots: new Set(tasks.map((t) => t.assetId)).size,
      weekTodo: week.filter((x) => x.status === 0).length,
      reminded: week.filter((x) => x.state === 'reminded').length,
      overdue: all.filter((x) => x.state === 'over').length
    },
    counts: { todo: all.filter((x) => x.state === 'todo').length, reminded: all.filter((x) => x.state === 'reminded').length, over: all.filter((x) => x.state === 'over').length },
    list
  })
})

/** 发送提醒 / 催一下 / 推送委托代办：都会给认养人留一条消息 */
farmStewardExtraRouter.post('/self-guide/remind', (req, res) => {
  const ids = myAdoptionIds(req)
  const t = db().tasks.find((x) => x.id === Number(req.body?.taskId) && x.tenantId === tenantOf(req))
  if (!t || t.owner !== 'member' || t.adoptionId === null) return notFound(res, '自管任务')
  if (!ids.has(t.adoptionId)) return fail(res, BIZ.NOT_MY_TASK, '只能提醒自己负责的认养人')
  if (t.status !== 0) return fail(res, BIZ.TASK_STATE, '任务状态已变化，请刷新')
  const entrust = req.body?.entrust === true
  const ad = db().adoptions.find((a) => a.id === t.adoptionId)!
  reminded.set(t.id, { time: Date.now(), entrust })
  const price = t.entrustPrice ?? 6000
  pushMessage(tenantOf(req), me(req).id, ad.memberId, t.assetId, 'steward',
    entrust ? `「${t.title}」已逾期，可点「委托代办」交给我处理（¥${(price / 100).toFixed(0)}）。`
      : `提醒：「${t.title}」${t.deadline < Date.now() ? '已逾期' : '请按时完成'}，有问题随时问我。`)
  return ok(res, true)
})

// ================= 我的记录 =================
farmStewardExtraRouter.get('/record/list', (req, res) => {
  const u = me(req)
  const status = q(req, 'status')
  const mine = db().records.filter((r) => r.tenantId === tenantOf(req) && r.stewardId === u.id).sort(byNewest)
  const rows = mine.filter((r) => status === undefined || r.status === Number(status))
  return ok(res, {
    stats: { all: mine.length, pending: mine.filter((r) => r.status === 0).length, published: mine.filter((r) => r.status === 10).length, rejected: mine.filter((r) => r.status === 20).length },
    list: rows.slice(0, 100).map((r) => ({ ...recordView(r), assetCode: assetCode(r.assetId) }))
  })
})

// ================= 标的管理 =================
/** scope：all / raising（在养）/ free（空闲·休整）。范围 = 我所在基地的全部标的 */
farmStewardExtraRouter.get('/asset/list', (req, res) => {
  const u = me(req)
  const scope = q(req, 'scope') ?? 'all'
  const now = Date.now()
  const assets = db().assets.filter((a) => a.tenantId === tenantOf(req) && a.baseId === u.baseId)
  const rows = assets.map((a) => {
    const ad = db().adoptions.find((x) => x.assetId === a.id && x.status === 10)
    const total = ad ? Math.max(1, ad.endAt - ad.startAt) : 0
    return {
      id: a.id, code: a.code, name: a.name, assetType: a.assetType, crop: a.specs.crop ?? a.specs.breed ?? '', areaM2: a.specs.areaM2 ?? null,
      raising: !!ad, statusName: ad ? '在养' : '空闲', memberName: ad ? memberName(ad.memberId) : '',
      mine: !!ad && ad.stewardId === u.id,
      progress: ad ? Math.min(100, Math.max(0, Math.round((now - ad.startAt) / total * 100))) : 0,
      harvestedJin: ad?.harvestedJin ?? 0, promisedJin: ad?.promisedJin ?? null,
      leftDays: ad ? Math.max(0, Math.ceil((ad.endAt - now) / DAY)) : 0
    }
  })
  const list = rows.filter((r) => scope === 'raising' ? r.raising : scope === 'free' ? !r.raising : true)
  return ok(res, { counts: { all: rows.length, raising: rows.filter((r) => r.raising).length, free: rows.filter((r) => !r.raising).length }, list })
})

// ================= 农资代办 =================
const SUPPLY_KIND: Record<string, string> = { tool: '上门安装', bio: '农场代施', pesticide: '农场代施', fert: '寄存 + 代施', seed: '寄存 + 代播' }
const SUPPLY_HINT: Record<string, string> = {
  tool: '认养人已付款，需 48h 内完成安装并拍照',
  bio: '限持证技术人员施用，施用后登记用量',
  pesticide: '限持证技术人员施用，施用后登记用量',
  fert: '整地时同步施入，需在打卡中登记用量',
  seed: '播种时同步使用，需在打卡中登记用量'
}
farmStewardExtraRouter.get('/supply-task/list', (req, res) => {
  const ids = myAdoptionIds(req)
  const tab = q(req, 'tab') === 'done' ? 'done' : 'todo'
  const all = db().supplyOrders.filter((o) => o.tenantId === tenantOf(req) && ids.has(o.adoptionId)).map((o) => {
    const spu = db().supplies.find((s) => s.id === o.spuId)
    const ad = db().adoptions.find((a) => a.id === o.adoptionId)!
    return { id: o.id, no: o.no, assetCode: assetCode(o.assetId), memberName: memberName(o.memberId), supplyName: `${spu?.name ?? ''}${o.qty > 1 ? ` ×${o.qty}` : ''}`,
      cat: spu?.cat ?? '', batchNo: o.batchNo, kind: SUPPLY_KIND[spu?.cat ?? ''] ?? '代办', careModeName: CARE_MODE_NAME[ad.careMode],
      hint: SUPPLY_HINT[spu?.cat ?? ''] ?? '', done: o.traceState === 10, usedAt: o.usedAt, dosage: o.dosage, createTime: o.createTime }
  }).filter((x) => x.cat !== 'ins' && x.cat !== 'svc').sort(byNewest)
  return ok(res, { counts: { todo: all.filter((x) => !x.done).length, done: all.filter((x) => x.done).length }, list: all.filter((x) => (tab === 'done') === x.done) })
})

/** 登记施用 / 安装（写入投入品台账，口径同后台 PUT /supply-order/trace，但只能登记自己负责的认养） */
farmStewardExtraRouter.put('/supply-task/apply', (req, res) => {
  const ids = myAdoptionIds(req)
  const o = db().supplyOrders.find((x) => x.id === Number(req.body?.id) && x.tenantId === tenantOf(req))
  if (!o) return notFound(res, '农资订单')
  if (!ids.has(o.adoptionId)) return fail(res, BIZ.NOT_MY_TASK, '只能处理自己负责的认养')
  if (!String(req.body.dosage ?? '').trim()) return badRequest(res, '请填写用量')
  if (o.traceState === 10) return fail(res, BIZ.TASK_STATE, '已经登记过了')
  o.traceState = 10
  o.usedAt = Number(req.body.usedAt) || Date.now()
  o.dosage = String(req.body.dosage).trim()
  const spu = db().supplies.find((s) => s.id === o.spuId)
  db().inputRecords.push({ id: nextId(db().inputRecords), tenantId: o.tenantId, assetId: o.assetId, source: 'farm', name: spu?.name ?? '', batchNo: o.batchNo,
    usedAt: o.usedAt, dosage: o.dosage, operator: me(req).nickname })
  return ok(res, true)
})

// ================= 异常上报 =================
export const ALARM_TYPES = ['设备异常', '损耗登记', '气象灾害', '病虫害爆发'] as const
interface Alarm { id: number, tenantId: number, stewardId: number, type: string, level: '高' | '中', assetId: number | null, title: string, content: string, qty: number | null,
  photos: string[], insuranceReported: boolean, status: number, createTime: number }
const alarms: Alarm[] = []

farmStewardExtraRouter.get('/alarm/list', (req, res) => {
  const rows = alarms.filter((a) => a.tenantId === tenantOf(req) && a.stewardId === me(req).id).sort(byNewest)
  return ok(res, rows.map((a) => ({ ...a, assetCode: a.assetId ? assetCode(a.assetId) : '' })))
})
farmStewardExtraRouter.post('/alarm/create', (req, res) => {
  const b = req.body ?? {}
  if (!(ALARM_TYPES as readonly string[]).includes(b.type)) return badRequest(res, '请选择异常类型')
  const content = String(b.content ?? '').trim()
  if (!content) return badRequest(res, '请填写情况说明')
  const photos: string[] = Array.isArray(b.photos) ? b.photos.filter(Boolean) : []
  const needPhoto = b.type !== '设备异常'
  if (needPhoto && photos.length === 0) return badRequest(res, '损耗、灾害、病虫害必须留现场影像')
  if (photos.length > 9) return badRequest(res, '最多上传 9 张照片')
  let assetId: number | null = null
  if (b.assetId) {
    const a = db().assets.find((x) => x.id === Number(b.assetId) && x.tenantId === tenantOf(req) && x.baseId === me(req).baseId)
    if (!a) return badRequest(res, '请选择本基地的标的')
    assetId = a.id
  }
  const qty = b.qty === undefined || b.qty === '' ? null : Number(b.qty)
  if (b.type === '损耗登记' && (qty === null || !(qty > 0))) return badRequest(res, '请填写损耗数量')
  const a: Alarm = { id: nextId(alarms), tenantId: tenantOf(req), stewardId: me(req).id, type: b.type, level: b.type === '气象灾害' || b.type === '病虫害爆发' ? '高' : '中',
    assetId, title: `${b.type}${assetId ? ' · ' + assetCode(assetId) : ''}`, content, qty, photos,
    insuranceReported: b.type === '损耗登记' || b.type === '气象灾害', status: 0, createTime: Date.now() }
  alarms.push(a)
  return ok(res, a.id)
})

// ================= 消息 / 聊天 =================
interface Msg { id: number, tenantId: number, stewardId: number, memberId: number, assetId: number | null, from: 'steward' | 'member', content: string, createTime: number }
const messages: Msg[] = []
function pushMessage(tenantId: number, stewardId: number, memberId: number, assetId: number | null, from: Msg['from'], content: string) {
  messages.push({ id: nextId(messages), tenantId, stewardId, memberId, assetId, from, content, createTime: Date.now() })
}

farmStewardExtraRouter.get('/message/conversations', (req, res) => {
  const u = me(req)
  const byMember = new Map<number, { memberId: number, assetCode: string }>()
  for (const a of myAdoptions(req).filter((x) => x.status === 10)) if (!byMember.has(a.memberId)) byMember.set(a.memberId, { memberId: a.memberId, assetCode: assetCode(a.assetId) })
  const list = [...byMember.values()].map((c) => {
    const ms = messages.filter((m) => m.tenantId === u.tenantId && m.stewardId === u.id && m.memberId === c.memberId).sort((a, b) => b.createTime - a.createTime)
    const m = db().members.find((x) => x.id === c.memberId)
    return { memberId: c.memberId, memberName: m?.nickname ?? '', assetCode: c.assetCode, last: ms[0]?.content ?? '', lastTime: ms[0]?.createTime ?? null, unread: 0 }
  }).sort((a, b) => (b.lastTime ?? 0) - (a.lastTime ?? 0))
  return ok(res, { list, notices: 0 })
})
farmStewardExtraRouter.get('/message/list', (req, res) => {
  const u = me(req)
  const memberId = Number(req.query.memberId)
  if (![...myAdoptions(req)].some((a) => a.memberId === memberId)) return fail(res, BIZ.NOT_MY_TASK, '只能和自己负责的认养人聊天')
  const m = db().members.find((x) => x.id === memberId)
  const ad = myAdoptions(req).find((a) => a.memberId === memberId && a.status === 10)
  return ok(res, {
    member: { id: memberId, name: m?.nickname ?? '', mobile: maskMobile(m?.mobile), assetCode: ad ? assetCode(ad.assetId) : '' },
    list: messages.filter((x) => x.tenantId === u.tenantId && x.stewardId === u.id && x.memberId === memberId).sort((a, b) => a.createTime - b.createTime)
  })
})
farmStewardExtraRouter.post('/message/send', (req, res) => {
  const u = me(req)
  const memberId = Number(req.body?.memberId)
  const content = String(req.body?.content ?? '').trim()
  const ad = myAdoptions(req).find((a) => a.memberId === memberId && a.status === 10)
  if (!ad) return fail(res, BIZ.NOT_MY_TASK, '只能和自己负责的认养人聊天')
  if (!content) return badRequest(res, '请输入内容')
  if (content.length > 500) return badRequest(res, '一条消息最多 500 字')
  pushMessage(u.tenantId, u.id, memberId, ad.assetId, 'steward', content)
  return ok(res, true)
})

// ================= 绩效 =================
farmStewardExtraRouter.get('/performance', (req, res) => {
  const u = me(req)
  const now = Date.now()
  const d = new Date()
  const monthStart = new Date(d.getFullYear(), d.getMonth(), 1).getTime()
  const mineAd = myAdoptions(req).filter((a) => a.status === 10)
  const tasks = db().tasks.filter((t) => t.stewardId === u.id && t.owner === 'steward')
  const due = tasks.filter((t) => t.deadline >= monthStart && t.deadline <= now)
  const doneInTime = due.filter((t) => t.status === 10 && (t.finishTime ?? 0) <= t.deadline)
  const records = db().records.filter((r) => r.stewardId === u.id && r.createTime >= monthStart)
  const promised = mineAd.reduce((s, a) => s + (a.promisedJin ?? 0), 0)
  const harvested = mineAd.reduce((s, a) => s + a.harvestedJin, 0)
  const completionRate = due.length ? Math.round(doneInTime.length / due.length * 100) : null
  return ok(res, {
    stewardName: u.nickname, baseName: db().bases.find((b) => b.id === u.baseId)?.name ?? '', month: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
    assetCount: mineAd.length, memberCount: new Set(mineAd.map((a) => a.memberId)).size, monthCheckins: records.length,
    completionRate, passRate: records.length ? Math.round(records.filter((r) => r.status === 10).length / records.length * 100) : null,
    outputRate: promised > 0 ? Math.round(harvested / promised * 100) : null,
    // 响应时长、满意度、绩效分：系统没有这类数据，原型里的数字不编造
    responseMinutes: null, satisfaction: null, score: null
  })
})
