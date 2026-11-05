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

const productBody = (title: string, promise = '约定交付 240 斤，不足部分按同等品质补发') => ({
  baseId: 1, assetType: 'plot', title, description: '66㎡ 菜地', cycleDays: 365, stock: 2,
  careModes: [
    { mode: 'full', enabled: true, price: 128000, slogan: '农场全包', outputPromise: promise, contractVersion: 'v3.2', weeklyTodo: 0, insurance: '', duty: [] },
    { mode: 'semi', enabled: true, price: 86000, slogan: '日常您来', outputPromise: '按实际收成交付', contractVersion: 'v2.1', weeklyTodo: 2, insurance: '', duty: [] },
    { mode: 'self', enabled: false, price: 0, slogan: '', outputPromise: '', contractVersion: 'v1.4', weeklyTodo: 5, insurance: '', duty: [] }
  ]
})

beforeEach(async () => { await request(app).post('/__mock/reset') })

describe('R-2 认养商品违禁词拦截', () => {
  it('标题含「稳赚收益」被拦，返回命中的词和建议，风险自检记一次', async () => {
    const h = await admin(2, 'qingshan')
    const r = await post('/admin-api/farm/product/create', h, productBody('稳赚收益菜地'))
    expect(r.code).toBe(1_030_000_000)
    expect(r.data.words).toEqual(expect.arrayContaining(['稳赚', '收益']))
    expect(r.msg).toContain('约定交付')
    const check = await get('/admin-api/farm/risk/self-check', h)
    expect(check.data.hits).toHaveLength(1)
  })
  it('改成约定交付的说法能提交，列表里多一条', async () => {
    const h = await admin(2, 'qingshan')
    const r = await post('/admin-api/farm/product/create', h, productBody('一分菜地 · 春季认养'))
    expect(r.code).toBe(0)
    const list = await get('/admin-api/farm/product/page', h)
    expect(list.data.list[0].title).toBe('一分菜地 · 春季认养')
  })
  it('标题为空、全部关闭托管方式都报 400', async () => {
    const h = await admin(2, 'qingshan')
    expect((await post('/admin-api/farm/product/create', h, productBody(''))).code).toBe(400)
    const b = productBody('x')
    b.careModes.forEach((m) => (m.enabled = false))
    expect((await post('/admin-api/farm/product/create', h, b)).msg).toBe('至少开放一种托管方式')
  })
})

describe('主线：C 端下单 → 后台看到订单 → 自管任务（R-1, R-3, R-4）', () => {
  it('不勾选合同不能下单，订单数不变', async () => {
    const m = await member(2, '13800000001')
    const r = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'semi', contractAgreed: false })
    expect(r.code).toBe(1_030_000_001)
    const h = await admin(2, 'qingshan')
    expect((await get('/admin-api/farm/order/page', h)).data.total).toBe(6)
  })
  it('半托管下单并支付：后台订单列表出现、地块 A-01 被占用、生成 2 项自管任务；三档价格 1280/860/500', async () => {
    const m = await member(2, '13800000001')
    const detail = await get('/app-api/farm/product/get', { 'tenant-id': '2' }, { id: 1 })
    expect(detail.data.careModes.map((x: { price: number }) => x.price)).toEqual([128000, 86000, 50000])
    expect(detail.data.compareRows).toHaveLength(13)
    const o = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'semi', contractAgreed: true })
    expect(o.data.amount).toBe(86000)
    const pay = await post('/app-api/farm/order/pay', m, { id: o.data.id })
    expect(pay.data.assetCode).toBe('A-01')
    expect((await post('/app-api/farm/order/pay', m, { id: o.data.id })).code).toBe(1_030_000_002) // 重复支付
    const tasks = await get('/app-api/farm/self-task/list', m, { adoptionId: pay.data.adoptionId })
    expect(tasks.data).toHaveLength(2)
    const h = await admin(2, 'qingshan')
    const orders = await get('/admin-api/farm/order/page', h)
    expect(orders.data.list[0]).toMatchObject({ no: o.data.no, careMode: 'semi', status: 10, assetCode: 'A-01', memberName: '周女士' })
    const od = await get('/admin-api/farm/order/get', h, { id: o.data.id })
    expect(od.data.contract.version).toBe('v2.1')
    expect(od.data.adoption.assetCode).toBe('A-01')
  })
  it('全托管 0 项自管任务，约定交付 240 斤；不托管 5 项', async () => {
    const m = await member(2, '13800000002')
    const full = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true })
    const p1 = await post('/app-api/farm/order/pay', m, { id: full.data.id })
    expect((await get('/app-api/farm/self-task/list', m, { adoptionId: p1.data.adoptionId })).data).toHaveLength(0)
    expect((await get('/app-api/farm/adoption/get', m, { id: p1.data.adoptionId })).data.promisedJin).toBe(240)
    const self = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'self', contractAgreed: true })
    const p2 = await post('/app-api/farm/order/pay', m, { id: self.data.id })
    expect((await get('/app-api/farm/self-task/list', m, { adoptionId: p2.data.adoptionId })).data).toHaveLength(5)
  })
  it('空闲地块卖完后支付报「没有空闲的地块」', async () => {
    const m = await member(2, '13800000003')
    for (let i = 0; i < 4; i++) {
      const o = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true })
      expect((await post('/app-api/farm/order/pay', m, { id: o.data.id })).code).toBe(0)
    }
    const o = await post('/app-api/farm/order/create', m, { productId: 1, careMode: 'full', contractAgreed: true })
    expect((await post('/app-api/farm/order/pay', m, { id: o.data.id })).code).toBe(1_030_000_003)
  })
})

