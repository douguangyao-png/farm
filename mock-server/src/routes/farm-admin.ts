import { Router } from 'express'
import { getDb, nextId, type Product, type CareModeConfig, type Supply } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { findBannedWords, bannedMessage, SUGGESTION } from '../compliance.js'
import {
  BIZ, page, q, like, eqNum, byNewest, tenantOf, own, notFound, badRequest, currentUser, envView, makeNo, CARE_MODE_NAME, maskMobile
} from '../farm/common.js'
import {
  productView, CATEGORY_NAME, assetView, orderView, contractOf, adoptionView, recordView, serviceOrderView, supplyOrderView, harvestView, shipmentView
} from '../farm/views.js'
import { serviceOrderPage, supplyPage, supplyOrderPage, harvestPage, shipmentPage } from '../farm/stats.js'

/** /admin-api/farm/**：经营者后台（契约第 2 节） */
export const farmAdminRouter = Router()
farmAdminRouter.use(requireLogin('admin'))

const db = getDb
const DAY = 86400_000

// ---------- 2.1 工作台 ----------
/** 工作台曲线的演示底数（每天 ¥1,800–¥5,600），叠加真实下单金额；见 docs/demo/演示数据说明.md */
function baselineGmv(tenantId: number, dayIndex: number) {
  if (tenantId !== 2) return 0
  return Math.round((3700 + Math.sin(dayIndex * 1.7) * 1900) / 10) * 1000
}

