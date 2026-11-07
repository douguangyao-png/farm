import { Router, type Request } from 'express'
import { getDb, nextId, type Order, type Adoption } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { page, q, like, eqNum, byNewest, tenantOf, own, notFound, badRequest, makeNo, currentMember, CARE_MODE_NAME } from '../farm/common.js'
import { orderView } from '../farm/views.js'

/**
 * 退款闭环（第二期 A）。
 *
 * 状态与联动规则（Java 镜像：FarmRefundService，两边必须一致）：
 *  - 退款单 refund.status：0 待审核 / 10 已通过 / 20 已驳回
 *  - 订单 order.status 新增：40 退款中 / 50 已退款
 *  - 认养 adoption.status 新增：30 已终止（退款通过后）
 *  - 农事任务 task.status 新增：30 已关闭（退款通过后，认养名下待办任务关闭）
 *
 *  申请（C 端 POST /app-api/farm/refund/create）：只有「认养中(10)」订单可申请，原因必填。
 *    订单 10 -> 40，生成退款单（status=0，当时试算的金额存进退款单，经营者不能改）。
 *    订单已是 40（已有申请在处理）或 50（已退款）-> 1030000008；其他状态（待付款/已完成/已取消）-> 1030000009。
 *  驳回（PUT /admin-api/farm/refund/audit approve=false，审核意见必填）：
 *    退款单 0 -> 20；订单 40 -> 回到申请前的状态（10）；认养、地块、任务不动。驳回后认养人可再次申请。
 *  通过（approve=true）：
 *    退款单 0 -> 10；订单 40 -> 50；认养 10 -> 30；认养的地块若是「已认养(10)」则 -> 0 空闲（可重新上架销售）；
 *    认养名下待办(0)的农事任务 -> 30 已关闭；认养名下待接单(0)的代管工单 -> 30 已取消；
 *    合同台账按订单状态 50 显示「已解除」（合同状态由订单派生，见登记文件）。
 *    款项原路退回是真实支付能力，演示环境不做，只改状态。
 *
 * 试算（按原型 08「系统试算（按合同第六条公式）」）：
 *    已过周期比例 = clamp((现在 - 开始) / (结束 - 开始), 0, 1)，开始前为 0（开始前全额退）
 *    已发生农事投入 = round(实付 * 已过周期比例 * 20%)        20% 是演示系数，不是合同条款
 *    已交付农产品折算 = 有约定交付量时 round(实付 * min(1, 已收斤数 / 约定斤数))，否则 0（半托管/不托管没有约定量，不折算）
 *    扣减 = min(实付, 投入 + 折算)；应退 = 实付 - 扣减。金额单位：分。
 */
export interface Refund {
  id: number; tenantId: number; no: string; orderId: number; memberId: number
  amount: number; investDeduct: number; deliverDeduct: number; deduct: number; refundAmount: number
  reason: string; status: number; auditRemark: string | null; prevOrderStatus: number
  createTime: number; auditTime: number | null
}

export const REFUND_BIZ = { DUPLICATE: 1_030_000_008, NOT_ALLOWED: 1_030_000_009, STATE: 1_030_000_010 } as const
export const REFUND_STATUS_NAME: Record<number, string> = { 0: '待审核', 10: '已通过', 20: '已驳回' }

const HOUR = 3600_000
const DAY = 24 * HOUR
const db = getDb
/** 退款单集合：种子 seed/refunds.json 载入，resetDb 后随新 db 对象重新载入；没有该文件时当空集合 */
export const refundsOf = (): Refund[] => {
  const d = db() as unknown as { refunds?: Refund[] }
  return (d.refunds ??= [])
}

export function trial(o: Order, ad: Adoption | undefined, now = Date.now()) {
  let rate = 0
  if (ad && now > ad.startAt && ad.endAt > ad.startAt) rate = Math.min(1, (now - ad.startAt) / (ad.endAt - ad.startAt))
  const investDeduct = Math.round(o.amount * rate * 0.2)
  const promised = ad?.promisedJin ?? 0
  const deliverDeduct = promised > 0 ? Math.round(o.amount * Math.min(1, (ad?.harvestedJin ?? 0) / promised)) : 0
  const deduct = Math.min(o.amount, investDeduct + deliverDeduct)
  return { amount: o.amount, elapsedRate: Math.round(rate * 1000) / 1000, investDeduct, deliverDeduct, deduct, refundAmount: o.amount - deduct }
}

