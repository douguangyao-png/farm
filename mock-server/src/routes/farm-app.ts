import { Router, type Request } from 'express'
import { getDb, nextId, placeholderImage, type Adoption, type CareMode } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { BIZ, q, byNewest, browseTenant, currentMember, own, notFound, badRequest, envView, makeNo, CARE_MODE_NAME } from '../farm/common.js'
import { productView, orderView, contractOf, adoptionView, adoptionDetail, recordView, supplyOrderView, shipmentView } from '../farm/views.js'

/** /app-api/farm/**：C 端（契约第 4 节） */
export const farmAppRouter = Router()

const db = getDb
const HOUR = 3600_000
const login = requireLogin('member')
const mine = (req: Request) => currentMember(req)!

const needTenant = (req: Request) => browseTenant(req)

// ---------- 浏览（不需要登录） ----------
farmAppRouter.get('/home', (req, res) => {
  const t = needTenant(req)
  if (!t) return badRequest(res, '缺少租户')
  const tenant = db().tenants.find((x) => x.id === t)
  return ok(res, {
    tenantName: tenant?.name ?? '',
    banners: [
      { image: placeholderImage('一块自己的菜地', '#2e7d32'), title: '一分菜地 · 年度认养', url: '/pages/farm/product/detail?id=1' },
      { image: placeholderImage('林下跑山鸡', '#f9a825'), title: '安吉跑山鸡 · 认养一只', url: '/pages/farm/product/detail?id=2' }
    ],
    bases: db().bases.filter((b) => b.tenantId === t && b.status === 0).map((b) => ({ id: b.id, name: b.name, cover: b.cover, address: b.address, live: true })),
    products: db().products.filter((p) => p.tenantId === t && p.status === 0).map(productView),
    // 农场资讯：只放本农场已发布（status=0）的文章；平台的公告、帮助是给经营者看的，在后台工作台展示
    articles: db().articles.filter((a) => a.tenantId === t && a.status === 0).sort(byNewest).slice(0, 5)
      .map((a) => ({ id: a.id, title: a.title, introduction: a.introduction, picUrl: a.picUrl, createTime: a.createTime })),
    demo: true
  })
})

farmAppRouter.get('/product/get', (req, res) => {
  const t = needTenant(req)
  const p = t ? own(db().products, req.query.id, t) : undefined
  if (!p || p.status !== 0) return notFound(res, '认养商品')
  const base = db().bases.find((b) => b.id === p.baseId)
  const rows = p.careModes[0].duty.map((d, i) => ({
    item: d.item,
    ...Object.fromEntries(p.careModes.map((m) => [m.mode, m.enabled ? (m.duty[i]?.owner === 'member' ? '您来做' : '农场做') : '—']))
  }))
  return ok(res, { ...productView(p), base: base ? { name: base.name, address: base.address, certNo: base.certNo } : null, compareRows: rows })
})

farmAppRouter.get('/supply/list', (req, res) => {
  const t = needTenant(req)
  const cat = q(req, 'cat')
  return ok(res, db().supplies.filter((s) => s.tenantId === t && s.status === 0 && s.retailAllowed && (cat === undefined || s.cat === cat)))
})
farmAppRouter.get('/supply/get', (req, res) => {
  const t = needTenant(req)
  const s = t ? own(db().supplies, req.query.id, t) : undefined
  return s && s.status === 0 && s.retailAllowed ? ok(res, s) : notFound(res, '农资')
})

// ---------- 认养下单、支付 ----------
farmAppRouter.post('/order/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const p = own(db().products, b.productId, m.tenantId)
  if (!p || p.status !== 0) return badRequest(res, '商品已下架')
  const mode = p.careModes.find((x) => x.mode === b.careMode && x.enabled)
  if (!mode) return badRequest(res, '请选择托管方式')
  if (b.contractAgreed !== true) return fail(res, BIZ.CONTRACT_NOT_AGREED, '请先阅读并勾选认养合同')
  const o = {
    id: nextId(db().orders), tenantId: m.tenantId, no: makeNo('RY'), memberId: m.id, productId: p.id, careMode: mode.mode, amount: mode.price, status: 0,
    assetId: null, adoptionId: null, contractVersion: mode.contractVersion, contractAgreed: true,
    receiverName: b.receiverName || m.nickname, receiverMobile: b.receiverMobile || m.mobile, address: b.address || m.address || '', createTime: Date.now(), payTime: null
  }
  db().orders.push(o)
  return ok(res, { id: o.id, no: o.no, amount: o.amount })
})