farmAdminRouter.get('/dashboard/summary', (req, res) => {
  const t = tenantOf(req)
  const now = Date.now()
  const startToday = new Date(new Date().toDateString()).getTime()
  const paid = db().orders.filter((o) => o.tenantId === t && o.payTime)
  const supplyPaid = db().supplyOrders.filter((o) => o.tenantId === t)
  const incomeOn = (from: number, to: number) =>
    paid.filter((o) => o.payTime! >= from && o.payTime! < to).reduce((s, o) => s + o.amount, 0) +
    supplyPaid.filter((o) => o.createTime >= from && o.createTime < to).reduce((s, o) => s + o.amount, 0)
  const paidCountOn = (from: number, to: number) => paid.filter((o) => o.payTime! >= from && o.payTime! < to).length
  // 近 30 天曲线（原型 08 dash：近 30 天认养 GMV）
  const gmvTrend = Array.from({ length: 30 }, (_, i) => {
    const from = startToday - (29 - i) * DAY
    const d = new Date(from)
    const dayIndex = Math.floor(from / DAY)
    return { date: `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`, amount: baselineGmv(t, dayIndex) + incomeOn(from, from + DAY) }
  })
  const gmvPeak = gmvTrend.reduce((m, x) => (x.amount > m.amount ? x : m), gmvTrend[0])
  const gmvToday = gmvTrend[29].amount
  const gmvYesterday = gmvTrend[28].amount
  const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime()
  const assets = db().assets.filter((a) => a.tenantId === t && a.status !== 20)
  const adoptions = db().adoptions.filter((a) => a.tenantId === t && a.status === 10)
  const pendingRecordCount = db().records.filter((r) => r.tenantId === t && r.status === 0).length
  const pendingServiceOrderCount = db().serviceOrders.filter((s) => s.tenantId === t && s.status === 0).length
  const overdueSelfTaskCount = db().tasks.filter((x) => x.tenantId === t && x.owner === 'member' && x.status === 0 && x.deadline < now).length
  const unshipped = db().harvests.filter((h) => h.tenantId === t && !h.shipmentId).length
  const untraced = db().supplyOrders.filter((o) => o.tenantId === t && o.traceState === 0).length
  const risk = selfCheck(t)
  const riskBadCount = risk.items.filter((x) => x.status !== 'ok').length
  const ordersLast7d = paidCountOn(startToday - 6 * DAY, startToday + DAY)
  const ordersPrev7d = paidCountOn(startToday - 13 * DAY, startToday - 6 * DAY)
  // 在养认养人 = 有进行中认养的不同认养人；续费 / 复购率 = 已支付订单 ≥2 笔的认养人 / 有已支付订单的认养人
  const adoptingMemberCount = new Set(adoptions.map((a) => a.memberId)).size
  const orderCountByMember = new Map<number, number>()
  for (const o of paid) orderCountByMember.set(o.memberId, (orderCountByMember.get(o.memberId) ?? 0) + 1)
  const payers = orderCountByMember.size
  const repurchaseRate = payers ? Math.round((Array.from(orderCountByMember.values()).filter((n) => n >= 2).length / payers) * 1000) / 10 : 0
  const slotName: Record<string, { name: string; unit: string }> = {
    plot: { name: CATEGORY_NAME.plot, unit: '位' }, animal: { name: CATEGORY_NAME.animal, unit: '只' }, tree: { name: CATEGORY_NAME.tree, unit: '棵' }
  }
  const slotUsage = (['plot', 'animal', 'tree'] as const).map((type) => {
    const list = assets.filter((a) => a.assetType === type)
    return { type, name: slotName[type].name, unit: slotName[type].unit, used: list.filter((a) => a.status === 10).length, total: list.length }
  })
  const weekFrom = startToday - 6 * DAY
  const weekPaid = paid.filter((o) => o.payTime! >= weekFrom)
  const weekCategories = (['plot', 'animal', 'tree'] as const).map((type) => ({
    name: CATEGORY_NAME[type],
    count: weekPaid.filter((o) => db().products.find((p) => p.id === o.productId)?.assetType === type).length
  }))
  const latestOrders = db().orders.filter((o) => o.tenantId === t).sort(byNewest).slice(0, 5).map(orderView)
  return ok(res, {
    gmvToday,
    gmvYesterday,
    gmvDayOnDayRate: gmvYesterday ? Math.round(((gmvToday - gmvYesterday) / gmvYesterday) * 1000) / 10 : null,
    gmvMonth: Array.from({ length: Math.round((startToday - monthStart) / DAY) + 1 }, (_, i) => monthStart + i * DAY)
      .reduce((s, from) => s + baselineGmv(t, Math.floor(from / DAY)) + incomeOn(from, from + DAY), 0),
    gmvPeak,
    orderCountToday: paidCountOn(startToday, startToday + DAY),
    orderCountYesterday: paidCountOn(startToday - DAY, startToday),
    orderCountMonth: paid.filter((o) => o.payTime! >= monthStart).length,
    orderCountLast7d: ordersLast7d,
    orderCountLast7dRate: ordersPrev7d ? Math.round(((ordersLast7d - ordersPrev7d) / ordersPrev7d) * 1000) / 10 : null,
    pendingShipCount: unshipped,
    adoptingCount: adoptions.length,
    adoptingMemberCount,
    repurchaseRate,
    assetTotal: assets.length,
    assetAdopted: assets.filter((a) => a.status === 10).length,
    pendingRecordCount, pendingServiceOrderCount, overdueSelfTaskCount,
    riskScore: risk.score,
    gmvTrend,
    careModeShare: (['full', 'semi', 'self'] as const).map((mode) => ({ mode, name: CARE_MODE_NAME[mode], count: adoptions.filter((a) => a.careMode === mode).length })),
    slotUsage,
    latestOrders,
    weekOverview: {
      categories: weekCategories,
      avgOrderAmount: paid.length ? Math.round(paid.reduce((s, o) => s + o.amount, 0) / paid.length) : 0,
      repurchaseRate,
      refundRate: null
    },
    // level：danger 红点（风控）/ primary 绿点 / warning 橙点；desc 是一句话说明
    todos: [
      { type: 'record', title: '农事记录待审核', count: pendingRecordCount, desc: '管家上传记录需复核后发布到 C 端', level: 'warning', path: '/farm-work/record' },
      { type: 'service', title: '代管工单待接单', count: pendingServiceOrderCount, desc: '工单池里还没有管家接单', level: 'warning', path: '/farm-supply/service-order' },
      { type: 'harvest', title: '收成待发货', count: unshipped, desc: '已录入收成但还没生成配送单', level: 'primary', path: '/farm-order/shipment' },
      { type: 'trace', title: '农资待登记施用', count: untraced, desc: '自购农资未登记批次和施用时间，地块档案会标「待补充」', level: 'warning', path: '/farm-supply/supply-order' },
      { type: 'risk', title: '风险自检待改进', count: riskBadCount, desc: '合规自检有未达标项，建议尽快处理', level: 'danger', path: '/farm-compliance/self-check' }
    ],
    todayRecords: db().records.filter((r) => r.tenantId === t && r.createTime >= startToday - DAY).sort(byNewest).slice(0, 6).map(recordView),
    demo: true
  })
})

