import { Router } from 'express'
import { getDb, type Member } from '../store.js'
import { ok, requireLogin } from '../http.js'
import { page, q, like, tenantOf, own, notFound, maskMobile } from '../farm/common.js'

/**
 * 客户管理 / 客户分层 / 管家管理（第二期 A）。全部只读，由 Member + Order + Adoption + User(kind=steward) 派生，不新增表。
 * 金额一律分。Java 镜像：FarmCustomerService / FarmStewardManageService，两边口径必须一致。
 *
 * 口径（原型 08 custList / custGroup / keeperMg；原型里没有数据来源的字段不编造）：
 *  - 已付订单 = payTime 非空且订单状态 ∈ {10 认养中, 20 已完成, 40 退款中}；已退款(50)、已取消(30)不算。
 *  - 累计认养 = 已付订单数；累计消费 = 已付订单金额合计；最近活跃 = 该会员全部订单的下单/支付时间的最大值。
 *  - 等级 V1~V4 按累计消费：< 1000 元 V1，< 3000 元 V2，< 8000 元 V3，其余 V4（取自原型样例的区间，演示口径，不是会员积分体系）。
 *  - 城市：从收货地址取「省」之后的「xx市」，取不到为空。
 *  - 标签（规则派生，原型里的「亲子家庭」等手工标签没有数据）：新客（仅 1 个已付订单且首单 <= 90 天）、多单客户（>= 2 个已付订单）、
 *    全托管用户（有全托管订单）、有退款记录（有已退款订单）。
 *  - 分层 RFM（原型 custGroup 的四条判定规则 + 一个补充桶），按顺序判断：
 *      无已付订单 -> other；最近活跃距今 > 180 天 -> sleep 沉睡；已付订单 >= 3 或近 365 天消费 >= 8000 元 -> core 核心；
 *      已付订单 == 2 或近 365 天消费 >= 3000 元 -> growth 成长；首单距今 <= 90 天 -> new 新客；其余 -> other 其他（原型未覆盖，演示补充）。
 *  - 续费倾向：core 高 / growth 中 / new 新客 / sleep 低 / other —。
 *  - 到期：认养中(10)且结束时间在未来 30 天内。
 *  - 到访率、满意度：没有数据来源，接口不返回，页面显示「暂无数据」。
 */
const DAY = 24 * 3600_000
const PAID = new Set([10, 20, 40])
const db = getDb

export const TIERS = [
  { key: 'core', name: '核心客户', level: '高', levelType: 'success', rule: '已付订单 ≥ 3 单，或近 365 天消费 ≥ ¥8,000', action: '专属管家 + 新品内测 + 生日礼盒' },
  { key: 'growth', name: '成长客户', level: '中高', levelType: 'warning', rule: '已付订单 2 单，或近 365 天消费 ¥3,000–8,000', action: '续费提醒 + 亲子活动优先' },
  { key: 'new', name: '新客', level: '待激活', levelType: 'info', rule: '首单距今 ≤ 90 天', action: '首季回访 + 到访邀约 + 农事科普' },
  { key: 'sleep', name: '沉睡客户', level: '流失风险', levelType: 'danger', rule: '180 天无下单/支付动作', action: '电话回访 + 到园体验邀请' },
  { key: 'other', name: '其他', level: '—', levelType: 'info', rule: '无已付订单，或不满足以上规则（原型未覆盖，演示补充）', action: '—' }
] as const
type TierKey = (typeof TIERS)[number]['key']

export function cityOf(addr?: string | null): string {
  if (!addr) return ''
  const s = addr.replace(/^[^省]*?(省|自治区)/, '')
  const m = /^(.+?)市/.exec(s)
  return m ? m[1] : ''
}

export function levelOf(totalFen: number): string {
  if (totalFen < 100_000) return 'V1'
  if (totalFen < 300_000) return 'V2'
  if (totalFen < 800_000) return 'V3'
  return 'V4'
}