describe('R-5 自管任务逾期 → 委托代办 → 管家接单打卡 → 后台审核 → C 端档案', () => {
  it('完整走一遍', async () => {
    const m = await member(2, '13800000001')
    const list = await get('/app-api/farm/self-task/list', m, { adoptionId: 3 })
    const overdue = list.data.find((t: { overdue: boolean }) => t.overdue)
    expect(overdue.title).toBe('浇水')
    const e = await post('/app-api/farm/self-task/entrust', m, { id: overdue.id })
    expect(e.code).toBe(0)
    expect((await post('/app-api/farm/self-task/entrust', m, { id: overdue.id })).code).toBe(1_030_000_006)

    const s = await admin(2, 'zhangguanjia')
    const pool = await get('/admin-api/farm/steward/service-order/list', s, { scope: 'pool' })
    expect(pool.data[0]).toMatchObject({ id: e.data.serviceOrderId, status: 0 })
    expect((await put('/admin-api/farm/steward/service-order/accept', s, { id: e.data.serviceOrderId })).code).toBe(0)
    const tasks = await get('/admin-api/farm/steward/task/list', s)
    const svcTask = tasks.data.find((t: { serviceOrderId: number }) => t.serviceOrderId === e.data.serviceOrderId)
    expect((await post('/admin-api/farm/steward/task/checkin', s, { taskId: svcTask.id, photos: [] })).msg).toBe('至少拍一张现场照片')
    const ck = await post('/admin-api/farm/steward/task/checkin', s, { taskId: svcTask.id, photos: ['data:image/png;base64,AA=='], content: '浇透一次' })
    expect(ck.code).toBe(0)
    expect(ck.data.checks.gpsFence).toBe(true)

    const h = await admin(2, 'qingshan')
    const so = await get('/admin-api/farm/service-order/get', h, { id: e.data.serviceOrderId })
    expect(so.data.status).toBe(20)
    const before = (await get('/app-api/farm/adoption/records', m, { adoptionId: 3 })).data.length
    expect((await put('/admin-api/farm/record/audit', h, { id: ck.data.recordId, pass: false })).msg).toBe('驳回时请填写原因')
    expect((await put('/admin-api/farm/record/audit', h, { id: ck.data.recordId, pass: true })).code).toBe(0)
    expect((await get('/app-api/farm/adoption/records', m, { adoptionId: 3 })).data.length).toBe(before + 1)
  })
  it('管家不能打别人的任务卡；李管家看不到青山一号基地的工单池', async () => {
    const li = await admin(2, 'liguanjia')
    expect((await post('/admin-api/farm/steward/task/checkin', li, { taskId: 1, photos: ['x'] })).code).toBe(1_030_000_007)
    const m = await member(2, '13800000001')
    await post('/app-api/farm/self-task/entrust', m, { id: 11 })
    expect((await get('/admin-api/farm/steward/service-order/list', li, { scope: 'pool' })).data).toHaveLength(0)
  })
  it('经营者账号不能用管家端接口', async () => {
    const h = await admin(2, 'qingshan')
    expect((await get('/admin-api/farm/steward/home', h)).code).toBe(403)
  })
})

