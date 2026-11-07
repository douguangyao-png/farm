import { Router, type Request } from 'express'
import { getDb } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { BIZ, q, byNewest, browseTenant, currentMember, own, notFound, badRequest, makeNo } from '../farm/common.js'
import { productView, recordView, shipmentView } from '../farm/views.js'

/**
 * /app-api/farm/**：C 端第二期补页接口（搜索、基地、收藏、地址、消息、农事记录详情、质检报告、物流详情、
 * 转赠、评价、预约到访、在线管家）。不改 app.ts / farm-app.ts，由主线程在 app.ts 里
 * `app.use('/app-api/farm', farmAppExtraRouter)` 注册（须放在 farmAppRouter 之前或之后均可，路径不重叠）。
 * 收藏、地址、评价、预约、转赠、聊天存内存（重启清空），Java 镜像存 farm_app_extra 表。
 */
export const farmAppExtraRouter = Router()

const db = getDb
const login = requireLogin('member')
const mine = (req: Request) => currentMember(req)!

// ---------- 内存数据（按会员） ----------
type Fav = { id: number; memberId: number; type: 'product' | 'base'; targetId: number; createTime: number }
type Addr = { id: number; memberId: number; name: string; mobile: string; detail: string; isDefault: boolean }
type Review = { id: number; tenantId: number; memberId: number; memberName: string; productId: number; adoptionId: number | null; score: number; dims: { freshness: number; service: number; logistics: number }; content: string; createTime: number }
type Visit = { id: number; memberId: number; no: string; baseId: number; baseName: string; date: string; people: number; meal: string; items: string[]; remark: string; status: number; createTime: number }
type Transfer = { id: number; memberId: number; no: string; adoptionId: number; toMobile: string; scope: string; message: string; status: number; createTime: number }
type Chat = { id: number; memberId: number; from: 'me' | 'steward'; content: string; createTime: number }
const favs: Fav[] = []
const addrs: Addr[] = []
const reviews: Review[] = []
const visits: Visit[] = []
const transfers: Transfer[] = []
const chats: Chat[] = []
const readMsgs = new Set<string>() // `${memberId}:${msgKey}`
let seq = 1000
const sid = () => ++seq

// ---------- 搜索 / 商城 ----------
const HOT_WORDS = ['一分地', '跑山鸡', '果树', '水稻', '有机蔬菜', '农资']

farmAppExtraRouter.get('/search/hot', (_req, res) => ok(res, HOT_WORDS))

/** 搜索认养商品和基地；type 为 plot/animal/tree 时只返回该品类（分类商城用同一接口）。sort: default | sold | price */
farmAppExtraRouter.get('/search', (req, res) => {
  const t = browseTenant(req)
  if (!t) return badRequest(res, '缺少租户')
  const kw = (q(req, 'keyword') ?? '').trim()
  const type = q(req, 'type')
  const sort = q(req, 'sort') ?? 'default'
  const hit = (s: string | undefined) => !kw || (s ?? '').includes(kw)
  let products = db().products.filter((p) => p.tenantId === t && p.status === 0 && (!type || p.assetType === type))
    .filter((p) => hit(p.title) || hit(p.description) || hit(db().bases.find((b) => b.id === p.baseId)?.name))
    .map(productView)
  if (sort === 'sold') products = products.sort((a, b) => b.sold - a.sold)
  else if (sort === 'price') products = products.sort((a, b) => a.minPrice - b.minPrice)
  const bases = type ? [] : db().bases.filter((b) => b.tenantId === t && b.status === 0 && (hit(b.name) || hit(b.address) || hit(b.cropSummary)))
    .map(baseCard)
  return ok(res, { products, bases })
})

// ---------- 基地 ----------
/** 评分：由本租户该基地商品的评价算平均；没有评价时不编造，rating=null */
function baseRating(baseId: number) {
  const pids = db().products.filter((p) => p.baseId === baseId).map((p) => p.id)
  const rs = reviews.filter((r) => pids.includes(r.productId))
  if (!rs.length) return { rating: null as number | null, reviewCount: 0 }
  return { rating: Math.round((rs.reduce((s, r) => s + r.score, 0) / rs.length) * 10) / 10, reviewCount: rs.length }
}
function baseCard(b: ReturnType<typeof getDb>['bases'][number]) {
  return {
    id: b.id, name: b.name, cover: b.cover, address: b.address, areaMu: b.areaMu, cropSummary: b.cropSummary, certNo: b.certNo,
    productCount: db().products.filter((p) => p.baseId === b.id && p.status === 0).length,
    ...baseRating(b.id)
  }
}

