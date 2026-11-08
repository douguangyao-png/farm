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

beforeEach(async () => { await request(app).post('/__mock/reset') })

describe('第一期补齐 D：农资下单地址', () => {
  it('express 三项必填、手机号 11 位；pickup/store 可空；后台列表和 C 端列表返回地址', async () => {
    const m = await member(2, '13800000001')
    const base = { spuId: 3, qty: 1, adoptionId: 1 }
    const url = '/app-api/farm/supply-order/create'
    expect((await post(url, m, { ...base, deliverWay: 'express' })).code).toBe(400)
    expect((await post(url, m, { ...base, deliverWay: 'express', receiverName: '张三', receiverMobile: '1380000', address: '安吉县某路 1 号' })).code).toBe(400)
    expect((await post(url, m, { ...base, deliverWay: 'express', receiverName: '张三', receiverMobile: '13800000009', address: '' })).code).toBe(400)
    expect((await post(url, m, { ...base, deliverWay: 'express', receiverName: '', receiverMobile: '13800000009', address: 'x' })).code).toBe(400)
    const ok1 = await post(url, m, { ...base, deliverWay: 'express', receiverName: '张三', receiverMobile: '13800000009', address: '安吉县某路 1 号' })
    expect(ok1.code).toBe(0)
    const ok2 = await post(url, m, { ...base, deliverWay: 'pickup' })
    expect(ok2.code).toBe(0)
    const ok3 = await post(url, m, base)
    expect(ok3.code).toBe(0)

    const mine = await get('/app-api/farm/supply-order/list', m)
    const r1 = mine.data.find((x: { id: number }) => x.id === ok1.data.id)
    expect(r1).toMatchObject({ deliverWay: 'express', receiverName: '张三', receiverMobile: '13800000009', address: '安吉县某路 1 号' })
    const h = await admin(2, 'qingshan')
    const page = await get('/admin-api/farm/supply-order/page', h, { pageSize: 100 })
    const row = page.data.list.find((x: { id: number }) => x.id === ok1.data.id)
    expect(row).toMatchObject({ receiverName: '张三', receiverMobile: '13800000009', address: '安吉县某路 1 号' })
    const row2 = page.data.list.find((x: { id: number }) => x.id === ok2.data.id)
    expect(row2.address ?? '').toBe('')
  })
})

describe('第一期补齐 D：商品详情农资套餐', () => {
  it('supplyPackages 与后台 hosting-config 的 supplyPackage 一致；full 为空', async () => {
    const detail = await get('/app-api/farm/product/get', { 'tenant-id': '2' }, { id: 1 })
    expect(detail.code).toBe(0)
    const sp = detail.data.supplyPackages as { mode: string; items: { id: number; recommended: boolean }[] }[]
    expect(sp.map((x) => x.mode)).toEqual(detail.data.careModes.map((c: { mode: string }) => c.mode))
    expect(sp.find((x) => x.mode === 'full')!.items).toEqual([])
    const h = await admin(2, 'qingshan')
    const cfg = await get('/admin-api/farm/hosting-config/get', h, { productId: 1 })
    for (const s of sp) {
      const mode = cfg.data.modes.find((x: { mode: string }) => x.mode === s.mode)
      expect(s.items).toEqual(mode.supplyPackage)
      if (s.items.length) expect(s.items.filter((i) => i.recommended).length).toBe(1)
    }
    expect(sp.some((x) => x.mode !== 'full' && x.items.length > 0)).toBe(true)
  })
})