function tierOf(paidCount: number, spend365: number, firstPaid: number | null, lastActive: number | null, now: number): TierKey {
  if (paidCount === 0) return 'other'
  if (lastActive !== null && now - lastActive > 180 * DAY) return 'sleep'
  if (paidCount >= 3 || spend365 >= 800_000) return 'core'
  if (paidCount === 2 || spend365 >= 300_000) return 'growth'
  if (firstPaid !== null && now - firstPaid <= 90 * DAY) return 'new'
  return 'other'
}
const REP: Record<TierKey, string> = { core: '高', growth: '中', new: '新客', sleep: '低', other: '—' }

export function buildCustomers(t: number, now: number) {
  const d = db()
  return d.members.filter((m: Member) => m.tenantId === t).map((m) => {
    const orders = d.orders.filter((o) => o.tenantId === t && o.memberId === m.id)
    const paid = orders.filter((o) => o.payTime !== null && PAID.has(o.status))
    const totalAmount = paid.reduce((s, o) => s + o.amount, 0)
    const spend365 = paid.filter((o) => o.payTime! >= now - 365 * DAY).reduce((s, o) => s + o.amount, 0)
    const firstPaid = paid.length ? Math.min(...paid.map((o) => o.payTime!)) : null
    const times = orders.flatMap((o) => [o.createTime, o.payTime]).filter((x): x is number => x !== null)
    const lastActive = times.length ? Math.max(...times) : null
    const tier = tierOf(paid.length, spend365, firstPaid, lastActive, now)
    const tags: string[] = []
    if (paid.length === 1 && firstPaid !== null && now - firstPaid <= 90 * DAY) tags.push('新客')
    if (paid.length >= 2) tags.push('多单客户')
    if (paid.some((o) => o.careMode === 'full')) tags.push('全托管用户')
    if (orders.some((o) => o.status === 50)) tags.push('有退款记录')
    const active = d.adoptions.filter((a) => a.tenantId === t && a.memberId === m.id && a.status === 10)
    const expiring = active.filter((a) => a.endAt >= now && a.endAt - now <= 30 * DAY)
    return {
      id: m.id, name: m.nickname, mobile: maskMobile(m.mobile), mobileFull: m.mobile, city: cityOf(m.address),
      level: levelOf(totalAmount), adoptCount: paid.length, totalAmount, tags,
      lastActive, tier, tierName: TIERS.find((x) => x.key === tier)!.name, renewIntent: REP[tier],
      activeAdoptionCount: active.length, expiringCount: expiring.length,
      expiringAmount: expiring.reduce((s, a) => s + (d.orders.find((o) => o.id === a.orderId)?.amount ?? 0), 0),
      productIds: [...new Set(paid.map((o) => o.productId))], hasFull: paid.some((o) => o.careMode === 'full'), firstPaid
    }
  })
}
/** 订单详情用：该客户的标签（新客/多单客户/全托管用户/有退款记录）与分层名，复用客户管理的派生口径 */
export function customerTagsOf(t: number, memberId: number): string[] {
  const c = buildCustomers(t, Date.now()).find((x) => x.id === memberId)
  return c ? [...c.tags, c.tierName] : []
}
type CustomerRow = ReturnType<typeof buildCustomers>[number]
const publicRow = ({ mobileFull: _a, productIds: _b, hasFull: _c, firstPaid: _d, expiringAmount: _e, ...rest }: CustomerRow) => rest

// =============================== /admin-api/farm/customer ===============================
export const farmCustomerRouter = Router()
farmCustomerRouter.use(requireLogin('admin'))