/** 按托管方式生成自管任务：全托管 0 项，半托管 2 项，不托管 5 项（文档 10「每周待办」；R-4） */
const SELF_TASKS: Record<CareMode, { title: string; description: string; afterH: number; price: number }[]> = {
  full: [],
  semi: [
    { title: '浇水', description: '新地块，先浇一次透水。半托管下浇水由您负责。', afterH: 24, price: 1500 },
    { title: '除草', description: '行间杂草清一遍。', afterH: 72, price: 3000 }
  ],
  self: [
    { title: '整地', description: '翻地、起垄，农场可借用工具。', afterH: 24, price: 6000 },
    { title: '播种', description: '按种植计划播种，种子可在农资商城购买。', afterH: 48, price: 4000 },
    { title: '浇水', description: '播种后浇透水。', afterH: 50, price: 1500 },
    { title: '除草', description: '行间杂草清一遍。', afterH: 96, price: 3000 },
    { title: '追肥', description: '每株追施有机肥一把。', afterH: 120, price: 2000 }
  ]
}

function promisedJinOf(text: string | undefined): number | null {
  const m = /约定交付[^\d]*(\d+(\.\d+)?)\s*斤/.exec(text ?? '')
  return m ? Number(m[1]) : null
}

farmAppRouter.post('/order/pay', login, (req, res) => {
  const m = mine(req)
  const o = own(db().orders, req.body?.id, m.tenantId)
  if (!o || o.memberId !== m.id) return notFound(res, '订单')
  if (o.status !== 0) return fail(res, BIZ.ORDER_STATE, '订单状态已变化，请刷新')
  const p = db().products.find((x) => x.id === o.productId)!
  const asset = db().assets.find((a) => a.tenantId === o.tenantId && a.baseId === p.baseId && a.assetType === p.assetType && a.status === 0)
  if (!asset) return fail(res, BIZ.NO_IDLE_ASSET, '这个商品暂时没有空闲的地块')
  const steward = db().users.find((u) => u.kind === 'steward' && u.baseId === p.baseId) ?? db().users.find((u) => u.kind === 'steward' && u.tenantId === o.tenantId)
  const now = Date.now()
  const mode = p.careModes.find((x) => x.mode === o.careMode)!
  const ad: Adoption = {
    id: nextId(db().adoptions), tenantId: o.tenantId, orderId: o.id, memberId: m.id, productId: p.id, assetId: asset.id, careMode: o.careMode,
    startAt: now, endAt: now + p.cycleDays * 24 * HOUR, status: 10, promisedJin: o.careMode === 'full' ? promisedJinOf(mode.outputPromise) : null, harvestedJin: 0,
    stewardId: steward?.id ?? 0
  }
  db().adoptions.push(ad)
  o.status = 10
  o.payTime = now
  o.assetId = asset.id
  o.adoptionId = ad.id
  asset.status = 10
  p.sold += 1
  p.stock = Math.max(0, p.stock - 1)
  for (const t of SELF_TASKS[o.careMode]) {
    db().tasks.push({ id: nextId(db().tasks), tenantId: o.tenantId, adoptionId: ad.id, assetId: asset.id, owner: 'member', stewardId: null, title: t.title, description: t.description,
      source: 'calendar', serviceOrderId: null, deadline: now + t.afterH * HOUR, status: 0, entrustPrice: t.price, finishTime: null, createTime: now })
  }
  if (steward) {
    db().tasks.push({ id: nextId(db().tasks), tenantId: o.tenantId, adoptionId: ad.id, assetId: asset.id, owner: 'steward', stewardId: steward.id,
      title: `${asset.code} 新认养交接`, description: `${m.nickname}认养了 ${asset.code}（${CARE_MODE_NAME[o.careMode]}），请插认养牌并拍照。`, source: 'manual',
      serviceOrderId: null, deadline: now + 24 * HOUR, status: 0, entrustPrice: null, finishTime: null, createTime: now })
  }
  return ok(res, { orderId: o.id, adoptionId: ad.id, assetCode: asset.code })
})

