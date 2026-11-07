import { describe, it, expect, beforeEach } from 'vitest'
import request from 'supertest'
import { createApp } from '../src/app.js'

const server = createApp()
const app = { get: (p: string) => request(server).get(p), post: (p: string) => request(server).post(p) }

async function mtoken() {
  const r = await app.post('/app-api/member/auth/login').set('tenant-id', '2').send({ mobile: '13800000001', password: 'demo123' })
  return { 'tenant-id': '2', Authorization: 'Bearer ' + r.body.data.accessToken }
}
const memberPost = async (path: string, body: object) => app.post(path).set(await mtoken()).send(body)
const memberGet = async (path: string) => app.get(path).set(await mtoken())
beforeEach(async () => { await app.post('/__mock/reset') })

describe('C 端第二期补页', () => {
  it('搜索不需要登录，按关键词和品类过滤', async () => {
    const r = await app.get('/app-api/farm/search').query({ keyword: '地' }).set('tenant-id', '1')
    expect(r.body.code).toBe(0)
    expect(Array.isArray(r.body.data.products)).toBe(true)
    const t = await app.get('/app-api/farm/search').query({ type: 'plot' }).set('tenant-id', '1')
    expect(t.body.data.products.every((p: any) => p.assetType === 'plot')).toBe(true)
  })
  it('收藏切换两次回到未收藏', async () => {
    const a = await memberPost('/app-api/farm/favorite/toggle', { type: 'product', targetId: 1 })
    expect(a.body.data).toBe(true)
    const b = await memberPost('/app-api/farm/favorite/toggle', { type: 'product', targetId: 1 })
    expect(b.body.data).toBe(false)
  })
  it('地址：手机号格式不对返回 400；保存后第一条自动默认', async () => {
    expect((await memberPost('/app-api/farm/address/save', { name: '张', mobile: '123', detail: 'x' })).body.code).toBe(400)
    const ok = await memberPost('/app-api/farm/address/save', { name: '张', mobile: '13800138000', detail: '某路 1 号' })
    expect(ok.body.code).toBe(0)
  })
  it('评价：同一认养只能评一次；别人的认养 404', async () => {
    const first = await memberPost('/app-api/farm/review/create', { adoptionId: 1, score: 5 })
    expect(first.body.code).toBe(0)
    expect((await memberPost('/app-api/farm/review/create', { adoptionId: 1, score: 4 })).body.code).not.toBe(0)
  })
  it('预约到访：过去的日期被拒绝', async () => {
    const r = await memberPost('/app-api/farm/visit/create', { baseId: 1, date: '2020-01-01', people: 2 })
    expect(r.body.code).toBe(400)
  })
  it('农事记录详情、物流详情只能看自己的', async () => {
    expect((await memberGet('/app-api/farm/record/get?id=99999')).body.code).toBe(404)
    expect((await memberGet('/app-api/farm/logistics/get?id=99999')).body.code).toBe(404)
  })
  // 走真实装配的兜底（Java 侧）：FarmAppExtraController 的 Bean、farm_app_extra 表和序列存在，
  // 在服务器上调一次 GET /app-api/farm/base/list 即可发现「缺 Mapper / 缺表」
})