farmCustomerRouter.get('/page', (req, res) => {
  const now = Date.now()
  const kw = q(req, 'keyword')
  const city = q(req, 'city')
  const tag = q(req, 'tag')
  const tier = q(req, 'tier')
  const productId = q(req, 'productId')
  const activeDays = q(req, 'activeDays')
  const min = q(req, 'minAmount')
  const max = q(req, 'maxAmount')
  const expiring = q(req, 'expiring')
  const rows = buildCustomers(tenantOf(req), now)
    .filter((c) => (kw === undefined || like(c.name, kw) || like(c.mobileFull, kw))
      && (city === undefined || c.city === city)
      && (tag === undefined || c.tags.includes(tag))
      && (tier === undefined || c.tier === tier)
      && (productId === undefined || c.productIds.includes(Number(productId)))
      && (activeDays === undefined || (c.lastActive !== null && now - c.lastActive <= Number(activeDays) * DAY))
      && (expiring === undefined || c.expiringCount > 0)
      && (min === undefined || c.totalAmount >= Number(min))
      && (max === undefined || c.totalAmount <= Number(max)))
    .sort((a, b) => (b.lastActive ?? 0) - (a.lastActive ?? 0))
  return ok(res, page(req, rows.map(publicRow)))
})

farmCustomerRouter.get('/stats', (req, res) => {
  const now = Date.now()
  const t = tenantOf(req)
  const d = new Date(now)
  const m0 = new Date(d.getFullYear(), d.getMonth(), 1).getTime()
  const rows = buildCustomers(t, now)
  const buyers = rows.filter((c) => c.adoptCount >= 1)
  const orderCount = rows.reduce((s, c) => s + c.adoptCount, 0)
  return ok(res, {
    total: rows.length,
    newThisMonth: rows.filter((c) => c.firstPaid !== null && c.firstPaid >= m0).length,
    adopting: rows.filter((c) => c.activeAdoptionCount > 0).length,
    expiring30: rows.filter((c) => c.expiringCount > 0).length,
    repeatRate: buyers.length ? Math.round((buyers.filter((c) => c.adoptCount >= 2).length / buyers.length) * 1000) / 10 : null,
    avgOrderAmount: orderCount ? Math.round(rows.reduce((s, c) => s + c.totalAmount, 0) / orderCount) : 0,
    cities: [...new Set(rows.map((c) => c.city).filter(Boolean))].sort(),
    tags: ['新客', '多单客户', '全托管用户', '有退款记录']
  })
})

// =============================== /admin-api/farm/customer-tier ===============================
export const farmCustomerTierRouter = Router()
farmCustomerTierRouter.use(requireLogin('admin'))

farmCustomerTierRouter.get('/summary', (req, res) => {
  const now = Date.now()
  const rows = buildCustomers(tenantOf(req), now)
  const expiring = rows.filter((c) => c.expiringCount > 0)
  return ok(res, {
    total: rows.length,
    tiers: TIERS.map((x) => ({ ...x, count: rows.filter((c) => c.tier === x.key).length })),
    expiring: { customerCount: expiring.length, adoptionCount: expiring.reduce((s, c) => s + c.expiringCount, 0), estimateAmount: expiring.reduce((s, c) => s + c.expiringAmount, 0), remindDays: [30, 15, 3] },
    // 人群包：由规则实时圈出，只读（新建/推送是营销能力，不在本期范围）
    packs: [
      { name: '高价值待续费', count: expiring.length, buildWay: '到期规则自动生成：认养中且 30 天内到期', tier: null as string | null, query: 'expiring' },
      { name: '核心客户', count: rows.filter((c) => c.tier === 'core').length, buildWay: '分层规则：已付订单 ≥ 3 或近 365 天消费 ≥ ¥8,000', tier: 'core', query: 'tier' },
      { name: '沉睡客户召回', count: rows.filter((c) => c.tier === 'sleep').length, buildWay: '分层规则：180 天无下单/支付动作', tier: 'sleep', query: 'tier' },
      { name: '全托管用户', count: rows.filter((c) => c.hasFull).length, buildWay: '标签组合：有全托管已付订单', tier: null, query: 'tag' }
    ],
    updatedAt: now
  })
})

// =============================== /admin-api/farm/steward-manage ===============================
export const farmStewardManageRouter = Router()
farmStewardManageRouter.use(requireLogin('admin'))