farmAppRouter.get('/order/get', login, (req, res) => {
  const m = mine(req)
  const o = own(db().orders, req.query.id, m.tenantId)
  if (!o || o.memberId !== m.id) return notFound(res, '订单')
  return ok(res, { ...orderView(o), contract: contractOf(o) })
})
farmAppRouter.get('/order/list', login, (req, res) => {
  const m = mine(req)
  return ok(res, db().orders.filter((o) => o.memberId === m.id && o.tenantId === m.tenantId).sort(byNewest).map(orderView))
})

// ---------- 我的认养 ----------
function myAdoption(req: Request, id: unknown) {
  const m = mine(req)
  const ad = own(db().adoptions, id, m.tenantId)
  return ad && ad.memberId === m.id ? ad : undefined
}
farmAppRouter.get('/adoption/list', login, (req, res) => {
  const m = mine(req)
  return ok(res, db().adoptions.filter((a) => a.memberId === m.id && a.tenantId === m.tenantId).sort((a, b) => b.startAt - a.startAt).map(adoptionView))
})
farmAppRouter.get('/adoption/get', login, (req, res) => {
  const ad = myAdoption(req, req.query.id)
  return ad ? ok(res, adoptionDetail(ad)) : notFound(res, '认养')
})
farmAppRouter.get('/adoption/records', login, (req, res) => {
  const ad = myAdoption(req, req.query.adoptionId)
  if (!ad) return notFound(res, '认养')
  return ok(res, db().records.filter((r) => r.adoptionId === ad.id && r.status === 10).sort(byNewest).map(recordView))
})
farmAppRouter.get('/adoption/trace', login, (req, res) => {
  const ad = myAdoption(req, req.query.adoptionId)
  if (!ad) return notFound(res, '认养')
  const records = db().records.filter((r) => r.adoptionId === ad.id && r.status === 10).sort((a, b) => a.createTime - b.createTime)
  const inputs = db().inputRecords.filter((x) => x.assetId === ad.assetId).sort((a, b) => a.usedAt - b.usedAt)
  const harvests = db().harvests.filter((h) => h.adoptionId === ad.id)
  const shipments = db().shipments.filter((s) => s.adoptionId === ad.id)
  const sow = records.find((r) => /播种|定植/.test(r.title))
  const untraced = db().supplyOrders.filter((o) => o.adoptionId === ad.id && o.traceState === 0).length
  const stages = [
    { key: 'seed', title: '种源', time: ad.startAt, content: '种苗来自安吉县农业良种场，批次 ZM-2026-09（演示数据）' },
    { key: 'sow', title: '播种', time: sow?.createTime ?? null, content: sow ? sow.content : '待播种' },
    { key: 'grow', title: '生长', time: records.at(-1)?.createTime ?? null, content: `已记录 ${records.length} 次农事，每次带照片、定位和环境数据` },
    { key: 'input', title: '投入品', time: inputs.at(-1)?.usedAt ?? null, content: `农场投入 ${inputs.filter((x) => x.source === 'farm').length} 次，认养人自购 ${inputs.filter((x) => x.source === 'member').length} 次` + (untraced ? `；${untraced} 笔自购农资待登记，档案标「待补充」` : '') },
    { key: 'harvest', title: '采收', time: harvests.at(-1)?.harvestDate ?? null, content: harvests.length ? `已采收 ${harvests.length} 批，共 ${ad.harvestedJin} 斤` : '还没到采收期' },
    { key: 'ship', title: '配送', time: shipments.at(-1)?.createTime ?? null, content: shipments.length ? `已配送 ${shipments.length} 次，全程冷链` : '还没有配送' }
  ]
  return ok(res, { stages, inputs, pendingTrace: untraced > 0 })
})
farmAppRouter.get('/adoption/env', login, (req, res) => {
  const ad = myAdoption(req, req.query.adoptionId)
  if (!ad) return notFound(res, '认养')
  return ok(res, envView(db().assets.find((a) => a.id === ad.assetId)!))
})
farmAppRouter.get('/adoption/camera', login, (req, res) => {
  const ad = myAdoption(req, req.query.adoptionId)
  if (!ad) return notFound(res, '认养')
  const a = db().assets.find((x) => x.id === ad.assetId)!
  return ok(res, { name: a.cameraName ?? '暂无摄像头', online: !!a.cameraName, poster: placeholderImage(`${a.code} 实时画面`, '#37474f'), tip: '演示环境为视频占位，正式版接入萤石 / 海康直播流' })
})

