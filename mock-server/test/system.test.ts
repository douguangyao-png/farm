import { describe, it, expect, beforeEach } from 'vitest'
import request from 'supertest'
import { createApp } from '../src/app.js'

const app = createApp()

async function login(tenantId: number, username: string, password: string) {
  const r = await request(app).post('/admin-api/system/auth/login').set('tenant-id', String(tenantId)).send({ username, password })
  return r.body
}

describe('系统接口（T1）', () => {
  beforeEach(async () => {
    await request(app).post('/__mock/reset')
  })

  it('按租户名查租户编号', async () => {
    const r = await request(app).get('/admin-api/system/tenant/get-id-by-name').query({ name: '青山农场' })
    expect(r.body).toEqual({ code: 0, data: 2, msg: '' })
  })

  it('经营者登录后拿到菜单，没有平台管理菜单', async () => {
    const { data } = await login(2, 'qingshan', 'demo123')
    expect(data.accessToken).toBeTruthy()
    const r = await request(app).get('/admin-api/system/auth/get-permission-info')
      .set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '2')
    expect(r.body.code).toBe(0)
    const names = r.body.data.menus.map((m: { name: string }) => m.name)
    expect(names).toContain('系统管理')
    expect(names).not.toContain('平台管理')
  })

  it('平台管理员看到平台管理菜单', async () => {
    const { data } = await login(1, 'admin', 'admin123')
    const r = await request(app).get('/admin-api/system/auth/get-permission-info')
      .set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '1')
    expect(r.body.data.menus.map((m: { name: string }) => m.name)).toContain('平台管理')
    expect(r.body.data.permissions).toEqual(['*:*:*'])
  })

  it('异常：密码错误', async () => {
    const body = await login(2, 'qingshan', 'wrong')
    expect(body.code).toBe(1_002_000_000)
    expect(body.data).toBeNull()
  })

  it('异常：租户不存在', async () => {
    const body = await login(99, 'qingshan', 'demo123')
    expect(body.code).toBe(1_002_015_000)
  })

  it('异常：没登录返回 401', async () => {
    const r = await request(app).get('/admin-api/system/auth/get-permission-info').set('tenant-id', '2')
    expect(r.body.code).toBe(401)
  })

  it('异常：A 租户的登录态访问 B 租户返回 403（R-6）', async () => {
    const { data } = await login(2, 'qingshan', 'demo123')
    const r = await request(app).get('/admin-api/system/auth/get-permission-info')
      .set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '3')
    expect(r.body.code).toBe(403)
  })

  it('恢复：重置后旧 token 失效，重新登录可用（R-9）', async () => {
    const { data } = await login(2, 'qingshan', 'demo123')
    await request(app).post('/__mock/reset')
    const r1 = await request(app).get('/admin-api/system/auth/get-permission-info')
      .set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '2')
    expect(r1.body.code).toBe(401)
    const again = await login(2, 'qingshan', 'demo123')
    expect(again.code).toBe(0)
  })

  it('刷新令牌换新 token，旧 token 作废', async () => {
    const { data } = await login(2, 'qingshan', 'demo123')
    const r = await request(app).post('/admin-api/system/auth/refresh-token').query({ refreshToken: data.refreshToken })
    expect(r.body.data.accessToken).not.toBe(data.accessToken)
    const old = await request(app).get('/admin-api/system/auth/get-permission-info')
      .set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '2')
    expect(old.body.code).toBe(401)
  })

  it('兜底：未实现的查询返回空，写操作明确提示未实现', async () => {
    const page = await request(app).get('/admin-api/system/post/page')
    expect(page.body.data).toEqual({ list: [], total: 0 })
    const write = await request(app).post('/admin-api/system/post/create').send({})
    expect(write.body.code).toBe(501)
    const un = await request(app).get('/__mock/unhandled')
    expect(un.body.data['POST /admin-api/system/post/create']).toBe(1)
  })
})

describe('C 端接口（T1）', () => {
  beforeEach(async () => {
    await request(app).post('/__mock/reset')
  })

  it('按 H5 域名找到租户，并拿到首页模板', async () => {
    const t = await request(app).get('/app-api/system/tenant/get-by-website').query({ website: 'localhost:3000' })
    expect(t.body.data.id).toBe(2)
    const tpl = await request(app).get('/app-api/promotion/diy-template/used').set('tenant-id', '2')
    expect(tpl.body.data.property.tabBar.items.length).toBeGreaterThan(0)
  })

  it('认养用户登录并取个人信息；异常：密码错误', async () => {
    const bad = await request(app).post('/app-api/member/auth/login').set('tenant-id', '2').send({ mobile: '13800000001', password: 'x' })
    expect(bad.body.code).toBe(1_002_000_000)
    const r = await request(app).post('/app-api/member/auth/login').set('tenant-id', '2').send({ mobile: '13800000001', password: 'demo123' })
    const me = await request(app).get('/app-api/member/user/get').set('Authorization', `Bearer ${r.body.data.accessToken}`).set('tenant-id', '2')
    expect(me.body.data.nickname).toBe('周女士')
  })

  it('异常：后台 token 不能调 C 端接口', async () => {
    const { data } = await login(2, 'qingshan', 'demo123')
    const r = await request(app).get('/app-api/member/user/get').set('Authorization', `Bearer ${data.accessToken}`).set('tenant-id', '2')
    expect(r.body.code).toBe(401)
  })
})

describe('C 端兼容芋道商城的请求习惯', () => {
  it('裸 token（不带 Bearer）也能识别；会员信息带地址；购物车按芋道格式返回空', async () => {
    const lg = await request(app).post('/app-api/member/auth/login').set('tenant-id', '2').send({ mobile: '13800000001', password: 'demo123' })
    const u = await request(app).get('/app-api/member/user/get').set('Authorization', lg.body.data.accessToken).set('tenant-id', '2')
    expect(u.body.data).toMatchObject({ nickname: '周女士', address: '浙江省杭州市西湖区文三路 90 号' })
    const c = await request(app).get('/app-api/trade/cart/list').set('tenant-id', '2')
    expect(c.body.data).toEqual({ validList: [], invalidList: [] })
  })
})

describe('登录不选租户：按用户名找租户', () => {
  beforeEach(async () => { await request(app).post('/__mock/reset') })
  it('平台、经营者、管家账号都能找到各自租户；不存在的返回 null；开通租户时用户名重复报错', async () => {
    const q = (username: string) => request(app).get('/admin-api/system/tenant/get-id-by-username').query({ username }).then((r) => r.body.data)
    expect(await q('admin')).toBe(1)
    expect(await q('qingshan')).toBe(2)
    expect(await q('lvye')).toBe(3)
    expect(await q('zhangguanjia')).toBe(2)
    expect(await q('nobody')).toBeNull()
    const lg = await request(app).post('/admin-api/system/auth/login').set('tenant-id', '1').send({ username: 'admin', password: 'admin123' })
    const h = { Authorization: `Bearer ${lg.body.data.accessToken}`, 'tenant-id': '1' }
    const r = await request(app).post('/admin-api/system/tenant/create').set(h).send({ name: '东篱农场', packageId: 2, username: 'qingshan', password: 'x' })
    expect(r.body.msg).toBe('管理员账号已被占用，请换一个')
  })
})