// ---------- 2.1 风险自检 ----------
function selfCheck(t: number) {
  const bases = db().bases.filter((b) => b.tenantId === t)
  const certOk = bases.filter((b) => b.certNo).length
  const paid = db().orders.filter((o) => o.tenantId === t && o.payTime)
  const signed = paid.filter((o) => o.contractAgreed).length
  const hits = db().complianceHits.filter((h) => h.tenantId === t && h.time > Date.now() - 30 * DAY)
  const so = db().supplyOrders.filter((o) => o.tenantId === t)
  const traced = so.filter((o) => o.traceState === 10).length
  const ratio = (a: number, b: number) => (b === 0 ? 1 : a / b)
  const item = (key: string, name: string, score: number, detail: string, suggestion: string) => ({
    key, name, score: Math.round(score), max: 20, status: score >= 18 ? 'ok' : score >= 12 ? 'warn' : 'bad', detail, suggestion: score >= 18 ? '' : suggestion
  })
  const items = [
    item('cert', '资质证照', 20 * ratio(certOk, bases.length), `${certOk}/${bases.length} 个基地已上传资质编号`, '补齐基地的经营 / 防疫资质'),
    item('fund', '资金路径', 20, '认养款、农资款都走平台商户号，没有个人收款码', ''),
    item('contract', '合同签署率', 20 * ratio(signed, paid.length), `${signed}/${paid.length} 笔已支付订单签了电子合同`, '未签合同的订单请补签'),
    item('copy', '文案合规', Math.max(0, 20 - hits.length * 4), hits.length ? `近 30 天有 ${hits.length} 次发布被拦截` : '近 30 天没有被拦截的文案', SUGGESTION),
    item('trace', '溯源完整度', 20 * ratio(traced, so.length), `${traced}/${so.length} 笔自购农资已登记批次和施用时间`, '在「农资订单」里登记施用，否则地块档案会标「待补充」')
  ]
  return { score: items.reduce((s, x) => s + x.score, 0), items, hits: hits.slice().reverse() }
}
farmAdminRouter.get('/risk/self-check', (req, res) => ok(res, selfCheck(tenantOf(req))))

// ---------- 2.2 基地 ----------
farmAdminRouter.get('/base/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().bases.filter((b) => b.tenantId === t && like(b.name, q(req, 'name')) && eqNum(b.status, q(req, 'status')))
    .map((b) => ({
      ...b,
      assetCount: db().assets.filter((a) => a.baseId === b.id).length,
      stewardCount: db().users.filter((u) => u.kind === 'steward' && u.baseId === b.id).length
    }))
  return ok(res, page(req, rows))
})
farmAdminRouter.get('/base/list', (req, res) => ok(res, db().bases.filter((b) => b.tenantId === tenantOf(req) && b.status === 0).map((b) => ({ id: b.id, name: b.name }))))
farmAdminRouter.get('/base/get', (req, res) => {
  const b = own(db().bases, req.query.id, tenantOf(req))
  return b ? ok(res, b) : notFound(res, '基地')
})
farmAdminRouter.post('/base/create', (req, res) => {
  const { name, address, lng, lat, areaMu, certNo, cropSummary, status } = req.body ?? {}
  if (!name?.trim()) return badRequest(res, '基地名称不能为空')
  if (!address?.trim()) return badRequest(res, '基地地址不能为空')
  const b = { id: nextId(db().bases), tenantId: tenantOf(req), name: name.trim(), address: address.trim(), lng: Number(lng) || 0, lat: Number(lat) || 0,
    areaMu: Number(areaMu) || 0, certNo: certNo ?? '', cropSummary: cropSummary ?? '', status: Number(status) || 0, cover: '', createTime: Date.now() }
  db().bases.push(b)
  return ok(res, b.id)
})
farmAdminRouter.put('/base/update', (req, res) => {
  const b = own(db().bases, req.body?.id, tenantOf(req))
  if (!b) return notFound(res, '基地')
  const { name, address } = req.body
  if (name !== undefined && !String(name).trim()) return badRequest(res, '基地名称不能为空')
  if (address !== undefined && !String(address).trim()) return badRequest(res, '基地地址不能为空')
  for (const k of ['name', 'address', 'lng', 'lat', 'areaMu', 'certNo', 'cropSummary', 'status'] as const) if (req.body[k] !== undefined) (b as unknown as Record<string, unknown>)[k] = req.body[k]
  return ok(res, true)
})
farmAdminRouter.delete('/base/delete', (req, res) => {
  const t = tenantOf(req)
  const b = own(db().bases, req.query.id, t)
  if (!b) return notFound(res, '基地')
  if (db().assets.some((a) => a.baseId === b.id)) return badRequest(res, '基地下还有资产，不能删除')
  db().bases.splice(db().bases.indexOf(b), 1)
  return ok(res, true)
})