farmAppExtraRouter.get('/base/list', (req, res) => {
  const t = browseTenant(req)
  if (!t) return badRequest(res, '缺少租户')
  return ok(res, db().bases.filter((b) => b.tenantId === t && b.status === 0).map(baseCard))
})

farmAppExtraRouter.get('/base/get', (req, res) => {
  const t = browseTenant(req)
  const b = t ? own(db().bases, req.query.id, t) : undefined
  if (!b || b.status !== 0) return notFound(res, '基地')
  const arts = db().articles.filter((a) => a.tenantId === b.tenantId && a.status === 0).sort(byNewest).slice(0, 3)
    .map((a) => ({ id: a.id, title: a.title, introduction: a.introduction, picUrl: a.picUrl, createTime: a.createTime }))
  return ok(res, {
    ...baseCard(b),
    lng: b.lng, lat: b.lat,
    products: db().products.filter((p) => p.baseId === b.id && p.status === 0).map(productView),
    // 资质：基地合规编号来自 base.certNo；证照类别为演示文案（正式版取后台「资质证照」）
    certs: [{ name: '基地备案编号', no: b.certNo }, { name: '土地权属证明', no: '演示数据' }, { name: '投入品合规承诺书', no: '演示数据' }],
    // 动态：本农场已发布文章
    news: arts,
    reviews: reviews.filter((r) => db().products.some((p) => p.id === r.productId && p.baseId === b.id)).sort(byNewest).slice(0, 5),
    demo: true
  })
})

// ---------- 收藏 ----------
const favView = (f: Fav) => {
  if (f.type === 'product') {
    const p = db().products.find((x) => x.id === f.targetId)
    return p ? { ...f, product: productView(p) } : null
  }
  const b = db().bases.find((x) => x.id === f.targetId)
  return b ? { ...f, base: baseCard(b) } : null
}
farmAppExtraRouter.get('/favorite/list', login, (req, res) => {
  const m = mine(req)
  const type = q(req, 'type')
  return ok(res, favs.filter((f) => f.memberId === m.id && (!type || f.type === type)).sort(byNewest).map(favView).filter(Boolean))
})
farmAppExtraRouter.get('/favorite/check', login, (req, res) => {
  const m = mine(req)
  return ok(res, favs.some((f) => f.memberId === m.id && f.type === q(req, 'type') && f.targetId === Number(req.query.targetId)))
})
/** 收藏 / 取消收藏（切换）。返回切换后的状态 */
farmAppExtraRouter.post('/favorite/toggle', login, (req, res) => {
  const m = mine(req)
  const { type, targetId } = req.body ?? {}
  if (type !== 'product' && type !== 'base') return badRequest(res, '收藏类型不对')
  const tid = Number(targetId)
  const exists = type === 'product' ? own(db().products, tid, m.tenantId) : own(db().bases, tid, m.tenantId)
  if (!exists) return notFound(res, type === 'product' ? '商品' : '基地')
  const i = favs.findIndex((f) => f.memberId === m.id && f.type === type && f.targetId === tid)
  if (i >= 0) { favs.splice(i, 1); return ok(res, false) }
  favs.push({ id: sid(), memberId: m.id, type, targetId: tid, createTime: Date.now() })
  return ok(res, true)
})

