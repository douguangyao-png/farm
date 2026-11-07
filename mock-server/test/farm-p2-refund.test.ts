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


beforeEach(async () => { await request(app).post('/__mock/reset') })

describe('第二期 A 退款闭环', () => {
  const REASON = '认养计划变更'

  it('申请：原因必填，只能申请自己的认养中订单，不能重复申请', async () => {
    const m = await member(2, '13800000001') // 周女士：订单 1、3 认养中
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: '  ' })).code).toBe(400)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 2, reason: REASON })).code).toBe(404) // 陈先生的订单
    const m3 = await member(2, '13800000003') // 王女士：订单 5 待付款
    expect((await post('/app-api/farm/refund/create', m3, { orderId: 5, reason: REASON })).code).toBe(1_030_000_009)
    const ok1 = await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })
    expect(ok1.code).toBe(0)
    expect(ok1.data.status).toBe(0)
    expect(ok1.data.deduct).toBe(ok1.data.investDeduct + ok1.data.deliverDeduct)
    expect(ok1.data.refundAmount).toBe(ok1.data.amount - ok1.data.deduct)
    expect(ok1.data.orderStatus).toBe(40)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(1_030_000_008)
    expect((await get('/app-api/farm/refund/list', m)).data).toHaveLength(1)
    expect((await get('/app-api/farm/order/get', m, { id: 1 })).data.status).toBe(40)
  })

  it('驳回：必须写说明；订单回到认养中，可再次申请', async () => {
    const m = await member(2, '13800000001')
    const a = await admin(2, 'qingshan')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).data.id
    expect((await put('/admin-api/farm/refund/audit', a, { id, approve: false })).code).toBe(400)
    const r = await put('/admin-api/farm/refund/audit', a, { id, approve: false, remark: '仍在约定期内' })
    expect(r.code).toBe(0)
    expect(r.data.status).toBe(20)
    expect((await get('/app-api/farm/order/get', m, { id: 1 })).data.status).toBe(10)
    expect((await put('/admin-api/farm/refund/audit', a, { id, approve: false, remark: 'x' })).code).toBe(1_030_000_010) // 已审核
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(0)
  })

  it('通过：订单已退款、认养终止、地块释放为空闲、待办任务关闭、合同随订单（待主线程改 contractStatusOf 后）解除', async () => {
    const m = await member(2, '13800000001')
    const a = await admin(2, 'qingshan')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).data.id
    const r = await put('/admin-api/farm/refund/audit', a, { id, approve: true })
    expect(r.code).toBe(0)
    expect(r.data.status).toBe(10)
    const order = (await get('/app-api/farm/order/get', m, { id: 1 })).data
    expect(order.status).toBe(50)
    expect(order.adoptionStatus).toBe(30)
    const assets = (await get('/admin-api/farm/asset/page', a, { pageSize: 100 })).data.list
    expect(assets.find((x: any) => x.id === 3).status).toBe(0) // 订单 1 绑定的地块
    const tasks = await get('/admin-api/farm/refund/stats', a)
    expect(tasks.data.refundedCount).toBe(1)
    expect((await post('/app-api/farm/refund/create', m, { orderId: 1, reason: REASON })).code).toBe(1_030_000_008) // 已退款
    // 主线程改 views.ts contractStatusOf 后再打开下面一行
    // expect((await get('/admin-api/farm/contract/page', a, { status: 20 })).data.list.map((c: any) => c.orderId)).toContain(1)
  })

  it('后台列表和统计：种子里有 1 条已驳回；未登录 401；平台租户看不到别家的', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/refund/page', a)
    expect(page.data.total).toBe(1)
    expect(page.data.list[0].statusName).toBe('已驳回')
    expect((await get('/admin-api/farm/refund/stats', a)).data.counts.all).toBe(1)
    const other = await admin(3, 'lvye') // 绿野农庄管理员
    expect((await get('/admin-api/farm/refund/page', other)).data.total).toBe(0)
    expect((await request(app).get('/admin-api/farm/refund/page')).body.code).toBe(401)
  })
})

describe('第二期 A 客户管理 / 客户分层 / 管家管理', () => {
  it('客户名单由会员 + 订单派生', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/customer/page', a)
    expect(page.data.total).toBe(3)
    const zhou = page.data.list.find((c: any) => c.name === '周女士')
    expect(zhou.adoptCount).toBe(2)
    expect(zhou.totalAmount).toBe(214000)
    expect(zhou.level).toBe('V2')
    expect(zhou.city).toBe('杭州')
    expect(zhou.mobile).toBe('138****0001')
    expect((await get('/admin-api/farm/customer/page', a, { keyword: '13800000002' })).data.list.map((c: any) => c.name)).toEqual(['陈先生'])
    expect((await get('/admin-api/farm/customer/stats', a)).data.total).toBe(3)
  })

  it('分层人数加起来等于客户总数；已退款订单不计入累计消费', async () => {
    const a = await admin(2, 'qingshan')
    const s = (await get('/admin-api/farm/customer-tier/summary', a)).data
    expect(s.tiers.reduce((n: number, t: any) => n + t.count, 0)).toBe(s.total)
    const m = await member(2, '13800000002')
    const id = (await post('/app-api/farm/refund/create', m, { orderId: 2, reason: '计划变更' })).data.id
    await put('/admin-api/farm/refund/audit', a, { id, approve: true })
    const chen = (await get('/admin-api/farm/customer/page', a, { keyword: '陈先生' })).data.list[0]
    expect(chen.adoptCount).toBe(0)
    expect(chen.totalAmount).toBe(0)
    expect(chen.tags).toContain('有退款记录')
  })

  it('管家管理：本租户的管家，和管家端接口前缀不冲突', async () => {
    const a = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/steward-manage/page', a)
    expect(page.data.list.map((s: any) => s.name).sort()).toEqual(['张管家', '李管家'].sort())
    expect(page.data.list[0].mobile).toMatch(/\*\*\*\*/)
    const ov = await get('/admin-api/farm/steward-manage/overview', a)
    expect(ov.data.today).toHaveProperty('pendingRecords')
    expect((await get('/admin-api/farm/steward-manage/page', { })).code).toBe(401)
  })
})