// ---------- 2.3 资产 ----------
farmAdminRouter.get('/asset/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().assets.filter((a) => a.tenantId === t && eqNum(a.baseId, q(req, 'baseId')) && (q(req, 'assetType') === undefined || a.assetType === q(req, 'assetType'))
    && eqNum(a.status, q(req, 'status')) && like(a.code, q(req, 'code'))).map(assetView)
  return ok(res, page(req, rows))
})
farmAdminRouter.get('/asset/get', (req, res) => {
  const a = own(db().assets, req.query.id, tenantOf(req))
  return a ? ok(res, assetView(a)) : notFound(res, '资产')
})
farmAdminRouter.get('/asset/env', (req, res) => {
  const a = own(db().assets, req.query.assetId, tenantOf(req))
  return a ? ok(res, envView(a)) : notFound(res, '资产')
})
farmAdminRouter.post('/asset/create', (req, res) => {
  const t = tenantOf(req)
  const { baseId, assetType, code, name, specs, cameraName, sensorName } = req.body ?? {}
  if (!own(db().bases, baseId, t)) return badRequest(res, '请选择基地')
  if (!['plot', 'animal', 'tree'].includes(assetType)) return badRequest(res, '请选择资产类型')
  if (!code?.trim()) return badRequest(res, '编号不能为空')
  if (db().assets.some((a) => a.baseId === Number(baseId) && a.code === code.trim())) return badRequest(res, '编号已存在')
  const a = { id: nextId(db().assets), tenantId: t, baseId: Number(baseId), assetType, code: code.trim(), name: name?.trim() || code.trim(),
    specs: specs ?? {}, status: 0, cameraName: cameraName || null, sensorName: sensorName || null, createTime: Date.now() }
  db().assets.push(a)
  return ok(res, a.id)
})
farmAdminRouter.put('/asset/update', (req, res) => {
  const a = own(db().assets, req.body?.id, tenantOf(req))
  if (!a) return notFound(res, '资产')
  if (req.body.code !== undefined && db().assets.some((x) => x.id !== a.id && x.baseId === a.baseId && x.code === req.body.code)) return badRequest(res, '编号已存在')
  for (const k of ['code', 'name', 'specs', 'cameraName', 'sensorName'] as const) if (req.body[k] !== undefined) (a as unknown as Record<string, unknown>)[k] = req.body[k]
  if (req.body.status !== undefined) {
    if (a.status === 10 && Number(req.body.status) !== 10) return badRequest(res, '已认养的资产不能停用')
    a.status = Number(req.body.status)
  }
  return ok(res, true)
})

// ---------- 2.4 认养商品 ----------
function checkProduct(body: Partial<Product>) {
  if (!body.title?.trim()) return { code: 400, msg: '标题不能为空' }
  const modes = (body.careModes ?? []) as CareModeConfig[]
  if (!modes.some((m) => m.enabled)) return { code: 400, msg: '至少开放一种托管方式' }
  if (modes.some((m) => m.enabled && !(Number(m.price) > 0))) return { code: 400, msg: '开放的托管方式要填价格' }
  const words = findBannedWords(body.title, body.description, ...modes.map((m) => m.outputPromise), ...modes.map((m) => m.slogan))
  if (words.length) return { code: BIZ.BANNED_WORDS, msg: bannedMessage(words), data: { words, suggestion: SUGGESTION } }
  return null
}
function logHit(tenantId: number, productId: number | null, title: string, words: string[]) {
  db().complianceHits.push({ tenantId, productId, productTitle: title, words, time: Date.now() })
}