// ---------- 收货地址 ----------
farmAppExtraRouter.get('/address/list', login, (req, res) => {
  const m = mine(req)
  const list = addrs.filter((a) => a.memberId === m.id)
  // 会员资料里已有的地址作为默认地址带出（只读一次，不入库）
  if (!list.length && m.address) list.push({ id: 0, memberId: m.id, name: m.nickname, mobile: m.mobile, detail: m.address, isDefault: true })
  return ok(res, list.sort((a, b) => Number(b.isDefault) - Number(a.isDefault)))
})
farmAppExtraRouter.post('/address/save', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const name = String(b.name ?? '').trim()
  const mobile = String(b.mobile ?? '').trim()
  const detail = String(b.detail ?? '').trim()
  if (!name) return badRequest(res, '请填写收货人')
  if (!/^1\d{10}$/.test(mobile)) return badRequest(res, '手机号格式不对')
  if (!detail) return badRequest(res, '请填写详细地址')
  const mine_ = addrs.filter((a) => a.memberId === m.id)
  const isDefault = b.isDefault === true || mine_.length === 0
  if (isDefault) mine_.forEach((a) => (a.isDefault = false))
  let a = b.id ? mine_.find((x) => x.id === Number(b.id)) : undefined
  if (a) Object.assign(a, { name, mobile, detail, isDefault })
  else { a = { id: sid(), memberId: m.id, name, mobile, detail, isDefault }; addrs.push(a) }
  return ok(res, a.id)
})
farmAppExtraRouter.post('/address/delete', login, (req, res) => {
  const m = mine(req)
  const i = addrs.findIndex((a) => a.id === Number(req.body?.id) && a.memberId === m.id)
  if (i < 0) return notFound(res, '地址')
  const wasDefault = addrs[i].isDefault
  addrs.splice(i, 1)
  const rest = addrs.find((a) => a.memberId === m.id)
  if (wasDefault && rest) rest.isDefault = true
  return ok(res, true)
})

// ---------- 消息中心（由现有数据派生，已读状态存内存） ----------
type Msg = { key: string; type: 'system' | 'logistics' | 'farming' | 'interact'; title: string; content: string; createTime: number; link: string; read: boolean }
function messagesOf(m: ReturnType<typeof mine>): Msg[] {
  const out: Omit<Msg, 'read'>[] = []
  const myAd = db().adoptions.filter((a) => a.memberId === m.id)
  for (const o of db().orders.filter((x) => x.memberId === m.id && x.status >= 10)) {
    out.push({ key: 'o' + o.id, type: 'system', title: '认养成功', content: `订单 ${o.no} 已支付，合同已归档，管家会尽快联系您。`, createTime: o.payTime ?? o.createTime, link: '/pages/farm/contract/list' })
  }
  for (const s of db().shipments.filter((x) => x.memberId === m.id)) {
    const last = s.tracks[s.tracks.length - 1]
    out.push({ key: 's' + s.id, type: 'logistics', title: s.status === 20 ? '配送已签收' : '配送进度更新', content: `${s.carrier} ${s.logisticsNo}：${last?.content ?? '待发货'}`, createTime: last?.time ?? s.createTime, link: `/pages/farm/logistics/detail?id=${s.id}` })
  }
  for (const r of db().records.filter((x) => x.status === 10 && myAd.some((a) => a.id === x.adoptionId))) {
    out.push({ key: 'r' + r.id, type: 'farming', title: '新的农事记录', content: r.title, createTime: r.createTime, link: `/pages/farm/record/detail?id=${r.id}` })
  }
  for (const c of chats.filter((x) => x.memberId === m.id && x.from === 'steward')) {
    out.push({ key: 'c' + c.id, type: 'interact', title: '管家回复了您', content: c.content, createTime: c.createTime, link: '/pages/farm/service/chat' })
  }
  for (const t of transfers.filter((x) => x.memberId === m.id)) {
    out.push({ key: 't' + t.id, type: 'interact', title: '转赠申请已提交', content: `转赠给 ${t.toMobile.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')}，等待对方确认`, createTime: t.createTime, link: '/pages/farm/transfer/index?adoptionId=' + t.adoptionId })
  }
  return out.sort(byNewest).map((x) => ({ ...x, read: readMsgs.has(`${m.id}:${x.key}`) }))
}
farmAppExtraRouter.get('/message/list', login, (req, res) => {
  const type = q(req, 'type')
  const all = messagesOf(mine(req))
  return ok(res, { unread: all.filter((x) => !x.read).length, list: all.filter((x) => !type || x.type === type) })
})
/** 标记已读：传 key 读一条，不传全部已读 */
farmAppExtraRouter.post('/message/read', login, (req, res) => {
  const m = mine(req)
  const key = req.body?.key
  for (const x of messagesOf(m)) if (!key || x.key === key) readMsgs.add(`${m.id}:${x.key}`)
  return ok(res, true)
})

