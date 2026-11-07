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


describe('第二期 B：交易流水、分账、经营分析', () => {
  it('未登录返回 401；流水由订单派生，金额恒等、交易号唯一、租户隔离', async () => {
    const r401 = await request(app).get('/admin-api/farm/finance/flow/page')
    expect(r401.body.code).toBe(401)
    const h = await admin(2, 'qingshan')
    const r = await get('/admin-api/farm/finance/flow/page', h, { pageSize: 100 })
    expect(r.code).toBe(0)
    expect(r.data.total).toBeGreaterThan(0)
    const rows = r.data.list as { tradeNo: string; amount: number; fee: number; net: number; type: string }[]
    for (const x of rows) expect(x.net).toBe(x.amount - x.fee)
    expect(new Set(rows.map((x) => x.tradeNo)).size).toBe(rows.length)
    expect(r.data.stats.feeRate).toBe(3)
    // 只按类型筛，其余不变
    const adopt = await get('/admin-api/farm/finance/flow/page', h, { type: 'adopt', pageSize: 100 })
    expect(adopt.data.list.every((x: { type: string }) => x.type === 'adopt')).toBe(true)
    // 另一个租户看不到本租户的流水
    const h3 = await admin(3, 'lvye')
    const r3 = await get('/admin-api/farm/finance/flow/page', h3, { pageSize: 100 })
    const nos2 = new Set(rows.map((x) => x.tradeNo))
    expect(r3.data.list.every((x: { refNo: string }) => !x.refNo.startsWith('RY'))).toBe(true)
    expect(r3.data.total).toBeLessThan(r.data.total)
    void nos2
  })

  it('分账规则合计 100%，示例金额拆分后合计不变；经营分析的渠道合计等于已支付单数', async () => {
    const h = await admin(2, 'qingshan')
    const s = await get('/admin-api/farm/finance/split/get', h)
    expect(s.data.rules.reduce((a: number, x: { percent: number }) => a + x.percent, 0)).toBe(100)
    expect(s.data.rules.reduce((a: number, x: { exampleAmount: number }) => a + x.exampleAmount, 0)).toBe(s.data.exampleAmount)
    expect(s.data.month.items.reduce((a: number, x: { amount: number }) => a + x.amount, 0)).toBe(s.data.month.total)
    const a = await get('/admin-api/farm/analysis/summary', h, { days: 90 })
    expect(a.data.days).toBe(90)
    expect(a.data.funnel.map((x: { name: string }) => x.name)).toEqual(['曝光次数', '商品浏览', '进入下单', '完成支付', '签署合同', '首季续费'])
    expect(a.data.channels.reduce((n: number, x: { value: number }) => n + x.value, 0)).toBe(a.data.paidCount)
    expect(a.data.retention.some((x: { demo: boolean }) => x.demo)).toBe(true)
  })
})

describe('第二期 B：合规中心（资质证照、资金公示）', () => {
  it('上传证照进入待核验；类型不对 400；基地备案证只读；重置后清空', async () => {
    const h = await admin(2, 'qingshan')
    const bad = await post('/admin-api/farm/cert/create', h, { name: '检测报告', no: 'X1', type: '随便' })
    expect(bad.code).toBe(400)
    const ok = await post('/admin-api/farm/cert/create', h, { name: '新版检测报告', no: 'CNAS-2026-1', type: '建议', validUntil: Date.now() + 10 * 86400_000, publicMode: 'private' })
    expect(ok.code).toBe(0)
    const list = await get('/admin-api/farm/cert/page', h, { pageSize: 100 })
    const mine = list.data.list.find((x: { id: number }) => x.id === ok.data)
    expect(mine.verify).toBe('pending')
    expect(mine.expiringSoon).toBe(true)
    expect(list.data.stats.records.some((x: { text: string }) => x.text.includes('新版检测报告'))).toBe(true)
    const t = await put('/admin-api/farm/cert/update-public', h, { id: ok.data, publicMode: 'public' })
    expect(t.code).toBe(0)
    const base = list.data.list.find((x: { source: string }) => x.source === 'base')
    expect(base.editable).toBe(false)
    const ro = await put('/admin-api/farm/cert/update-public', h, { id: base.id, publicMode: 'private' })
    expect(ro.code).toBe(400)
    await request(app).post('/__mock/reset')
    const h2 = await admin(2, 'qingshan')
    const after = await get('/admin-api/farm/cert/page', h2, { pageSize: 100 })
    expect(after.data.list.some((x: { name: string }) => x.name === '新版检测报告')).toBe(false)
  })

  it('资金公示：开关保存后能读到；预览里的保单编号取自证照', async () => {
    const h = await admin(2, 'qingshan')
    const before = await get('/admin-api/farm/fund-notice/get', h)
    expect(before.data.fields.find((f: { key: string }) => f.key === 'monthlyTotal').enabled).toBe(false)
    expect(before.data.preview.policyNo).toBe('PICC-2026-0188')
    const s = await put('/admin-api/farm/fund-notice/save', h, { fields: { monthlyTotal: true, flowChart: false } })
    expect(s.code).toBe(0)
    const after = await get('/admin-api/farm/fund-notice/get', h)
    expect(after.data.fields.find((f: { key: string }) => f.key === 'monthlyTotal').enabled).toBe(true)
    expect(after.data.fields.find((f: { key: string }) => f.key === 'flowChart').enabled).toBe(false)
    expect(after.data.usage.reduce((n: number, x: { amount: number }) => n + x.amount, 0)).toBe(after.data.usageTotal)
  })
})