farmAdminRouter.get('/product/page', (req, res) => {
  const t = tenantOf(req)
  const priceMin = q(req, 'priceMin')
  const priceMax = q(req, 'priceMax')
  const rows = db().products.filter((p) => p.tenantId === t && like(p.title, q(req, 'title')) && eqNum(p.status, q(req, 'status')) && eqNum(p.baseId, q(req, 'baseId'))
    && (q(req, 'assetType') === undefined || p.assetType === q(req, 'assetType'))
    && (priceMin === undefined || p.basePrice >= Number(priceMin)) && (priceMax === undefined || p.basePrice <= Number(priceMax)))
    .sort(byNewest).map(productView)
  return ok(res, page(req, rows))
})
farmAdminRouter.get('/product/get', (req, res) => {
  const p = own(db().products, req.query.id, tenantOf(req))
  return p ? ok(res, productView(p)) : notFound(res, '认养商品')
})
farmAdminRouter.post('/product/create', (req, res) => {
  const t = tenantOf(req)
  const body = req.body ?? {}
  if (!own(db().bases, body.baseId, t)) return badRequest(res, '请选择基地')
  const err = checkProduct(body)
  if (err) {
    if (err.code === BIZ.BANNED_WORDS) logHit(t, null, body.title ?? '', err.data!.words)
    return fail(res, err.code, err.msg, err.data ?? null)
  }
  const modes = body.careModes as CareModeConfig[]
  const p: Product = {
    id: nextId(db().products), tenantId: t, baseId: Number(body.baseId), assetType: body.assetType ?? 'plot', title: body.title.trim(), cover: body.cover ?? '',
    description: body.description ?? '', cycleDays: Number(body.cycleDays) || 365, areaM2: body.areaM2 ?? null,
    basePrice: Number(modes.find((m) => m.mode === 'full')?.price ?? modes.find((m) => m.enabled)!.price),
    stock: Number(body.stock) || 0, sold: 0, status: Number(body.status ?? 0), careModes: modes, createTime: Date.now()
  }
  db().products.push(p)
  return ok(res, p.id)
})
farmAdminRouter.put('/product/update', (req, res) => {
  const t = tenantOf(req)
  const p = own(db().products, req.body?.id, t)
  if (!p) return notFound(res, '认养商品')
  const merged = { ...p, ...req.body }
  const err = checkProduct(merged)
  if (err) {
    if (err.code === BIZ.BANNED_WORDS) logHit(t, p.id, merged.title ?? '', err.data!.words)
    return fail(res, err.code, err.msg, err.data ?? null)
  }
  for (const k of ['title', 'cover', 'description', 'cycleDays', 'areaM2', 'stock', 'careModes', 'baseId', 'assetType', 'status'] as const) if (req.body[k] !== undefined) (p as unknown as Record<string, unknown>)[k] = req.body[k]
  p.basePrice = Number(p.careModes.find((m) => m.mode === 'full' && m.enabled)?.price ?? p.careModes.find((m) => m.enabled)!.price)
  return ok(res, true)
})
farmAdminRouter.put('/product/update-status', (req, res) => {
  const p = own(db().products, req.body?.id, tenantOf(req))
  if (!p) return notFound(res, '认养商品')
  p.status = Number(req.body.status) === 1 ? 1 : 0
  return ok(res, true)
})

// ---------- 2.5 认养订单 ----------
farmAdminRouter.get('/order/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().orders.filter((o) => o.tenantId === t).map(orderView)
    .filter((o) => like(o.no, q(req, 'no')) && like(o.memberMobile, q(req, 'memberMobile')) && (q(req, 'careMode') === undefined || o.careMode === q(req, 'careMode'))
      && eqNum(o.status, q(req, 'status')) && eqNum(o.productId, q(req, 'productId'))
      && (q(req, 'createTimeStart') === undefined || o.createTime >= Number(q(req, 'createTimeStart')))
      && (q(req, 'createTimeEnd') === undefined || o.createTime <= Number(q(req, 'createTimeEnd'))))
    .sort(byNewest)
  return ok(res, page(req, rows))
})
farmAdminRouter.get('/order/get', (req, res) => {
  const o = own(db().orders, req.query.id, tenantOf(req))
  if (!o) return notFound(res, '订单')
  const ad = db().adoptions.find((a) => a.id === o.adoptionId)
  const m = db().members.find((x) => x.id === o.memberId)
  const mine = db().orders.filter((x) => x.tenantId === o.tenantId && x.memberId === o.memberId && x.payTime && x.status !== 30)
  const st = ad ? db().users.find((u) => u.id === ad.stewardId) : undefined
  return ok(res, {
    ...orderView(o),
    contract: contractOf(o),
    // 客户信息卡：累计认养 = 该客户已支付且未取消的订单数，累计消费 = 其金额合计（分）
    customer: { name: m?.nickname ?? '', mobile: m?.mobile ?? '', orderCount: mine.length, totalAmount: mine.reduce((s, x) => s + x.amount, 0) },
    steward: st ? { name: st.nickname, mobile: maskMobile(st.mobile) } : null,
    adoption: ad ? adoptionView(ad) : null,
    records: ad ? db().records.filter((r) => r.adoptionId === ad.id && r.status === 10).sort(byNewest).slice(0, 10).map(recordView) : [],
    shipments: ad ? db().shipments.filter((s) => s.adoptionId === ad.id).map(shipmentView) : []
  })
})