export function refundView(r: Refund) {
  const o = db().orders.find((x) => x.id === r.orderId)
  const ov = o ? orderView(o) : undefined
  return {
    ...r,
    statusName: REFUND_STATUS_NAME[r.status] ?? '',
    orderNo: o?.no ?? '',
    orderStatus: o?.status ?? null,
    memberName: ov?.memberName ?? db().members.find((m) => m.id === r.memberId)?.nickname ?? '',
    memberMobile: ov?.memberMobile ?? '',
    productTitle: ov?.productTitle ?? '',
    productCover: ov?.productCover ?? '',
    assetCode: ov?.assetCode ?? '',
    careMode: o?.careMode ?? null,
    careModeName: o ? CARE_MODE_NAME[o.careMode] : ''
  }
}

/** 本租户「待审核」退款单数量（工作台待办用） */
export const pendingRefundCount = (tenantId: number) => refundsOf().filter((r) => r.tenantId === tenantId && r.status === 0).length

// =============================== 后台 /admin-api/farm/refund ===============================
export const farmRefundRouter = Router()
farmRefundRouter.use(requireLogin('admin'))

farmRefundRouter.get('/page', (req, res) => {
  const t = tenantOf(req)
  const rows = refundsOf().filter((r) => r.tenantId === t).map(refundView)
    .filter((r) => like(r.no, q(req, 'no')) && like(r.orderNo, q(req, 'orderNo')) && like(r.memberMobile, q(req, 'memberMobile')) && eqNum(r.status, q(req, 'status')))
    .sort(byNewest)
  return ok(res, page(req, rows))
})

farmRefundRouter.get('/stats', (req, res) => {
  const t = tenantOf(req)
  const all = refundsOf().filter((r) => r.tenantId === t)
  const now = Date.now()
  const d = new Date(now)
  const m0 = new Date(d.getFullYear(), d.getMonth(), 1).getTime()
  const monthApply = all.filter((r) => r.createTime >= m0).length
  const monthPaid = db().orders.filter((o) => o.tenantId === t && o.payTime !== null && o.payTime >= m0).length
  const pending = all.filter((r) => r.status === 0)
  const audited = all.filter((r) => r.auditTime !== null)
  const approved = all.filter((r) => r.status === 10)
  // 近 30 天同一原因 >= 2 单 -> 经营预警（原型 08 退款审核页底部提示）
  const reasonCount = new Map<string, number>()
  for (const r of all.filter((x) => x.createTime >= now - 30 * DAY)) reasonCount.set(r.reason, (reasonCount.get(r.reason) ?? 0) + 1)
  const top = [...reasonCount.entries()].sort((a, b) => b[1] - a[1])[0]
  return ok(res, {
    counts: { all: all.length, '0': pending.length, '10': approved.length, '20': all.filter((r) => r.status === 20).length },
    monthApply,
    refundRate: monthPaid > 0 ? Math.round((monthApply / monthPaid) * 1000) / 10 : null,
    pending: pending.length,
    pendingOver24h: pending.filter((r) => now - r.createTime > 24 * HOUR).length,
    avgHandleHours: audited.length ? Math.round((audited.reduce((s, r) => s + (r.auditTime! - r.createTime), 0) / audited.length / HOUR) * 10) / 10 : null,
    refundedAmount: approved.reduce((s, r) => s + r.refundAmount, 0),
    refundedCount: approved.length,
    reasonAlert: top && top[1] >= 2 ? { reason: top[0], count: top[1] } : null
  })
})

farmRefundRouter.get('/get', (req, res) => {
  const r = own(refundsOf(), req.query.id, tenantOf(req))
  if (!r) return notFound(res, '退款单')
  const o = db().orders.find((x) => x.id === r.orderId)
  const ad = o?.adoptionId ? db().adoptions.find((x) => x.id === o.adoptionId) : undefined
  return ok(res, {
    ...refundView(r),
    adoption: ad ? { id: ad.id, startAt: ad.startAt, endAt: ad.endAt, promisedJin: ad.promisedJin, harvestedJin: ad.harvestedJin } : null,
    elapsedRate: o ? trial(o, ad, r.createTime).elapsedRate : 0
  })
})