describe('第二期 B：IoT 设备、视频监控', () => {
  it('设备由资产派生；只有离线设备能派单；手动添加的设备进台账', async () => {
    const h = await admin(2, 'qingshan')
    const d = await get('/admin-api/farm/device/page', h, { pageSize: 100 })
    expect(d.data.stats.cameraCount).toBe(5)
    const offline = d.data.list.find((x: { status: string }) => x.status === 'offline')
    const online = d.data.list.find((x: { status: string }) => x.status === 'online')
    expect((await put('/admin-api/farm/device/dispatch', h, { code: online.code })).code).toBe(400)
    expect((await put('/admin-api/farm/device/dispatch', h, { code: offline.code })).code).toBe(0)
    const c = await post('/admin-api/farm/device/create', h, { kind: 'sensor', typeName: '气象站', name: 'B 区气象站' })
    expect(c.code).toBe(0)
    const d2 = await get('/admin-api/farm/device/page', h, { pageSize: 100 })
    expect(d2.data.list.find((x: { code: string }) => x.code === offline.code).dispatched).toBe(true)
    expect(d2.data.list.some((x: { code: string; custom: boolean }) => x.code === c.data && x.custom)).toBe(true)
    const cam = await get('/admin-api/farm/camera/overview', h)
    expect(cam.data.stats.cameraCount).toBe(5)
    expect(cam.data.cameras.every((x: { kind: string }) => x.kind === 'camera')).toBe(true)
  })
})

describe('第二期 B：托管模式配置、消息模板', () => {
  it('托管模式配置与认养商品是同一份三档口径；改商品后配置页同步变', async () => {
    const h = await admin(2, 'qingshan')
    const cfg = await get('/admin-api/farm/hosting-config/get', h, { productId: 1 })
    expect(cfg.code).toBe(0)
    const prod = (await get('/admin-api/farm/product/get', h, { id: 1 })).data
    for (const m of cfg.data.modes) expect(m.price).toBe(prod.careModes.find((c: { mode: string }) => c.mode === m.mode).price)
    expect(cfg.data.modes.map((m: { price: number }) => m.price)).toEqual([128000, 86000, 50000])
    expect(cfg.data.modes[1].priceDelta).toBe(-42000)
    expect(cfg.data.modes[0].supplyPackage).toEqual([])
    // 农药类不可零售，不会进默认农资包
    expect(cfg.data.modes[1].supplyPackage.every((s: { cat: string }) => s.cat !== 'pesticide')).toBe(true)
    const modes = prod.careModes.map((c: { mode: string; price: number }) => (c.mode === 'semi' ? { ...c, price: 90000 } : c))
    expect((await put('/admin-api/farm/product/update', h, { id: 1, careModes: modes })).code).toBe(0)
    const cfg2 = await get('/admin-api/farm/hosting-config/get', h, { productId: 1 })
    expect(cfg2.data.modes[1].price).toBe(90000)
    expect(cfg2.data.modes[1].priceDelta).toBe(-38000)
  })

  it('消息模板：违禁词被拦；停用后不能测试发送；测试发送只返回预览', async () => {
    const h = await admin(2, 'qingshan')
    const bad = await post('/admin-api/farm/msg-template/create', h, { name: '活动', trigger: '下单', channels: ['短信'], content: '稳赚不赔，回报丰厚' })
    expect(bad.code).toBe(1_030_000_000)
    expect(bad.data.words).toEqual(expect.arrayContaining(['稳赚', '回报']))
    const okc = await post('/admin-api/farm/msg-template/create', h, { name: '到园提醒', trigger: '预约前一天', channels: ['订阅消息'], content: '您预约的 {日期} 到访已确认' })
    expect(okc.code).toBe(0)
    const sent = await post('/admin-api/farm/msg-template/test-send', h, { id: okc.data })
    expect(sent.data.sent).toBe(false)
    expect(sent.data.preview).toBe('您预约的 示例 到访已确认')
    await put('/admin-api/farm/msg-template/update-status', h, { id: okc.data, status: 1 })
    expect((await post('/admin-api/farm/msg-template/test-send', h, { id: okc.data })).code).toBe(400)
    const upd = await put('/admin-api/farm/msg-template/update', h, { id: okc.data, content: '年化收益' })
    expect(upd.code).toBe(1_030_000_000)
    const lst = await get('/admin-api/farm/msg-template/page', h, { pageSize: 50 })
    expect(lst.data.total).toBe(7)
  })
})