// ---------- 2.6 农事记录审核 ----------
farmAdminRouter.get('/record/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().records.filter((r) => r.tenantId === t && eqNum(r.status, q(req, 'status')) && eqNum(r.stewardId, q(req, 'stewardId')))
    .map(recordView).filter((r) => like(r.assetCode, q(req, 'assetCode'))).sort(byNewest)
  return ok(res, page(req, rows))
})
/** 统计卡（原型 archivePub）：今日提交 / 待审核 / 本月自动过检 / 本月需人工复核 / 本月累计 */
farmAdminRouter.get('/record/stats', (req, res) => {
  const t = tenantOf(req)
  const startToday = new Date(new Date().toDateString()).getTime()
  const monthStart = new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime()
  const all = db().records.filter((r) => r.tenantId === t)
  const month = all.filter((r) => r.createTime >= monthStart)
  const allPass = (r: { checks: { exifTime: boolean; gpsFence: boolean; duplicate: boolean } }) => !!(r.checks?.exifTime && r.checks?.gpsFence && r.checks?.duplicate)
  const assetCount = new Set(month.map((r) => r.assetId)).size
  return ok(res, {
    todayCount: all.filter((r) => r.createTime >= startToday).length,
    pendingCount: all.filter((r) => r.status === 0).length,
    autoPassCount: month.filter(allPass).length,
    manualReviewCount: month.filter((r) => !allPass(r)).length,
    monthCount: month.length,
    avgPerAsset: assetCount ? Math.round((month.length / assetCount) * 10) / 10 : 0
  })
})
farmAdminRouter.get('/record/get', (req, res) => {
  const r = own(db().records, req.query.id, tenantOf(req))
  return r ? ok(res, recordView(r)) : notFound(res, '农事记录')
})
farmAdminRouter.put('/record/audit', (req, res) => {
  const r = own(db().records, req.body?.id, tenantOf(req))
  if (!r) return notFound(res, '农事记录')
  const pass = req.body.pass === true
  if (!pass && !String(req.body.remark ?? '').trim()) return badRequest(res, '驳回时请填写原因')
  if (r.status !== 0) return fail(res, BIZ.TASK_STATE, '这条记录已经审核过了')
  r.status = pass ? 10 : 20
  r.auditRemark = req.body.remark ?? null
  r.auditTime = Date.now()
  return ok(res, true)
})

// ---------- 2.7 代管工单 ----------
farmAdminRouter.get('/service-order/page', (req, res) => {
  // 带 stats 与派生字段（careModeName、proofCount），见 farm/stats.ts
  return ok(res, serviceOrderPage(req, tenantOf(req)))
})
farmAdminRouter.get('/service-order/get', (req, res) => {
  const s = own(db().serviceOrders, req.query.id, tenantOf(req))
  return s ? ok(res, serviceOrderView(s)) : notFound(res, '代管工单')
})