// ---------- 自管任务 ----------
farmAppRouter.get('/self-task/list', login, (req, res) => {
  const m = mine(req)
  const adIds = db().adoptions.filter((a) => a.memberId === m.id).map((a) => a.id)
  const adoptionId = q(req, 'adoptionId')
  const status = q(req, 'status')
  const now = Date.now()
  const rows = db().tasks.filter((t) => t.owner === 'member' && t.adoptionId !== null && adIds.includes(t.adoptionId) && (adoptionId === undefined || t.adoptionId === Number(adoptionId))
    && (status === undefined || t.status === Number(status)))
    .map((t) => ({ id: t.id, adoptionId: t.adoptionId, assetCode: db().assets.find((a) => a.id === t.assetId)?.code ?? '', title: t.title, description: t.description,
      deadline: t.deadline, status: t.status, overdue: t.status === 0 && t.deadline < now, entrustPrice: t.entrustPrice, finishTime: t.finishTime, serviceOrderId: t.serviceOrderId }))
    .sort((a, b) => Number(b.overdue) - Number(a.overdue) || a.deadline - b.deadline)
  return ok(res, rows)
})
function mySelfTask(req: Request, id: unknown) {
  const m = mine(req)
  const t = db().tasks.find((x) => x.id === Number(id) && x.owner === 'member' && x.tenantId === m.tenantId)
  const ad = t && db().adoptions.find((a) => a.id === t.adoptionId)
  return t && ad?.memberId === m.id ? t : undefined
}
farmAppRouter.post('/self-task/done', login, (req, res) => {
  const t = mySelfTask(req, req.body?.id)
  if (!t) return notFound(res, '任务')
  if (t.status !== 0) return fail(res, BIZ.TASK_STATE, '任务状态已变化，请刷新')
  t.status = 10
  t.finishTime = Date.now()
  return ok(res, true)
})
farmAppRouter.post('/self-task/entrust', login, (req, res) => {
  const t = mySelfTask(req, req.body?.id)
  if (!t) return notFound(res, '任务')
  if (t.status !== 0) return fail(res, BIZ.TASK_STATE, '任务状态已变化，请刷新')
  const m = mine(req)
  const so = { id: nextId(db().serviceOrders), tenantId: t.tenantId, no: makeNo('DG'), fromTaskId: t.id, taskTitle: t.title, adoptionId: t.adoptionId, assetId: t.assetId,
    memberId: m.id, stewardId: null, serviceType: `代${t.title}`, price: t.entrustPrice ?? 3000, status: 0, split: { steward: 70, farm: 20, platform: 10 },
    proofPhotos: [], remark: `认养人委托：${t.description}`, createTime: Date.now(), acceptTime: null, finishTime: null }
  db().serviceOrders.push(so)
  t.status = 20
  t.serviceOrderId = so.id
  return ok(res, { serviceOrderId: so.id, no: so.no, price: so.price })
})

