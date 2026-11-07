import type { Request } from 'express'
import { getDb, type Supply } from '../store.js'
import { CARE_MODE_NAME, page, q, like, eqNum, byNewest } from './common.js'
import { serviceOrderView, supplyOrderView, harvestView, shipmentView } from './views.js'

/**
 * 经营者后台「收成录入、配送批次、农资订单、农资商品、代管工单」五个列表页：
 * 在 page 返回里附带 stats，并给每行补原型里有的派生字段（字段说明见 docs/api/farm-接口契约.md 2.7～2.9）。
 * 派生字段只在这几个后台列表接口里加，不动 views.ts，C 端和管家端不受影响。
 */
const db = getDb
const r1 = (n: number) => Math.round(n * 10) / 10
const startOfDay = (t = Date.now()) => new Date(new Date(t).toDateString()).getTime()
const monthStart = (offset = 0) => {
  const d = new Date()
  return new Date(d.getFullYear(), d.getMonth() + offset, 1).getTime()
}
const pad = (n: number) => String(n).padStart(2, '0')

// ---------- 2.7 代管工单 ----------
/** 执行好评率没有评价数据，演示口径固定 98.4（%） */
const DEMO_GOOD_RATE = 98.4

export function serviceOrderPage(req: Request, t: number) {
  const all = db().serviceOrders.filter((s) => s.tenantId === t).sort(byNewest)
  const m0 = monthStart()
  const m1 = monthStart(-1)
  const monthCount = all.filter((s) => s.createTime >= m0).length
  const lastMonthCount = all.filter((s) => s.createTime >= m1 && s.createTime < m0).length
  const doneThisMonth = all.filter((s) => s.status === 20 && (s.finishTime ?? s.createTime) >= m0)
  const accepted = all.filter((s) => s.acceptTime)
  const stats = {
    counts: {
      all: all.length,
      pending: all.filter((s) => s.status === 0).length,
      serving: all.filter((s) => s.status === 10).length,
      done: all.filter((s) => s.status === 20).length,
      cancelled: all.filter((s) => s.status === 30).length
    },
    monthCount,
    monthCountGrowth: lastMonthCount > 0 ? r1(((monthCount - lastMonthCount) / lastMonthCount) * 100) : null,
    income: doneThisMonth.reduce((s, x) => s + x.price, 0),
    stewardIncome: doneThisMonth.reduce((s, x) => s + Math.round((x.price * x.split.steward) / 100), 0),
    avgAcceptHours: accepted.length ? r1(accepted.reduce((s, x) => s + (x.acceptTime! - x.createTime), 0) / accepted.length / 3600_000) : null,
    goodRate: DEMO_GOOD_RATE
  }
  const rows = all
    .filter((s) => eqNum(s.status, q(req, 'status')) && like(s.no, q(req, 'no')))
    .map((s) => {
      const ad = db().adoptions.find((a) => a.id === s.adoptionId)
      return { ...serviceOrderView(s), careModeName: ad ? CARE_MODE_NAME[ad.careMode] ?? '' : '', proofCount: s.proofPhotos.length }
    })
  return { ...page(req, rows), stats }
}

// ---------- 2.8 农资商品 ----------
/** 低库存线：库存低于 200 算低库存（服务、保险库存是 9999，不会命中） */
const LOW_STOCK = 200

export function supplyStateName(s: Supply) {
  if (s.status === 1) return '已下架'
  if (s.needWorkorder) return '需工单'
  if (s.stock < LOW_STOCK) return '低库存'
  return '正常'
}

export function supplyPage(req: Request, t: number) {
  const all = db().supplies.filter((s) => s.tenantId === t)
  const m0 = monthStart()
  const orders = db().supplyOrders.filter((o) => o.tenantId === t)
  const monthOrders = orders.filter((o) => o.createTime >= m0)
  const perMember = new Map<number, number>()
  for (const o of orders) perMember.set(o.memberId, (perMember.get(o.memberId) ?? 0) + 1)
  const repeatMemberCount = [...perMember.values()].filter((n) => n >= 2).length
  const monthGmv = monthOrders.reduce((s, o) => s + o.amount, 0)
  const catCounts: Record<string, number> = { all: all.length }
  for (const s of all) catCounts[s.cat] = (catCounts[s.cat] ?? 0) + 1
  const stats = {
    monthGmv,
    monthOrderCount: monthOrders.length,
    avgOrderAmount: monthOrders.length ? Math.round(monthGmv / monthOrders.length) : 0,
    repeatMemberCount,
    repeatRate: perMember.size ? r1((repeatMemberCount / perMember.size) * 100) : 0,
    serviceIncome: db().serviceOrders.filter((x) => x.tenantId === t && x.status === 20 && (x.finishTime ?? x.createTime) >= m0).reduce((s, x) => s + x.price, 0),
    catCounts
  }
  const rows = all
    .filter((s) => like(s.name, q(req, 'name')) && (q(req, 'cat') === undefined || s.cat === q(req, 'cat')) && like(s.batchNo, q(req, 'batchNo')) && eqNum(s.status, q(req, 'status')))
    .map((s) => ({ ...s, code: `SP-${100 + s.id}`, grossMargin: s.price > 0 ? Math.round(((s.price - s.cost) / s.price) * 100) : 0, stateName: supplyStateName(s) }))
  return { ...page(req, rows), stats }
}