farmRefundRouter.put('/audit', (req, res) => {
  const t = tenantOf(req)
  const b = req.body ?? {}
  const r = own(refundsOf(), b.id, t)
  if (!r) return notFound(res, '退款单')
  if (typeof b.approve !== 'boolean') return badRequest(res, '请选择通过或驳回')
  const remark = String(b.remark ?? '').trim()
  if (!b.approve && !remark) return badRequest(res, '驳回时请填写审核说明')
  if (remark.length > 200) return badRequest(res, '审核说明最长 200 字')
  const o = own(db().orders, r.orderId, t)
  if (r.status !== 0 || !o || o.status !== 40) return fail(res, REFUND_BIZ.STATE, '退款单状态已变化，请刷新')
  const now = Date.now()
  r.auditRemark = remark || null
  r.auditTime = now
  if (!b.approve) {
    r.status = 20
    o.status = r.prevOrderStatus
  } else {
    r.status = 10
    o.status = 50
    const ad = o.adoptionId ? db().adoptions.find((x) => x.id === o.adoptionId) : undefined
    if (ad) {
      if (ad.status === 10) ad.status = 30
      const asset = db().assets.find((a) => a.id === ad.assetId)
      if (asset && asset.status === 10) asset.status = 0
      for (const tk of db().tasks) if (tk.adoptionId === ad.id && tk.status === 0) tk.status = 30
      for (const so of db().serviceOrders) if (so.adoptionId === ad.id && so.status === 0) so.status = 30
    }
  }
  return ok(res, refundView(r))
})

// =============================== C 端 /app-api/farm/refund ===============================
export const farmAppRefundRouter = Router()
const login = requireLogin('member')
const mine = (req: Request) => currentMember(req)!

/** 试算预览：申请前展示「预计退回」（原型 07 退款页「退款金额试算」） */
farmAppRefundRouter.get('/preview', login, (req, res) => {
  const m = mine(req)
  const o = own(db().orders, req.query.orderId, m.tenantId)
  if (!o || o.memberId !== m.id) return notFound(res, '订单')
  const ad = o.adoptionId ? db().adoptions.find((x) => x.id === o.adoptionId) : undefined
  return ok(res, { orderId: o.id, orderNo: o.no, canApply: o.status === 10, orderStatus: o.status, ...trial(o, ad) })
})

farmAppRefundRouter.post('/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const reason = String(b.reason ?? '').trim()
  if (!reason) return badRequest(res, '请填写退款原因')
  if (reason.length > 200) return badRequest(res, '退款原因最长 200 字')
  const o = own(db().orders, b.orderId, m.tenantId)
  if (!o || o.memberId !== m.id) return notFound(res, '订单')
  if (o.status === 40 || refundsOf().some((r) => r.orderId === o.id && r.status === 0)) return fail(res, REFUND_BIZ.DUPLICATE, '该订单已有退款申请在处理中')
  if (o.status === 50) return fail(res, REFUND_BIZ.DUPLICATE, '该订单已退款')
  if (o.status !== 10) return fail(res, REFUND_BIZ.NOT_ALLOWED, '该订单当前状态不能申请退款')
  const ad = o.adoptionId ? db().adoptions.find((x) => x.id === o.adoptionId) : undefined
  const tr = trial(o, ad)
  const r: Refund = {
    id: nextId(refundsOf()), tenantId: o.tenantId, no: makeNo('R'), orderId: o.id, memberId: m.id,
    amount: tr.amount, investDeduct: tr.investDeduct, deliverDeduct: tr.deliverDeduct, deduct: tr.deduct, refundAmount: tr.refundAmount,
    reason, status: 0, auditRemark: null, prevOrderStatus: o.status, createTime: Date.now(), auditTime: null
  }
  refundsOf().push(r)
  o.status = 40
  return ok(res, refundView(r))
})

farmAppRefundRouter.get('/list', login, (req, res) => {
  const m = mine(req)
  return ok(res, refundsOf().filter((r) => r.memberId === m.id && r.tenantId === m.tenantId).sort(byNewest).map(refundView))
})
