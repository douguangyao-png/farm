import { describe, it, expect, beforeEach } from 'vitest'
import request from 'supertest'
import { createApp } from '../src/app.js'

const app = createApp()
type H = Record<string, string>

async function admin(tenantId: number, username: string, password = 'demo123'): Promise<H> {
  const r = await request(app).post('/admin-api/system/auth/login').set('tenant-id', String(tenantId)).send({ username, password })
  return { Authorization: `Bearer ${r.body.data.accessToken}`, 'tenant-id': String(tenantId) }
}
async function member(tenantId: number, mobile: string): Promise<H> {
  const r = await request(app).post('/app-api/member/auth/login').set('tenant-id', String(tenantId)).send({ mobile, password: 'demo123' })
  return { Authorization: `Bearer ${r.body.data.accessToken}`, 'tenant-id': String(tenantId) }
}
const get = (path: string, h: H, query: Record<string, unknown> = {}) => request(app).get(path).set(h).query(query).then((r) => r.body)
const post = (path: string, h: H, body: unknown = {}) => request(app).post(path).set(h).send(body as object).then((r) => r.body)
const put = (path: string, h: H, body: unknown = {}) => request(app).put(path).set(h).send(body as object).then((r) => r.body)
const del = (path: string, h: H, query: Record<string, unknown> = {}) => request(app).delete(path).set(h).query(query).then((r) => r.body)

/** 1x1 透明 PNG */
const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='
const DAY = 86400_000

beforeEach(async () => { await request(app).post('/__mock/reset') })

describe('第一期补齐：订单补字段', () => {
  it('下单带渠道/交付方式/定位；缺省 h5 + express；非法值 400；admin 详情带 customerTags', async () => {
    const m = await member(2, '13800000001')
    const a = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true })
    expect(a.code).toBe(0)
    const h = await admin(2, 'qingshan')
    const d0 = await get('/admin-api/farm/order/get', h, { id: a.data.id })
    expect(d0.data.channel).toBe('h5')
    expect(d0.data.deliverWay).toBe('express')
    expect(d0.data.couponAmount).toBe(0)
    expect(Array.isArray(d0.data.customerTags)).toBe(true)
    expect(d0.data.signature).toBeUndefined()

    const b = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'semi', contractAgreed: true, deliverWay: 'pickup', channel: 'scan', packageName: '半托管 · 一年', lng: 119.68, lat: 30.63, gpsText: '安吉县' })
    const d = await get('/admin-api/farm/order/get', h, { id: b.data.id })
    expect(d.data).toMatchObject({ deliverWay: 'pickup', channel: 'scan', packageName: '半托管 · 一年', gpsLng: 119.68, gpsLat: 30.63, gpsText: '安吉县' })
    const pg = await get('/admin-api/farm/order/page', h, { pageSize: 100 })
    const row = pg.data.list.find((x: { id: number }) => x.id === b.data.id)
    expect(row.channel).toBe('scan')
    expect(row.deliverWay).toBe('pickup')

    expect((await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true, deliverWay: 'drone' })).code).toBe(400)
    expect((await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true, channel: 'x' })).code).toBe(400)
  })
})

describe('第一期补齐：配送批次', () => {
  it('创建 -> 发货 -> 完成；状态不对 400；不存在 404；租户隔离', async () => {
    const h = await admin(2, 'qingshan')
    const seeded = await get('/admin-api/farm/ship-batch/page', h)
    expect(seeded.data.total).toBe(2)
    // 新建一个收成 + 配送单再成批
    const hv = await post('/admin-api/farm/harvest/create', h, { adoptionId: 2, crop: '生菜', weightJin: 5 })
    const sp = await post('/admin-api/farm/shipment/create', h, { harvestId: hv.data, carrier: '顺丰冷运', logisticsNo: 'SF1' })
    expect((await post('/admin-api/farm/ship-batch/create', h, { harvestId: hv.data, planDate: Date.now() + DAY, shipmentIds: [] })).code).toBe(400)
    const c = await post('/admin-api/farm/ship-batch/create', h, { harvestId: hv.data, planDate: Date.now() + DAY, remark: '测', shipmentIds: [sp.data] })
    expect(c.code).toBe(0)
    const g = await get('/admin-api/farm/ship-batch/get', h, { id: c.data })
    expect(g.data.no).toMatch(/^PC\d{8}\d{3}$/)
    expect(g.data.status).toBe(0)
    expect(g.data.shipments.length).toBe(1)
    // 同一配送单不能再入别的批次
    expect((await post('/admin-api/farm/ship-batch/create', h, { harvestId: hv.data, planDate: Date.now(), shipmentIds: [sp.data] })).code).toBe(400)
    expect((await put('/admin-api/farm/ship-batch/finish', h, { id: c.data })).code).toBe(400) // 待发不能直接完成
    expect((await put('/admin-api/farm/ship-batch/ship', h, { id: c.data })).code).toBe(0)
    expect((await put('/admin-api/farm/ship-batch/ship', h, { id: c.data })).code).toBe(400)
    expect((await put('/admin-api/farm/ship-batch/finish', h, { id: c.data })).code).toBe(0)
    expect((await get('/admin-api/farm/ship-batch/get', h, { id: c.data })).data.status).toBe(20)
    expect((await get('/admin-api/farm/ship-batch/get', h, { id: 9999 })).code).toBe(404)
    const h3 = await admin(3, 'lvye')
    expect((await get('/admin-api/farm/ship-batch/get', h3, { id: c.data })).code).toBe(404)
    expect((await get('/admin-api/farm/ship-batch/page', h, { status: 20 })).data.list.every((x: { status: number }) => x.status === 20)).toBe(true)
  })
})