// ---------- 2.8 农资订单 ----------
/** 订单状态（演示口径，库里没有单独状态字段）：服务类 = 登记后已完成、否则待派单；保险 = 已生效；农具登记后 = 已安装；其余登记后 = 已使用、否则待使用 */
export function supplyOrderStateName(cat: string, traceState: number) {
  if (cat === 'svc') return traceState === 10 ? '已完成' : '待派单'
  if (cat === 'ins') return '已生效'
  if (traceState === 10) return cat === 'tool' ? '已安装' : '已使用'
  return '待使用'
}

export function supplyOrderPage(req: Request, t: number) {
  const all = db().supplyOrders.filter((o) => o.tenantId === t).sort(byNewest).map((o) => {
    const v = supplyOrderView(o)
    return { ...v, stateName: supplyOrderStateName(v.cat, o.traceState) }
  })
  const d0 = startOfDay()
  const today = all.filter((o) => o.createTime >= d0)
  const perMember = new Map<number, number>()
  for (const o of all) perMember.set(o.memberId, (perMember.get(o.memberId) ?? 0) + 1)
  const repeatMemberCount = [...perMember.values()].filter((n) => n >= 2).length
  const stats = {
    todayCount: today.length,
    todayAmount: today.reduce((s, o) => s + o.amount, 0),
    pendingDispatch: all.filter((o) => o.stateName === '待派单').length,
    untraced: all.filter((o) => o.traceState === 0).length,
    repeatMemberCount,
    repeatRate: perMember.size ? r1((repeatMemberCount / perMember.size) * 100) : 0
  }
  const kw = q(req, 'keyword')
  const rows = all.filter(
    (o) =>
      eqNum(o.traceState, q(req, 'traceState')) &&
      like(o.no, q(req, 'no')) &&
      (kw === undefined || o.no.includes(kw) || o.memberName.includes(kw)) &&
      like(o.assetCode, q(req, 'assetCode')) &&
      (q(req, 'state') === undefined || o.stateName === q(req, 'state'))
  )
  return { ...page(req, rows), stats }
}

// ---------- 2.9 收成 ----------
export function harvestPage(req: Request, t: number) {
  const all = db().harvests.filter((h) => h.tenantId === t).sort((a, b) => b.harvestDate - a.harvestDate)
  const m0 = monthStart()
  const unshipped = all.filter((h) => !h.shipmentId)
  const stats = {
    totalCount: all.length,
    totalWeightJin: r1(all.reduce((s, h) => s + h.weightJin, 0)),
    monthWeightJin: r1(all.filter((h) => h.harvestDate >= m0).reduce((s, h) => s + h.weightJin, 0)),
    unshippedCount: unshipped.length,
    unshippedWeightJin: r1(unshipped.reduce((s, h) => s + h.weightJin, 0))
  }
  const rows = all
    .map(harvestView)
    .filter((h) => like(h.assetCode, q(req, 'assetCode')) && (q(req, 'shipped') === undefined || (q(req, 'shipped') === 'true') === !!h.shipmentId))
  return { ...page(req, rows), stats }
}

// ---------- 2.9 配送 ----------
/** 完成度（原型：待发货 28 / 已发货 62 / 已完成 100） */
const SHIPMENT_PROGRESS: Record<number, number> = { 0: 28, 10: 62, 20: 100 }

export function shipmentPage(req: Request, t: number) {
  const all = db().shipments.filter((s) => s.tenantId === t).sort(byNewest).map((s) => {
    const d = new Date(s.createTime)
    return {
      ...shipmentView(s),
      batchNo: `B${d.getFullYear()}-${pad(d.getMonth() + 1)}${pad(d.getDate())}`,
      planDate: startOfDay(s.createTime),
      progress: SHIPMENT_PROGRESS[s.status] ?? 0
    }
  })
  const stats = {
    counts: {
      all: all.length,
      pending: all.filter((s) => s.status === 0).length,
      shipped: all.filter((s) => s.status === 10).length,
      done: all.filter((s) => s.status === 20).length
    },
    batchCount: new Set(all.map((s) => s.batchNo)).size,
    shippedWeightJin: r1(all.filter((s) => s.status === 10).reduce((s, x) => s + x.weightJin, 0)),
    coldChainCount: all.filter((s) => s.coldChain).length
  }
  const rows = all.filter((s) => eqNum(s.status, q(req, 'status')) && like(s.no, q(req, 'no')))
  return { ...page(req, rows), stats }
}