// ---------- 农事记录详情 ----------
farmAppExtraRouter.get('/record/get', login, (req, res) => {
  const m = mine(req)
  const r = own(db().records, req.query.id, m.tenantId)
  const ad = r && db().adoptions.find((a) => a.id === r.adoptionId)
  if (!r || !ad || ad.memberId !== m.id || r.status !== 10) return notFound(res, '农事记录')
  const v = recordView(r)
  return ok(res, {
    id: r.id, adoptionId: r.adoptionId, assetCode: v.assetCode, title: r.title, content: r.content, kindName: v.kindName, photos: r.photos,
    stewardName: v.stewardName, env: r.env, lng: r.lng, lat: r.lat, createTime: r.createTime,
    // 存证编号由记录 id 派生（演示：正式版为区块链存证哈希）；校验项取机器校验结果
    evidenceNo: 'CZ' + String(r.id).padStart(8, '0'),
    checks: r.checks, machineCheck: v.machineCheck,
    // 工时：原型有该字段，系统没有采集，不编造
    workHours: null,
    demo: true
  })
})

// ---------- 质检报告（演示数据：第三方检测由基地上传，系统暂无该数据） ----------
farmAppExtraRouter.get('/report/get', login, (req, res) => {
  const m = mine(req)
  const ad = own(db().adoptions, req.query.adoptionId, m.tenantId)
  if (!ad || ad.memberId !== m.id) return notFound(res, '认养')
  const asset = db().assets.find((a) => a.id === ad.assetId)
  const crop = asset?.specs.crop ?? asset?.specs.breed ?? '农产品'
  return ok(res, {
    adoptionId: ad.id, assetCode: asset?.code ?? '', crop,
    reportNo: 'JC' + String(ad.id).padStart(6, '0'), org: '演示检测机构', sampleDate: ad.startAt, conclusion: '合格',
    items: [
      { name: '毒死蜱', limit: '≤ 0.01 mg/kg', result: '未检出', pass: true },
      { name: '氯氰菊酯', limit: '≤ 0.5 mg/kg', result: '未检出', pass: true },
      { name: '铅（以 Pb 计）', limit: '≤ 0.1 mg/kg', result: '0.02 mg/kg', pass: true },
      { name: '镉（以 Cd 计）', limit: '≤ 0.05 mg/kg', result: '0.01 mg/kg', pass: true }
    ],
    demo: true
  })
})

// ---------- 物流详情 ----------
farmAppExtraRouter.get('/logistics/get', login, (req, res) => {
  const m = mine(req)
  const s = own(db().shipments, req.query.id, m.tenantId)
  if (!s || s.memberId !== m.id) return notFound(res, '配送单')
  const h = db().harvests.find((x) => x.id === s.harvestId)
  const nth = db().shipments.filter((x) => x.adoptionId === s.adoptionId && x.id <= s.id).length
  const ad = db().adoptions.find((a) => a.id === s.adoptionId)
  const st = ad && db().users.find((u) => u.id === ad.stewardId)
  return ok(res, {
    ...shipmentView(s), nth,
    items: [{ name: h?.crop ?? '农产品', weightJin: s.weightJin }],
    steward: st ? { name: st.nickname, mobile: st.mobile ?? '' } : null
  })
})

// ---------- 转赠 ----------
farmAppExtraRouter.get('/transfer/list', login, (req, res) => {
  const m = mine(req)
  return ok(res, transfers.filter((t) => t.memberId === m.id).sort(byNewest))
})
farmAppExtraRouter.post('/transfer/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const ad = own(db().adoptions, b.adoptionId, m.tenantId)
  if (!ad || ad.memberId !== m.id) return notFound(res, '认养')
  if (ad.status !== 10) return fail(res, BIZ.ORDER_STATE, '只有认养中的订单能转赠')
  if (!/^1\d{10}$/.test(String(b.toMobile ?? ''))) return badRequest(res, '受赠人手机号格式不对')
  if (b.toMobile === m.mobile) return badRequest(res, '不能转赠给自己')
  if (transfers.some((t) => t.adoptionId === ad.id && t.status === 0)) return fail(res, BIZ.ORDER_STATE, '该认养已有待确认的转赠')
  const t: Transfer = { id: sid(), memberId: m.id, no: makeNo('ZZ'), adoptionId: ad.id, toMobile: String(b.toMobile), scope: b.scope === 'right' ? 'right' : 'all', message: String(b.message ?? ''), status: 0, createTime: Date.now() }
  transfers.push(t)
  return ok(res, { id: t.id, no: t.no })
})