// ---------- 2.8 农资 ----------
function normalizeSupply(s: Partial<Supply>) {
  if (s.cat === 'pesticide') { s.retailAllowed = false; s.needWorkorder = true }
}
farmAdminRouter.get('/supply/page', (req, res) => {
  // 带 stats 与派生字段（code、grossMargin、stateName），支持 batchNo 筛选，见 farm/stats.ts
  return ok(res, supplyPage(req, tenantOf(req)))
})
farmAdminRouter.get('/supply/get', (req, res) => {
  const s = own(db().supplies, req.query.id, tenantOf(req))
  return s ? ok(res, s) : notFound(res, '农资')
})
farmAdminRouter.post('/supply/create', (req, res) => {
  const b = req.body ?? {}
  if (!b.name?.trim()) return badRequest(res, '名称不能为空')
  if (!b.cat) return badRequest(res, '请选择类目')
  if (!(Number(b.price) > 0)) return badRequest(res, '价格要大于 0')
  const words = findBannedWords(b.name, b.description)
  if (words.length) return fail(res, BIZ.BANNED_WORDS, bannedMessage(words), { words, suggestion: SUGGESTION })
  const s: Supply = { id: nextId(db().supplies), tenantId: tenantOf(req), name: b.name.trim(), cat: b.cat, cover: b.cover ?? '', price: Number(b.price), cost: Number(b.cost) || 0,
    stock: Number(b.stock) || 0, unit: b.unit ?? '件', spec: b.spec ?? '', fitModes: b.fitModes ?? ['semi', 'self'], batchNo: b.batchNo ?? '', retailAllowed: b.retailAllowed !== false,
    needWorkorder: !!b.needWorkorder, status: Number(b.status) || 0, sold: 0, description: b.description ?? '' }
  normalizeSupply(s)
  db().supplies.push(s)
  return ok(res, s.id)
})
farmAdminRouter.put('/supply/update', (req, res) => {
  const s = own(db().supplies, req.body?.id, tenantOf(req))
  if (!s) return notFound(res, '农资')
  const words = findBannedWords(req.body.name, req.body.description)
  if (words.length) return fail(res, BIZ.BANNED_WORDS, bannedMessage(words), { words, suggestion: SUGGESTION })
  for (const k of ['name', 'cat', 'cover', 'price', 'cost', 'stock', 'unit', 'spec', 'fitModes', 'batchNo', 'retailAllowed', 'needWorkorder', 'status', 'description'] as const)
    if (req.body[k] !== undefined) (s as unknown as Record<string, unknown>)[k] = req.body[k]
  normalizeSupply(s)
  return ok(res, true)
})
farmAdminRouter.get('/supply-order/page', (req, res) => {
  // 带 stats 与 stateName；筛选 no / keyword（订单号或认养人）/ assetCode / state / traceState，见 farm/stats.ts
  return ok(res, supplyOrderPage(req, tenantOf(req)))
})
farmAdminRouter.put('/supply-order/trace', (req, res) => {
  const o = own(db().supplyOrders, req.body?.id, tenantOf(req))
  if (!o) return notFound(res, '农资订单')
  if (!req.body.usedAt) return badRequest(res, '请填写施用时间')
  if (!String(req.body.dosage ?? '').trim()) return badRequest(res, '请填写用量')
  if (o.traceState === 10) return fail(res, BIZ.TASK_STATE, '已经登记过了')
  o.traceState = 10
  o.usedAt = Number(req.body.usedAt)
  o.dosage = String(req.body.dosage)
  const spu = db().supplies.find((s) => s.id === o.spuId)
  db().inputRecords.push({ id: nextId(db().inputRecords), tenantId: o.tenantId, assetId: o.assetId, source: 'member', name: spu?.name ?? '', batchNo: o.batchNo,
    usedAt: o.usedAt, dosage: o.dosage, operator: db().members.find((m) => m.id === o.memberId)?.nickname ?? '' })
  return ok(res, true)
})