describe('第一期补齐：收成环境快照', () => {
  it('创建收成写快照；种子收成按采收日补并标演示数据；C 端只能看自己的', async () => {
    const h = await admin(2, 'qingshan')
    const hv = await post('/admin-api/farm/harvest/create', h, { adoptionId: 1, crop: '生菜', weightJin: 3 })
    const list = await get('/admin-api/farm/harvest/page', h, { pageSize: 100 })
    const row = list.data.list.find((x: { id: number }) => x.id === hv.data)
    expect(row.envSnapshot).toMatchObject({ source: '演示数据' })
    expect(typeof row.envSnapshot.temp).toBe('number')
    expect(typeof row.envSnapshot.light).toBe('number')
    expect(list.data.list.every((x: { envSnapshot: unknown }) => x.envSnapshot)).toBe(true)

    const m = await member(2, '13800000001')
    const mine = await get('/app-api/farm/harvest/list', m)
    expect(mine.data.length).toBeGreaterThan(0)
    expect(mine.data.every((x: { envSnapshot: unknown }) => x.envSnapshot)).toBe(true)
    expect((await get('/app-api/farm/harvest/get', m, { id: 1 })).data.envSnapshot.source).toBe('演示数据')
    expect((await get('/app-api/farm/harvest/get', m, { id: 11 })).code).toBe(404) // 别的租户
    expect((await get('/app-api/farm/harvest/get', m, { id: 2 })).code).toBe(404) // 同租户别人的认养（adoption 2 属于会员 2）
  })
})

describe('第一期补齐：畜禽批次', () => {
  it('种子、创建、更新、出栏、删除规则', async () => {
    const h = await admin(2, 'qingshan')
    const p = await get('/admin-api/farm/livestock-batch/page', h)
    expect(p.data.total).toBe(2)
    expect((await get('/admin-api/farm/livestock-batch/page', await admin(3, 'lvye'))).data.total).toBe(1)
    expect((await get('/admin-api/farm/livestock-batch/page', h, { species: '鸭' })).data.total).toBe(1)
    expect((await post('/admin-api/farm/livestock-batch/create', h, { baseId: 1, species: '', count: 10 })).code).toBe(400)
    expect((await post('/admin-api/farm/livestock-batch/create', h, { baseId: 3, species: '鸡', count: 10 })).code).toBe(400) // 别的租户基地
    expect((await post('/admin-api/farm/livestock-batch/create', h, { baseId: 1, species: '鸡', count: 0 })).code).toBe(400)
    const c = await post('/admin-api/farm/livestock-batch/create', h, { baseId: 1, species: '鹅', breed: '白鹅', count: 50, pen: 'C 栏' })
    expect(c.code).toBe(0)
    const g = await get('/admin-api/farm/livestock-batch/get', h, { id: c.data })
    expect(g.data).toMatchObject({ species: '鹅', count: 50, outCount: 0, status: 0, remaining: 50 })
    expect(g.data.no).toMatch(/^XB\d{8}\d{3}$/)
    expect((await put('/admin-api/farm/livestock-batch/out', h, { id: c.data, count: 51 })).code).toBe(400)
    expect((await put('/admin-api/farm/livestock-batch/out', h, { id: c.data, count: 20 })).code).toBe(0)
    expect((await get('/admin-api/farm/livestock-batch/get', h, { id: c.data })).data).toMatchObject({ outCount: 20, status: 10 })
    expect((await del('/admin-api/farm/livestock-batch/delete', h, { id: c.data })).code).toBe(400) // 已有出栏
    expect((await put('/admin-api/farm/livestock-batch/update', h, { id: c.data, baseId: 1, species: '鹅', count: 10 })).code).toBe(400) // 数量小于已出栏
    expect((await put('/admin-api/farm/livestock-batch/out', h, { id: c.data, count: 30 })).code).toBe(0)
    expect((await get('/admin-api/farm/livestock-batch/get', h, { id: c.data })).data.status).toBe(20)
    const c2 = await post('/admin-api/farm/livestock-batch/create', h, { baseId: 1, species: '鸭', count: 5 })
    expect((await del('/admin-api/farm/livestock-batch/delete', h, { id: c2.data })).code).toBe(0)
    expect((await get('/admin-api/farm/livestock-batch/get', h, { id: c2.data })).code).toBe(404)
  })
})

