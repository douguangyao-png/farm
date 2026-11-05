import { describe, it, expect, beforeEach } from 'vitest'
import request from 'supertest'
import { createApp } from '../src/app.js'

const app = createApp()

async function auth(tenantId: number, username: string) {
  const r = await request(app).post('/admin-api/system/auth/login').set('tenant-id', String(tenantId))
    .send({ username, password: username === 'admin' ? 'admin123' : 'demo123' })
  return { Authorization: `Bearer ${r.body.data.accessToken}`, 'tenant-id': String(tenantId) }
}

describe('系统管理列表页（用户、角色、字典）', () => {
  beforeEach(async () => { await request(app).post('/__mock/reset') })

  it('经营者看到本农场的角色，看不到别的农场和平台的', async () => {
    const r = await request(app).get('/admin-api/system/role/page').set(await auth(2, 'qingshan')).query({ pageNo: 1, pageSize: 10 })
    expect(r.body.code).toBe(0)
    expect(r.body.data.total).toBe(2)
    expect(r.body.data.list.map((x: { name: string }) => x.name)).toEqual(['经营者管理员', '管家'])
    expect(r.body.data.list[0]).toMatchObject({ type: 1, status: 0 })
  })

  it('角色名筛选、分页越界返回空列表但 total 正确', async () => {
    const h = await auth(2, 'qingshan')
    const f = await request(app).get('/admin-api/system/role/page').set(h).query({ name: '管家' })
    expect(f.body.data.list).toHaveLength(1)
    const p = await request(app).get('/admin-api/system/role/page').set(h).query({ pageNo: 99, pageSize: 10 })
    expect(p.body.data).toEqual({ list: [], total: 2 })
  })

  it('用户列表按租户隔离；查别的租户的用户返回不存在', async () => {
    const h = await auth(2, 'qingshan')
    const r = await request(app).get('/admin-api/system/user/page').set(h)
    expect(r.body.data.list.map((x: { username: string }) => x.username)).toEqual(['qingshan', 'zhangguanjia', 'liguanjia'])
    expect(r.body.data.list[0]).not.toHaveProperty('password')
    expect(r.body.data.list[0].deptName).toBe('青山农场')
    const other = await request(app).get('/admin-api/system/user/get').set(h).query({ id: 4 })
    expect(other.body.code).toBe(404)
  })

  it('字典类型和字典数据能查到，按类型过滤', async () => {
    const h = await auth(1, 'admin')
    const t = await request(app).get('/admin-api/system/dict-type/page').set(h)
    expect(t.body.data.total).toBe(4)
    const d = await request(app).get('/admin-api/system/dict-data/page').set(h).query({ dictType: 'system_role_type' })
    expect(d.body.data.list.map((x: { label: string }) => x.label)).toEqual(['内置', '自定义'])
  })

  it('未登录访问返回 401', async () => {
    const r = await request(app).get('/admin-api/system/role/page')
    expect(r.body.code).toBe(401)
  })

  it('写操作仍走兜底 501，不影响读', async () => {
    const h = await auth(2, 'qingshan')
    const w = await request(app).post('/admin-api/system/role/create').set(h).send({ name: 'x' })
    expect(w.body.code).toBe(501)
    const r = await request(app).get('/admin-api/system/role/page').set(h)
    expect(r.body.data.total).toBe(2)
  })
})

describe('个人中心', () => {
  beforeEach(async () => { await request(app).post('/__mock/reset') })
  it('管家能取到自己的资料，未登录 401', async () => {
    const r = await request(app).get('/admin-api/system/user/profile/get').set(await auth(2, 'zhangguanjia'))
    expect(r.body.data).toMatchObject({ username: 'zhangguanjia', nickname: '张管家', dept: { name: '青山农场' }, roles: [{ name: '管家' }] })
    expect(r.body.data).not.toHaveProperty('password')
    expect((await request(app).get('/admin-api/system/user/profile/get')).body.code).toBe(401)
  })
})