describe('R-6 租户隔离', () => {
  it('青山的 token 带绿野的 tenant-id 返回 403；按 id 取别的租户的订单返回 404', async () => {
    const h = await admin(2, 'qingshan')
    expect((await get('/admin-api/farm/order/get', { ...h, 'tenant-id': '3' }, { id: 11 })).code).toBe(403)
    expect((await get('/admin-api/farm/order/get', h, { id: 11 })).code).toBe(404)
    const lv = await admin(3, 'lvye')
    const a = (await get('/admin-api/farm/order/page', h)).data.list.map((o: { id: number }) => o.id)
    const b = (await get('/admin-api/farm/order/page', lv)).data.list.map((o: { id: number }) => o.id)
    expect(a.some((id: number) => b.includes(id))).toBe(false)
  })
  it('经营者不能看风控预警；平台能看所有租户', async () => {
    expect((await get('/admin-api/farm/risk-alert/page', await admin(2, 'qingshan'))).code).toBe(403)
    const p = await get('/admin-api/farm/risk-alert/page', await admin(1, 'admin', 'admin123'))
    expect(new Set(p.data.list.map((x: { tenantId: number }) => x.tenantId))).toEqual(new Set([2, 3]))
  })
})

describe('农资：必须绑定地块、农药不零售、登记施用', () => {
  it('错误路径和登记', async () => {
    const m = await member(2, '13800000001')
    expect((await post('/app-api/farm/supply-order/create', m, { spuId: 3, qty: 1 })).code).toBe(1_030_000_004)
    expect((await post('/app-api/farm/supply-order/create', m, { spuId: 11, qty: 1, adoptionId: 1 })).code).toBe(1_030_000_005)
    expect((await post('/app-api/farm/supply-order/create', m, { spuId: 3, qty: 0, adoptionId: 1 })).code).toBe(400)
    const list = await get('/app-api/farm/supply/list', { 'tenant-id': '2' })
    expect(list.data.some((s: { cat: string }) => s.cat === 'pesticide')).toBe(false)
    const o = await post('/app-api/farm/supply-order/create', m, { spuId: 3, qty: 1, adoptionId: 1 })
    expect(o.data.amount).toBe(6800)
    const h = await admin(2, 'qingshan')
    expect((await put('/admin-api/farm/supply-order/trace', h, { id: o.data.id, usedAt: Date.now(), dosage: '' })).code).toBe(400)
    expect((await put('/admin-api/farm/supply-order/trace', h, { id: o.data.id, usedAt: Date.now(), dosage: '20kg' })).code).toBe(0)
    const trace = await get('/app-api/farm/adoption/trace', m, { adoptionId: 1 })
    expect(trace.data.inputs.some((x: { source: string; name: string }) => x.source === 'member' && x.name === '腐熟羊粪有机肥')).toBe(true)
  })
  it('化学农药在后台改成可零售，服务端强制改回不可零售', async () => {
    const h = await admin(2, 'qingshan')
    await put('/admin-api/farm/supply/update', h, { id: 11, retailAllowed: true })
    expect((await get('/admin-api/farm/supply/get', h, { id: 11 })).data.retailAllowed).toBe(false)
  })
})

describe('收成与配送', () => {
  it('录入收成 → 发货 → C 端看到物流；重复发货报错', async () => {
    const h = await admin(2, 'qingshan')
    expect((await post('/admin-api/farm/harvest/create', h, { adoptionId: 1, crop: '番茄', weightJin: 0 })).code).toBe(400)
    const hv = await post('/admin-api/farm/harvest/create', h, { adoptionId: 1, crop: '番茄', weightJin: 20.5 })
    const sp = await post('/admin-api/farm/shipment/create', h, { harvestId: hv.data, carrier: '顺丰冷运', logisticsNo: 'SF100' })
    expect(sp.code).toBe(0)
    expect((await post('/admin-api/farm/shipment/create', h, { harvestId: hv.data, carrier: '顺丰冷运', logisticsNo: 'SF100' })).code).toBe(400)
    const m = await member(2, '13800000001')
    const list = await get('/app-api/farm/shipment/list', m, { adoptionId: 1 })
    expect(list.data[0]).toMatchObject({ logisticsNo: 'SF100', status: 10 })
    expect((await get('/app-api/farm/adoption/get', m, { id: 1 })).data.harvestedJin).toBe(52)
  })
})