describe('第一期补齐：租户切换 visit-tenant-id', () => {
  it('平台管理员带头后看到所选租户数据；经营者带头无效', async () => {
    const pr = await request(app).post('/admin-api/system/auth/login').set('tenant-id', '1').send({ username: 'admin', password: 'admin123' })
    const ph: H = { Authorization: `Bearer ${pr.body.data.accessToken}`, 'tenant-id': '1' }
    const none = await get('/admin-api/farm/livestock-batch/page', ph)
    expect(none.data.total).toBe(0)
    const visit = await get('/admin-api/farm/livestock-batch/page', { ...ph, 'visit-tenant-id': '2' })
    expect(visit.data.total).toBe(2)
    const h = await admin(2, 'qingshan')
    const other = await get('/admin-api/farm/livestock-batch/page', { ...h, 'visit-tenant-id': '3' })
    expect(other.data.total).toBe(2) // 仍是自己的租户
  })
})

describe('第一期补齐：合同签名', () => {
  it('签名成功后后台可见；重复签 1030000011；非 PNG/超大/别人的合同被拒', async () => {
    const m = await member(2, '13800000001')
    const h = await admin(2, 'qingshan')
    const before = await get('/app-api/farm/contract/get', m, { orderId: 1 })
    expect(before.data).toMatchObject({ orderId: 1, signed: false, hasSignature: false, signature: null })
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 1 })).code).toBe(400)
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 1, signature: 'not base64!' })).code).toBe(400)
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 1, signature: Buffer.from('hello world').toString('base64') })).code).toBe(400) // 不是 PNG
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 1, signature: 'data:image/jpeg;base64,' + PNG_B64 })).code).toBe(400)
    const big = Buffer.concat([Buffer.from('89504e470d0a1a0a', 'hex'), Buffer.alloc(210 * 1024)]).toString('base64')
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 1, signature: big })).code).toBe(400)
    expect((await post('/app-api/farm/contract/sign', m, { orderId: 2, signature: PNG_B64 })).code).toBe(404) // 订单 2 是会员 2 的

    const ok1 = await post('/app-api/farm/contract/sign', m, { contractId: 1, signature: 'data:image/png;base64,' + PNG_B64 })
    expect(ok1.code).toBe(0)
    const dup = await post('/app-api/farm/contract/sign', m, { orderId: 1, signature: PNG_B64 })
    expect(dup.code).toBe(1_030_000_011)
    const after = await get('/app-api/farm/contract/get', m, { orderId: 1 })
    expect(after.data.signed).toBe(true)
    expect(after.data.signature).toMatch(/^data:image\/png;base64,/)

    const page = await get('/admin-api/farm/contract/page', h, { pageSize: 100 })
    const row = page.data.list.find((x: { orderId: number }) => x.orderId === 1)
    expect(row.hasSignature).toBe(true)
    expect(row.signedAt).toBe(after.data.signedAt)
    expect(page.data.list.find((x: { orderId: number }) => x.orderId === 3).hasSignature).toBe(false)
    const od = await get('/admin-api/farm/order/get', h, { id: 1 })
    expect(od.data.contract.hasSignature).toBe(true)
    expect(JSON.stringify(od.data)).not.toContain('base64')
    expect((await request(app).get('/app-api/farm/contract/get').query({ orderId: 1 })).body.code).toBe(401)
  })
})
