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


let s: H, li: H, h: H
beforeEach(async () => {
  await request(app).post('/__mock/reset')
  s = await admin(2, 'zhangguanjia')
  li = await admin(2, 'liguanjia')
  h = await admin(2, 'qingshan')
})

describe('管家端补充（第二期）', () => {
  it('配送确认：只能处理自己负责的认养；发货、核销走通', async () => {
    const list = await get('/admin-api/farm/steward/shipment/list', s)
    expect(list.code).toBe(0)
    expect(list.data.stats).toHaveProperty('pending')
    const h = list.data.pending[0]
    if (h) {
      expect((await post('/admin-api/farm/steward/shipment/create', s, { harvestId: h.harvestId, carrier: '', logisticsNo: 'X' })).msg).toBe('请填写承运商')
      const c = await post('/admin-api/farm/steward/shipment/create', s, { harvestId: h.harvestId, carrier: '顺丰冷链', logisticsNo: 'SF100' })
      expect(c.code).toBe(0)
      expect((await put('/admin-api/farm/steward/shipment/sign', s, { id: c.data })).code).toBe(0)
      expect((await put('/admin-api/farm/steward/shipment/sign', s, { id: c.data })).code).toBe(1_030_000_002)
    }
    // 另一个管家不能操作
    const other = await post('/admin-api/farm/steward/shipment/create', li, { harvestId: h?.harvestId ?? 1, carrier: 'a', logisticsNo: 'b' })
    expect(other.code).not.toBe(0)
  })
  it('异常上报：灾害必须留影像；损耗必须填数量', async () => {
    expect((await post('/admin-api/farm/steward/alarm/create', s, { type: '气象灾害', content: '冰雹' })).msg).toBe('损耗、灾害、病虫害必须留现场影像')
    expect((await post('/admin-api/farm/steward/alarm/create', s, { type: '损耗登记', content: '死亡', photos: ['x'] })).msg).toBe('请填写损耗数量')
    expect((await post('/admin-api/farm/steward/alarm/create', s, { type: '设备异常', content: '摄像头离线' })).code).toBe(0)
    const l = await get('/admin-api/farm/steward/alarm/list', s)
    expect(l.data.length).toBeGreaterThan(0)
  })
  it('聊天：只能和自己负责的认养人聊', async () => {
    const cv = await get('/admin-api/farm/steward/message/conversations', s)
    expect(cv.code).toBe(0)
    if (cv.data.list[0]) {
      const mid = cv.data.list[0].memberId
      expect((await post('/admin-api/farm/steward/message/send', s, { memberId: mid, content: '你好' })).code).toBe(0)
      expect((await get('/admin-api/farm/steward/message/list', s, { memberId: mid })).data.list.at(-1).content).toBe('你好')
    }
    expect((await post('/admin-api/farm/steward/message/send', s, { memberId: 999999, content: 'x' })).code).toBe(1_030_000_007)
  })
  it('只读接口都能返回，非管家 403', async () => {
    for (const p of ['self-guide/list', 'record/list', 'asset/list', 'supply-task/list', 'performance']) {
      expect((await get(`/admin-api/farm/steward/${p}`, s)).code).toBe(0)
      expect((await get(`/admin-api/farm/steward/${p}`, h)).code).toBe(403)
    }
  })
})