// ---------- 农资下单 ----------
farmAppRouter.post('/supply-order/create', login, (req, res) => {
  const m = mine(req)
  const b = req.body ?? {}
  const spu = own(db().supplies, b.spuId, m.tenantId)
  if (!spu || spu.status !== 0) return badRequest(res, '农资已下架')
  if (!spu.retailAllowed) return fail(res, BIZ.SUPPLY_NOT_RETAIL, '该农资不能零售，只能由农场代施')
  if (!b.adoptionId) return fail(res, BIZ.SUPPLY_NEED_ADOPTION, '请选择要使用这份农资的认养地块')
  const ad = myAdoption(req, b.adoptionId)
  if (!ad) return fail(res, BIZ.SUPPLY_NEED_ADOPTION, '请选择要使用这份农资的认养地块')
  const qty = Number(b.qty)
  if (!Number.isInteger(qty) || qty < 1) return badRequest(res, '数量至少 1 件')
  if (qty > spu.stock) return badRequest(res, `库存只剩 ${spu.stock} 件`)
  const now = Date.now()
  const o = { id: nextId(db().supplyOrders), tenantId: m.tenantId, no: makeNo('NZ'), memberId: m.id, adoptionId: ad.id, assetId: ad.assetId, spuId: spu.id, qty, amount: spu.price * qty,
    deliverWay: ['store', 'express', 'pickup', 'apply'].includes(b.deliverWay) ? b.deliverWay : 'store', batchNo: spu.batchNo,
    traceState: spu.cat === 'svc' || spu.cat === 'ins' ? 10 : 0, usedAt: null, dosage: null, createTime: now }
  db().supplyOrders.push(o)
  spu.stock -= qty
  spu.sold += qty
  // 代管服务、生物防治类：自动生成一张待接单的代管工单（文档 10 need_workorder）
  if (spu.needWorkorder) {
    db().serviceOrders.push({ id: nextId(db().serviceOrders), tenantId: m.tenantId, no: makeNo('DG'), fromTaskId: null, taskTitle: spu.name, adoptionId: ad.id, assetId: ad.assetId,
      memberId: m.id, stewardId: null, serviceType: spu.name.replace(/（.*?）/g, ''), price: spu.price * qty, status: 0, split: { steward: 70, farm: 20, platform: 10 },
      proofPhotos: [], remark: `农资订单 ${o.no} 自动生成`, createTime: now, acceptTime: null, finishTime: null })
  }
  return ok(res, { id: o.id, no: o.no, amount: o.amount })
})
farmAppRouter.get('/supply-order/list', login, (req, res) => {
  const m = mine(req)
  return ok(res, db().supplyOrders.filter((o) => o.memberId === m.id).sort(byNewest).map(supplyOrderView))
})

// ---------- 收获配送 ----------
farmAppRouter.get('/shipment/list', login, (req, res) => {
  const m = mine(req)
  const adoptionId = q(req, 'adoptionId')
  if (adoptionId !== undefined && !myAdoption(req, adoptionId)) return notFound(res, '认养')
  return ok(res, db().shipments.filter((s) => s.memberId === m.id && (adoptionId === undefined || s.adoptionId === Number(adoptionId))).sort(byNewest).map(shipmentView))
})


// ---------- 农场资讯（经营者在「系统管理 → 文章管理」发布的文章） ----------
farmAppRouter.get('/article/list', (req, res) => {
  const t = needTenant(req)
  return ok(res, db().articles.filter((a) => a.tenantId === t && a.status === 0).sort(byNewest)
    .map((a) => ({ id: a.id, title: a.title, introduction: a.introduction, picUrl: a.picUrl, author: a.author, browseCount: a.browseCount, createTime: a.createTime })))
})
farmAppRouter.get('/article/get', (req, res) => {
  const t = needTenant(req)
  const a = t ? own(db().articles, req.query.id, t) : undefined
  if (!a || a.status !== 0) return notFound(res, '文章')
  a.browseCount += 1
  return ok(res, { ...a, categoryName: db().articleCategories.find((c) => c.id === a.categoryId)?.name ?? '' })
})
