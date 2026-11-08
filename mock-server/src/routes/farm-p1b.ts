import { Router } from 'express'
import { getDb, nextId, type ShipBatch, type LivestockBatch, type Order } from '../store.js'
import { ok, fail, requireLogin } from '../http.js'
import { BIZ, page, q, like, eqNum, byNewest, tenantOf, own, notFound, badRequest, makeNo, currentMember, CARE_MODE_NAME } from '../farm/common.js'
import { shipmentView, harvestView, contractOf, contractStatusOf, orderView } from '../farm/views.js'

/**
 * 第一期补齐（docs/plans/第一期补齐-2026-11-08.md）：配送批次、畜禽批次（后台）；合同签名、收成详情（C 端）。
 * Java 镜像：FarmShipBatchService / FarmLivestockBatchService / FarmAppContractService / FarmAppHarvestService。
 */
const db = getDb

// =============================== 配送批次 /admin-api/farm/ship-batch ===============================
export const farmShipBatchRouter = Router()
farmShipBatchRouter.use(requireLogin('admin'))

function batchView(b: ShipBatch) {
  const ships = db().shipments.filter((s) => s.batchId === b.id)
  const h = db().harvests.find((x) => x.id === b.harvestId)
  const asset = db().assets.find((a) => a.id === h?.assetId)
  return {
    ...b,
    harvestCrop: h?.crop ?? '',
    assetCode: asset?.code ?? '',
    shipmentCount: ships.length,
    weightJin: Math.round(ships.reduce((s, x) => s + x.weightJin, 0) * 10) / 10
  }
}

farmShipBatchRouter.get('/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().shipBatches.filter((b) => b.tenantId === t && eqNum(b.status, q(req, 'status')) && like(b.no, q(req, 'no')))
    .sort(byNewest).map(batchView)
  return ok(res, page(req, rows))
})
farmShipBatchRouter.get('/get', (req, res) => {
  const b = own(db().shipBatches, req.query.id, tenantOf(req))
  if (!b) return notFound(res, '配送批次')
  return ok(res, { ...batchView(b), shipments: db().shipments.filter((s) => s.batchId === b.id).map(shipmentView) })
})
farmShipBatchRouter.post('/create', (req, res) => {
  const t = tenantOf(req)
  const body = req.body ?? {}
  const h = own(db().harvests, body.harvestId, t)
  if (!h) return badRequest(res, '请选择收成')
  const planDate = Number(body.planDate)
  if (!(planDate > 0)) return badRequest(res, '请选择计划发货日期')
  const remark = String(body.remark ?? '').trim()
  if (remark.length > 255) return badRequest(res, '备注不能超过 255 个字')
  const ids: number[] = Array.isArray(body.shipmentIds) ? body.shipmentIds.map(Number) : []
  if (!ids.length) return badRequest(res, '请选择配送单')
  if (new Set(ids).size !== ids.length) return badRequest(res, '配送单重复')
  const ships = ids.map((id) => own(db().shipments, id, t))
  if (ships.some((s) => !s)) return badRequest(res, '配送单不存在')
  if (ships.some((s) => s!.batchId)) return badRequest(res, '有配送单已在其它批次里')
  const b: ShipBatch = { id: nextId(db().shipBatches), tenantId: t, no: makeNo('PC'), harvestId: h.id, planDate, status: 0, remark, createTime: Date.now() }
  db().shipBatches.push(b)
  for (const s of ships) s!.batchId = b.id
  return ok(res, b.id)
})
farmShipBatchRouter.put('/ship', (req, res) => {
  const t = tenantOf(req)
  const b = own(db().shipBatches, req.body?.id, t)
  if (!b) return notFound(res, '配送批次')
  if (b.status !== 0) return badRequest(res, '只有待发的批次能发货')
  const now = Date.now()
  for (const s of db().shipments.filter((x) => x.batchId === b.id && x.status === 0)) {
    s.status = 10
    s.tracks.push({ time: now, content: `批次 ${b.no} 已发出` })
  }
  b.status = 10
  return ok(res, true)
})
farmShipBatchRouter.put('/finish', (req, res) => {
  const b = own(db().shipBatches, req.body?.id, tenantOf(req))
  if (!b) return notFound(res, '配送批次')
  if (b.status !== 10) return badRequest(res, '只有已发出的批次能完成')
  b.status = 20
  return ok(res, true)
})