// ---------- 评价 ----------
farmAppExtraRouter.get('/review/list', (req, res) => {
  const t = browseTenant(req)
  const pid = Number(req.query.productId)
  return ok(res, reviews.filter((r) => r.tenantId === t && (!pid || r.productId === pid)).sort(byNewest))
})
farmAppExtraRouter.post('/review/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const ad = own(db().adoptions, b.adoptionId, m.tenantId)
  if (!ad || ad.memberId !== m.id) return notFound(res, '认养')
  const score = Number(b.score)
  if (!(score >= 1 && score <= 5)) return badRequest(res, '请打分')
  if (reviews.some((r) => r.adoptionId === ad.id && r.memberId === m.id)) return fail(res, BIZ.ORDER_STATE, '这次认养已经评价过了')
  const dim = (v: unknown) => { const n = Number(v); return n >= 1 && n <= 5 ? n : score }
  const r: Review = {
    id: sid(), tenantId: m.tenantId, memberId: m.id, memberName: m.nickname.slice(0, 1) + '**', productId: ad.productId, adoptionId: ad.id, score,
    dims: { freshness: dim(b.dims?.freshness), service: dim(b.dims?.service), logistics: dim(b.dims?.logistics) },
    content: String(b.content ?? '').slice(0, 500), createTime: Date.now()
  }
  reviews.push(r)
  return ok(res, r.id)
})
farmAppExtraRouter.get('/review/mine', login, (req, res) => ok(res, reviews.filter((r) => r.memberId === mine(req).id).sort(byNewest)))

// ---------- 预约到访 ----------
farmAppExtraRouter.get('/visit/list', login, (req, res) => ok(res, visits.filter((v) => v.memberId === mine(req).id).sort(byNewest)))
farmAppExtraRouter.post('/visit/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const base = own(db().bases, b.baseId, m.tenantId)
  if (!base) return notFound(res, '基地')
  const date = String(b.date ?? '')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return badRequest(res, '请选择到访日期')
  if (new Date(date + 'T00:00:00').getTime() < new Date(new Date().toDateString()).getTime()) return badRequest(res, '到访日期不能早于今天')
  const people = Number(b.people)
  if (!(people >= 1 && people <= 20)) return badRequest(res, '人数需在 1 到 20 之间')
  const v: Visit = { id: sid(), memberId: m.id, no: makeNo('FW'), baseId: base.id, baseName: base.name, date, people, meal: String(b.meal ?? 'none'), items: Array.isArray(b.items) ? b.items.map(String) : [], remark: String(b.remark ?? ''), status: 0, createTime: Date.now() }
  visits.push(v)
  return ok(res, { id: v.id, no: v.no })
})

// ---------- 在线管家 ----------
const STEWARD_REPLY = '收到，我马上帮您看一下，稍后在这里回复您。（演示：管家端接入后由真人回复）'
farmAppExtraRouter.get('/service/messages', login, (req, res) => {
  const m = mine(req)
  const ad = db().adoptions.filter((a) => a.memberId === m.id).sort((a, b) => b.startAt - a.startAt)[0]
  const st = ad && db().users.find((u) => u.id === ad.stewardId)
  return ok(res, {
    steward: st ? { name: st.nickname, mobile: st.mobile ?? '' } : null,
    list: chats.filter((c) => c.memberId === m.id).sort((a, b) => a.createTime - b.createTime)
  })
})
farmAppExtraRouter.post('/service/send', login, (req, res) => {
  const m = mine(req)
  const content = String(req.body?.content ?? '').trim()
  if (!content) return badRequest(res, '请输入内容')
  const now = Date.now()
  chats.push({ id: sid(), memberId: m.id, from: 'me', content: content.slice(0, 500), createTime: now })
  chats.push({ id: sid(), memberId: m.id, from: 'steward', content: STEWARD_REPLY, createTime: now + 1 })
  return ok(res, true)
})