function buildStewards(t: number, now: number) {
  const d = db()
  const dt = new Date(now)
  const m0 = new Date(dt.getFullYear(), dt.getMonth(), 1).getTime()
  const m1 = new Date(dt.getFullYear(), dt.getMonth() + 1, 1).getTime()
  return d.users.filter((u) => u.tenantId === t && u.kind === 'steward').map((u) => {
    const base = d.bases.find((b) => b.id === u.baseId)
    const baseAssets = d.assets.filter((a) => a.tenantId === t && a.baseId === u.baseId)
    const ads = d.adoptions.filter((a) => a.tenantId === t && a.stewardId === u.id && a.status === 10)
    const tasks = d.tasks.filter((x) => x.tenantId === t && x.stewardId === u.id && x.deadline >= m0 && x.deadline < m1 && (x.status === 0 || x.status === 10))
    const done = tasks.filter((x) => x.status === 10).length
    const accepted = d.serviceOrders.filter((s) => s.tenantId === t && s.stewardId === u.id && s.acceptTime !== null)
    const roleName = d.roles.find((r) => u.roleIds.includes(r.id))?.name ?? '管家'
    return {
      id: u.id, name: u.nickname, mobile: maskMobile(u.mobile), mobileFull: u.mobile ?? '', roleName,
      baseId: u.baseId ?? null, baseName: base?.name ?? '',
      assetTotal: baseAssets.length, assetAdopted: baseAssets.filter((a) => a.status === 10).length,
      memberCount: new Set(ads.map((a) => a.memberId)).size,
      monthTaskTotal: tasks.length, monthTaskDone: done,
      monthTaskRate: tasks.length ? Math.round((done / tasks.length) * 100) : null,
      avgResponseMinutes: accepted.length ? Math.round(accepted.reduce((s, x) => s + (x.acceptTime! - x.createTime), 0) / accepted.length / 60000) : null,
      status: 0 // 0 在岗（用户表没有在岗/离岗字段，演示口径）
    }
  })
}

farmStewardManageRouter.get('/page', (req, res) => {
  const kw = q(req, 'keyword')
  const baseId = q(req, 'baseId')
  const rows = buildStewards(tenantOf(req), Date.now())
    .filter((s) => (kw === undefined || like(s.name, kw) || like(s.mobileFull, kw)) && (baseId === undefined || s.baseId === Number(baseId)))
    .map(({ mobileFull: _m, ...rest }) => rest)
  return ok(res, page(req, rows))
})

/** 本月绩效排行 + 今日工作量。绩效只有「任务完成率」一个口径（响应时长、满意度没有评价数据） */
farmStewardManageRouter.get('/overview', (req, res) => {
  const t = tenantOf(req)
  const now = Date.now()
  const rows = buildStewards(t, now)
  const d = db()
  const dayStart = new Date(new Date(now).getFullYear(), new Date(now).getMonth(), new Date(now).getDate()).getTime()
  const ids = new Set(rows.map((s) => s.id))
  return ok(res, {
    ranking: rows.filter((s) => s.monthTaskRate !== null)
      .sort((a, b) => (b.monthTaskRate! - a.monthTaskRate!) || (b.memberCount - a.memberCount))
      .map((s) => ({ id: s.id, name: s.name, score: s.monthTaskRate })),
    today: {
      pendingTasks: d.tasks.filter((x) => x.tenantId === t && x.owner === 'steward' && x.status === 0 && x.stewardId !== null && ids.has(x.stewardId) && x.deadline < dayStart + 24 * 3600_000).length,
      pendingRecords: d.records.filter((r) => r.tenantId === t && r.status === 0).length,
      pendingServiceOrders: d.serviceOrders.filter((s) => s.tenantId === t && s.status === 0).length
    }
  })
})

farmStewardManageRouter.get('/get', (req, res) => {
  const u = own(db().users, req.query.id, tenantOf(req))
  if (!u || u.kind !== 'steward') return notFound(res, '管家')
  const row = buildStewards(tenantOf(req), Date.now()).find((s) => s.id === u.id)!
  const { mobileFull: _m, ...rest } = row
  return ok(res, rest)
})