// =============================== 畜禽批次 /admin-api/farm/livestock-batch ===============================
export const farmLivestockBatchRouter = Router()
farmLivestockBatchRouter.use(requireLogin('admin'))

const lbView = (b: LivestockBatch) => ({ ...b, baseName: db().bases.find((x) => x.id === b.baseId)?.name ?? '', remaining: b.count - b.outCount })

farmLivestockBatchRouter.get('/page', (req, res) => {
  const t = tenantOf(req)
  const rows = db().livestockBatches.filter((b) => b.tenantId === t && like(b.species, q(req, 'species')) && eqNum(b.status, q(req, 'status')) && eqNum(b.baseId, q(req, 'baseId')))
    .sort(byNewest).map(lbView)
  return ok(res, page(req, rows))
})
farmLivestockBatchRouter.get('/get', (req, res) => {
  const b = own(db().livestockBatches, req.query.id, tenantOf(req))
  return b ? ok(res, lbView(b)) : notFound(res, '畜禽批次')
})

type LbFields = { baseId: number; species: string; breed: string; inDate: number; count: number; pen: string; remark: string }
/** 校验并整理可编辑字段；失败返回错误文案 */
function readFields(t: number, body: Record<string, unknown>, outCount: number): string | LbFields {
  const base = own(db().bases, body.baseId, t)
  if (!base) return '请选择基地'
  const species = String(body.species ?? '').trim()
  if (!species) return '请填写畜禽种类'
  if (species.length > 32) return '畜禽种类不能超过 32 个字'
  const count = Number(body.count)
  if (!Number.isInteger(count) || count <= 0) return '数量要是大于 0 的整数'
  if (count < outCount) return `数量不能小于已出栏数 ${outCount}`
  const breed = String(body.breed ?? '').trim()
  const pen = String(body.pen ?? '').trim()
  const remark = String(body.remark ?? '').trim()
  if (breed.length > 64 || pen.length > 64) return '品种、栏舍不能超过 64 个字'
  if (remark.length > 255) return '备注不能超过 255 个字'
  const inDate = body.inDate === undefined || body.inDate === null || body.inDate === '' ? Date.now() : Number(body.inDate)
  if (!(inDate > 0)) return '入栏日期不正确'
  return { baseId: base.id, species, breed, inDate, count, pen, remark }
}

farmLivestockBatchRouter.post('/create', (req, res) => {
  const t = tenantOf(req)
  const f = readFields(t, req.body ?? {}, 0)
  if (typeof f === 'string') return badRequest(res, f)
  const b: LivestockBatch = { id: nextId(db().livestockBatches), tenantId: t, no: makeNo('XB'), ...f, outCount: 0, status: 0, createTime: Date.now() }
  db().livestockBatches.push(b)
  return ok(res, b.id)
})
farmLivestockBatchRouter.put('/update', (req, res) => {
  const t = tenantOf(req)
  const b = own(db().livestockBatches, req.body?.id, t)
  if (!b) return notFound(res, '畜禽批次')
  const f = readFields(t, req.body ?? {}, b.outCount)
  if (typeof f === 'string') return badRequest(res, f)
  Object.assign(b, f)
  // 数量调整后重新判断状态（满额 -> 已出栏）
  if (b.outCount > 0) b.status = b.outCount >= b.count ? 20 : 10
  return ok(res, true)
})
farmLivestockBatchRouter.put('/out', (req, res) => {
  const b = own(db().livestockBatches, req.body?.id, tenantOf(req))
  if (!b) return notFound(res, '畜禽批次')
  const n = Number(req.body?.count)
  if (!Number.isInteger(n) || n <= 0) return badRequest(res, '出栏数量要是大于 0 的整数')
  if (b.outCount + n > b.count) return badRequest(res, `出栏数量超过在栏数量（剩余 ${b.count - b.outCount}）`)
  b.outCount += n
  b.status = b.outCount >= b.count ? 20 : 10
  return ok(res, true)
})
farmLivestockBatchRouter.delete('/delete', (req, res) => {
  const t = tenantOf(req)
  const id = req.query.id ?? req.body?.id
  const b = own(db().livestockBatches, id, t)
  if (!b) return notFound(res, '畜禽批次')
  if (b.outCount > 0) return badRequest(res, '已有出栏记录的批次不能删除')
  db().livestockBatches.splice(db().livestockBatches.indexOf(b), 1)
  return ok(res, true)
})