describe('R-11 文章、R-12 问题反馈、R-13 项目文档', () => {
  it('文章按租户隔离，标题为空报错', async () => {
    const qs = await admin(2, 'qingshan')
    expect((await post('/admin-api/promotion/article/create', qs, { categoryId: 11, title: '' })).msg).toBe('标题不能为空')
    expect((await post('/admin-api/promotion/article/create', qs, { categoryId: 11, title: '冬季防冻' })).code).toBe(0)
    const lv = await admin(3, 'lvye')
    const titles = (await get('/admin-api/promotion/article/page', lv)).data.list.map((a: { title: string }) => a.title)
    expect(titles).not.toContain('冬季防冻')
  })
  it('反馈编号递增；经营者只看自己的，平台看全部并能回复', async () => {
    const qs = await admin(2, 'qingshan')
    expect((await post('/admin-api/farm/feedback/create', qs, { type: 'bug', content: '' })).code).toBe(400)
    expect((await post('/admin-api/farm/feedback/create', qs, { type: 'bug', content: 'x', screenshots: ['a', 'b', 'c', 'd'] })).msg).toBe('最多上传 3 张截图')
    const f = await post('/admin-api/farm/feedback/create', qs, { type: 'bug', content: '订单页导出没反应', pageTitle: '认养订单', pagePath: '/farm-order/order' })
    expect(f.data.no).toBe('FB-0002')
    expect((await get('/admin-api/farm/feedback/page', qs)).data.total).toBe(1)
    const pf = await admin(1, 'admin', 'admin123')
    const all = await get('/admin-api/farm/feedback/page', pf)
    expect(all.data.total).toBe(2)
    expect(all.data.list[0]).toMatchObject({ no: 'FB-0002', pageTitle: '认养订单', tenantName: '青山农场' })
    expect((await post('/admin-api/farm/feedback/reply', qs, { id: f.data.id, content: 'x' })).code).toBe(403)
    expect((await post('/admin-api/farm/feedback/reply', pf, { id: f.data.id, content: '已修复' })).code).toBe(0)
    expect((await get('/admin-api/farm/feedback/get', qs, { id: f.data.id })).data.status).toBe(10)
  })
  it('项目文档：平台能看列表和内容，经营者 403，不能越权读目录外文件', async () => {
    const pf = await admin(1, 'admin', 'admin123')
    const list = await get('/admin-api/farm/doc/list', pf)
    expect(list.data.some((d: { path: string; category: string }) => d.path === 'api/farm-接口契约.md' && d.category === '设计')).toBe(true)
    const doc = await get('/admin-api/farm/doc/get', pf, { path: '01-PRD.md' })
    expect(doc.data.content.length).toBeGreaterThan(100)
    expect((await get('/admin-api/farm/doc/get', pf, { path: '../mock-server/package.json' })).code).toBe(404)
    expect((await get('/admin-api/farm/doc/list', await admin(2, 'qingshan'))).code).toBe(403)
  })
})

describe('平台开通租户、R-9 重置', () => {
  it('开通新租户后能用新账号登录；租户名重复报错；重置后恢复', async () => {
    const pf = await admin(1, 'admin', 'admin123')
    const r = await post('/admin-api/system/tenant/create', pf, { name: '东篱农场', contactName: '陶先生', packageId: 2, username: 'dongli', password: 'demo123' })
    expect(r.code).toBe(0)
    expect((await post('/admin-api/system/tenant/create', pf, { name: '东篱农场', packageId: 2, username: 'x', password: 'y' })).msg).toBe('租户名已存在')
    const login = await request(app).post('/admin-api/system/auth/login').set('tenant-id', String(r.data)).send({ username: 'dongli', password: 'demo123' })
    expect(login.body.code).toBe(0)
    await request(app).post('/__mock/reset')
    const pf2 = await admin(1, 'admin', 'admin123')
    expect((await get('/admin-api/system/tenant/page', pf2)).data.total).toBe(3)
  })
})

describe('工作台、环境数据', () => {
  it('工作台有 14 天曲线和待办；环境数据 24 个点，A-03 土壤湿度低于 22%', async () => {
    const h = await admin(2, 'qingshan')
    const d = await get('/admin-api/farm/dashboard/summary', h)
    expect(d.data.gmvTrend).toHaveLength(14)
    expect(d.data.pendingRecordCount).toBe(2)
    const env = await get('/admin-api/farm/asset/env', h, { assetId: 3 })
    expect(env.data.series).toHaveLength(24)
    expect(env.data.current.soilMoisture).toBeLessThan(22)
  })
})