// ---------- 2.9 收成、配送 ----------
farmAdminRouter.get('/harvest/page', (req, res) => {
  return ok(res, harvestPage(req, tenantOf(req)))
})
farmAdminRouter.get('/adoption/simple-list', (req, res) =>
  ok(res, db().adoptions.filter((a) => a.tenantId === tenantOf(req) && a.status === 10).map((a) => {
    const v = adoptionView(a)
    return { id: a.id, label: `${v.assetCode} · ${v.memberName} · ${v.careModeName}`, assetCode: v.assetCode, memberName: v.memberName, careMode: a.careMode }
  }))
)
farmAdminRouter.post('/harvest/create', (req, res) => {
  const t = tenantOf(req)
  const ad = own(db().adoptions, req.body?.adoptionId, t)
  if (!ad) return badRequest(res, '请选择认养关系')
  // 支持多品类明细 items:[{crop,weightJin}]；老的单作物 crop + weightJin 仍可用
  const rawItems: { crop?: unknown; weightJin?: unknown }[] = Array.isArray(req.body.items) && req.body.items.length ? req.body.items : [{ crop: req.body.crop, weightJin: req.body.weightJin }]
  const items: { crop: string; weightJin: number }[] = []
  for (const it of rawItems) {
    const crop = String(it.crop ?? '').trim()
    const w = Number(it.weightJin)
    if (!crop) return badRequest(res, '请填写作物')
    if (!(w > 0)) return badRequest(res, '重量要大于 0')
    items.push({ crop, weightJin: Math.round(w * 10) / 10 })
  }
  const weightJin = Math.round(items.reduce((s, x) => s + x.weightJin, 0) * 10) / 10
  const cropText = items.map((x) => x.crop).join('、')
  const farmType = String(req.body.farmType ?? '').trim() || '采摘采收'
  const detail = items.length > 1 ? `明细：${items.map((x) => `${x.crop} ${x.weightJin} 斤`).join('、')}` : ''
  const userRemark = String(req.body.remark ?? '').trim()
  const remark = [userRemark, detail].filter(Boolean).join('；')
  // 库里 crop 是 varchar(64)、remark 是 varchar(255)，mock 也按这个限制，免得联调时才发现
  if (cropText.length > 64) return badRequest(res, '品类名称合计不能超过 64 个字')
  if (remark.length > 255) return badRequest(res, '备注加明细不能超过 255 个字')
  const h = { id: nextId(db().harvests), tenantId: t, adoptionId: ad.id, assetId: ad.assetId, crop: cropText, weightJin,
    harvestDate: Number(req.body.harvestDate) || Date.now(), photos: req.body.photos ?? [], remark, shipmentId: null }
  db().harvests.push(h)
  ad.harvestedJin = Math.round((ad.harvestedJin + weightJin) * 10) / 10
  // 采收写进生长档案（经营者录入的，直接算已审核）
  const u = currentUser(req)
  db().records.push({ id: nextId(db().records), tenantId: t, adoptionId: ad.id, assetId: ad.assetId, stewardId: ad.stewardId, taskId: null, kind: 'harvest',
    title: `采收 ${h.crop} ${weightJin} 斤`, content: `${u?.nickname ?? '农场'}录入收成（农事类型：${farmType}），称重 ${weightJin} 斤。${h.remark}`, photos: h.photos, lng: 0, lat: 0,
    env: { airTemp: 0, airHumidity: 0, soilMoisture: 0 }, checks: { exifTime: true, gpsFence: true, duplicate: true }, status: 10, auditRemark: null, createTime: Date.now(), auditTime: Date.now() })
  return ok(res, h.id)
})
farmAdminRouter.get('/shipment/page', (req, res) => {
  // 带 stats 与派生字段（batchNo、planDate、progress），见 farm/stats.ts
  return ok(res, shipmentPage(req, tenantOf(req)))
})
farmAdminRouter.post('/shipment/create', (req, res) => {
  const t = tenantOf(req)
  const h = own(db().harvests, req.body?.harvestId, t)
  if (!h) return badRequest(res, '请选择收成')
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
farmAdminRouter.put('/shipment/sign', (req, res) => {
  const s = own(db().shipments, req.body?.id, tenantOf(req))
  if (!s) return notFound(res, '配送单')
  if (s.status !== 10) return fail(res, BIZ.ORDER_STATE, '只有运输中的配送单能确认签收')
  s.status = 20
  s.tracks.push({ time: Date.now(), content: `已签收，签收人：${s.receiverName}` })
  return ok(res, true)
})

// ---------- 平台公告（平台在文章管理里发布的公告、帮助，给所有经营者看） ----------
const PLATFORM = 1
farmAdminRouter.get('/notice/list', (_req, res) =>
  ok(res, db().articles.filter((a) => a.tenantId === PLATFORM && a.status === 0).sort(byNewest)
    .map((a) => ({ id: a.id, title: a.title, introduction: a.introduction, createTime: a.createTime,
      categoryName: db().articleCategories.find((c) => c.id === a.categoryId)?.name ?? '' })))
)
farmAdminRouter.get('/notice/get', (req, res) => {
  const a = db().articles.find((x) => x.id === Number(req.query.id) && x.tenantId === PLATFORM && x.status === 0)
  if (!a) return notFound(res, '公告')
  return ok(res, { ...a, categoryName: db().articleCategories.find((c) => c.id === a.categoryId)?.name ?? '' })
})