// =============================== C 端 /app-api/farm ===============================
export const farmAppP1bRouter = Router()
const login = requireLogin('member')

const SIGN_MAX_BYTES = 200 * 1024
/** 解析签名：base64 PNG 或 data:image/png;base64,...；返回规范化的 dataURL，失败返回错误文案 */
function parseSignature(raw: unknown): { url: string } | { error: string } {
  if (typeof raw !== 'string' || !raw.trim()) return { error: '请先手写签名' }
  let b64 = raw.trim()
  const m = /^data:([\w/+.-]+);base64,/i.exec(b64)
  if (m) {
    if (m[1].toLowerCase() !== 'image/png') return { error: '签名图片必须是 PNG' }
    b64 = b64.slice(m[0].length)
  }
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(b64) || b64.length % 4 !== 0) return { error: '签名图片格式不正确' }
  const bytes = Buffer.from(b64, 'base64')
  if (bytes.length > SIGN_MAX_BYTES) return { error: '签名图片不能超过 200KB' }
  if (bytes.length < 8 || bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') return { error: '签名图片必须是 PNG' }
  return { url: 'data:image/png;base64,' + b64 }
}

function myOrderByContract(req: { body?: any; query: any }, m: { id: number; tenantId: number }) {
  const id = req.body?.contractId ?? req.body?.orderId ?? req.query.orderId ?? req.query.contractId
  const o = own(db().orders, id, m.tenantId)
  return o && o.memberId === m.id ? o : undefined
}

function appContractView(o: Order) {
  const ov = orderView(o)
  const c = contractOf(o)
  return {
    id: o.id, no: c.no, orderId: o.id, orderNo: o.no, title: c.title, version: c.version, careMode: o.careMode, careModeName: CARE_MODE_NAME[o.careMode],
    status: contractStatusOf(o), productTitle: ov.productTitle, amount: o.amount, memberName: ov.memberName, createTime: o.createTime, payTime: o.payTime,
    signed: !!o.signature, signedAt: o.signedAt ?? null, hasSignature: !!o.signature, signature: o.signature ?? null
  }
}

farmAppP1bRouter.get('/contract/get', login, (req, res) => {
  const o = myOrderByContract(req, currentMember(req)!)
  return o ? ok(res, appContractView(o)) : notFound(res, '合同')
})
farmAppP1bRouter.post('/contract/sign', login, (req, res) => {
  const o = myOrderByContract(req, currentMember(req)!)
  if (!o) return notFound(res, '合同')
  if (contractStatusOf(o) === 20) return badRequest(res, '合同已解除，不能签署')
  if (o.signature) return fail(res, BIZ.CONTRACT_SIGNED, '合同已签署，不能重复签名')
  const r = parseSignature(req.body?.signature)
  if ('error' in r) return badRequest(res, r.error)
  o.signature = r.url
  o.signedAt = Date.now()
  return ok(res, { orderId: o.id, signedAt: o.signedAt })
})

/** 收成详情（含环境快照）：只能看自己认养下的收成 */
farmAppP1bRouter.get('/harvest/list', login, (req, res) => {
  const m = currentMember(req)!
  const mineAd = new Set(db().adoptions.filter((a) => a.memberId === m.id && a.tenantId === m.tenantId).map((a) => a.id))
  const adId = q(req, 'adoptionId')
  return ok(res, db().harvests.filter((h) => h.tenantId === m.tenantId && mineAd.has(h.adoptionId) && (adId === undefined || h.adoptionId === Number(adId)))
    .sort((a, b) => b.harvestDate - a.harvestDate).map(harvestView))
})
farmAppP1bRouter.get('/harvest/get', login, (req, res) => {
  const m = currentMember(req)!
  const h = own(db().harvests, req.query.id, m.tenantId)
  const ad = h && db().adoptions.find((a) => a.id === h.adoptionId)
  if (!h || !ad || ad.memberId !== m.id) return notFound(res, '收成')
  return ok(res, harvestView(h))
})
