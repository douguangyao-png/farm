import { Router, type RequestHandler } from 'express'
import multer from 'multer'
import { getDb, nextId, type Article, type ArticleCategory, type Feedback } from '../store.js'
import { ok, requireLogin } from '../http.js'
import { page, q, like, eqNum, byNewest, tenantOf, isPlatform, currentUser, notFound, badRequest, requirePlatform } from '../farm/common.js'

/** 文章（R-11，芋道商城接口原样）、问题反馈（R-12）、文件上传。挂在 /admin-api 下。 */
export const contentRouter = Router()
const db = getDb
const adminLogin = requireLogin('admin')

// ---------- 文章分类 ----------
contentRouter.use(['/promotion', '/farm/feedback'], adminLogin)

const myCats = (t: number) => db().articleCategories.filter((c) => c.tenantId === t)
contentRouter.get('/promotion/article-category/page', (req, res) =>
  ok(res, page(req, myCats(tenantOf(req)).filter((c) => like(c.name, q(req, 'name')) && eqNum(c.status, q(req, 'status'))).sort((a, b) => a.sort - b.sort)))
)
contentRouter.get('/promotion/article-category/list-all-simple', (req, res) =>
  ok(res, myCats(tenantOf(req)).filter((c) => c.status === 0).map((c) => ({ id: c.id, name: c.name })))
)
contentRouter.get('/promotion/article-category/get', (req, res) => {
  const c = myCats(tenantOf(req)).find((x) => x.id === Number(req.query.id))
  return c ? ok(res, c) : notFound(res, '文章分类')
})
contentRouter.post('/promotion/article-category/create', (req, res) => {
  if (!String(req.body?.name ?? '').trim()) return badRequest(res, '分类名称不能为空')
  const c: ArticleCategory = { id: nextId(db().articleCategories), tenantId: tenantOf(req), name: String(req.body.name).trim(), picUrl: req.body.picUrl ?? '',
    sort: Number(req.body.sort) || 0, status: Number(req.body.status) || 0, createTime: Date.now() }
  db().articleCategories.push(c)
  return ok(res, c.id)
})
contentRouter.put('/promotion/article-category/update', (req, res) => {
  const c = myCats(tenantOf(req)).find((x) => x.id === Number(req.body?.id))
  if (!c) return notFound(res, '文章分类')
  if (req.body.name !== undefined && !String(req.body.name).trim()) return badRequest(res, '分类名称不能为空')
  for (const k of ['name', 'picUrl', 'sort', 'status'] as const) if (req.body[k] !== undefined) (c as unknown as Record<string, unknown>)[k] = req.body[k]
  return ok(res, true)
})
contentRouter.delete('/promotion/article-category/delete', (req, res) => {
  const c = myCats(tenantOf(req)).find((x) => x.id === Number(req.query.id))
  if (!c) return notFound(res, '文章分类')
  if (db().articles.some((a) => a.categoryId === c.id)) return badRequest(res, '分类下还有文章，不能删除')
  db().articleCategories.splice(db().articleCategories.indexOf(c), 1)
  return ok(res, true)
})

// ---------- 文章 ----------
const myArticles = (t: number) => db().articles.filter((a) => a.tenantId === t)
contentRouter.get('/promotion/article/page', (req, res) =>
  ok(res, page(req, myArticles(tenantOf(req)).filter((a) => like(a.title, q(req, 'title')) && like(a.author, q(req, 'author')) && eqNum(a.categoryId, q(req, 'categoryId')) && eqNum(a.status, q(req, 'status'))).sort(byNewest)))
)
contentRouter.get('/promotion/article/get', (req, res) => {
  const a = myArticles(tenantOf(req)).find((x) => x.id === Number(req.query.id))
  return a ? ok(res, a) : notFound(res, '文章')
})
function checkArticle(body: Partial<Article>, t: number): string | null {
  if (!String(body.title ?? '').trim()) return '标题不能为空'
  if (!myCats(t).some((c) => c.id === Number(body.categoryId))) return '请选择文章分类'
  return null
}
contentRouter.post('/promotion/article/create', (req, res) => {
  const t = tenantOf(req)
  const err = checkArticle(req.body ?? {}, t)
  if (err) return badRequest(res, err)
  const b = req.body
  const a: Article = { id: nextId(db().articles), tenantId: t, categoryId: Number(b.categoryId), title: String(b.title).trim(), author: b.author ?? currentUser(req)?.nickname ?? '',
    picUrl: b.picUrl ?? '', introduction: b.introduction ?? '', content: b.content ?? '', sort: Number(b.sort) || 0, status: Number(b.status) || 0, browseCount: 0,
    recommendHot: !!b.recommendHot, recommendBanner: !!b.recommendBanner, spuId: Number(b.spuId) || 0, createTime: Date.now() }
  db().articles.push(a)
  return ok(res, a.id)
})
contentRouter.put('/promotion/article/update', (req, res) => {
  const t = tenantOf(req)
  const a = myArticles(t).find((x) => x.id === Number(req.body?.id))
  if (!a) return notFound(res, '文章')
  const err = checkArticle({ ...a, ...req.body }, t)
  if (err) return badRequest(res, err)
  for (const k of ['categoryId', 'title', 'author', 'picUrl', 'introduction', 'content', 'sort', 'status', 'recommendHot', 'recommendBanner', 'spuId'] as const)
    if (req.body[k] !== undefined) (a as unknown as Record<string, unknown>)[k] = req.body[k]
  return ok(res, true)
})
contentRouter.delete('/promotion/article/delete', (req, res) => {
  const a = myArticles(tenantOf(req)).find((x) => x.id === Number(req.query.id))
  if (!a) return notFound(res, '文章')
  db().articles.splice(db().articles.indexOf(a), 1)
  return ok(res, true)
})

// ---------- 问题反馈（R-12） ----------
const TYPES = ['bug', 'advice', 'ask']
const tenantName = (id: number) => db().tenants.find((t) => t.id === id)?.name ?? ''
const visible = (t: number, platform: boolean) => db().feedbacks.filter((f) => platform || f.tenantId === t)
const feedbackView = (f: Feedback) => ({ ...f, tenantName: tenantName(f.tenantId) })

contentRouter.post('/farm/feedback/create', (req, res) => {
  const b = req.body ?? {}
  if (!TYPES.includes(b.type)) return badRequest(res, '请选择反馈类型')
  if (!String(b.content ?? '').trim()) return badRequest(res, '请填写反馈内容')
  const shots: string[] = Array.isArray(b.screenshots) ? b.screenshots.filter(Boolean) : []
  if (shots.length > 3) return badRequest(res, '最多上传 3 张截图')
  const max = db().feedbacks.reduce((m, f) => Math.max(m, Number(f.no.slice(3)) || 0), 0)
  const now = Date.now()
  const u = currentUser(req)
  const f = { id: nextId(db().feedbacks), tenantId: tenantOf(req), no: `FB-${String(max + 1).padStart(4, '0')}`, type: b.type, content: String(b.content).trim(), screenshots: shots,
    pageTitle: String(b.pageTitle ?? ''), pagePath: String(b.pagePath ?? ''), userAgent: String(b.userAgent ?? req.header('user-agent') ?? ''),
    creatorId: u?.id ?? 0, creatorName: u?.nickname ?? '', status: 0, replies: [], createTime: now, updateTime: now }
  db().feedbacks.push(f)
  return ok(res, { id: f.id, no: f.no })
})
contentRouter.get('/farm/feedback/page', (req, res) => {
  const rows = visible(tenantOf(req), isPlatform(req)).filter((f) => (q(req, 'type') === undefined || f.type === q(req, 'type')) && eqNum(f.status, q(req, 'status')) && like(f.no, q(req, 'no')))
    .sort(byNewest).map(feedbackView)
  return ok(res, page(req, rows))
})
contentRouter.get('/farm/feedback/get', (req, res) => {
  const f = visible(tenantOf(req), isPlatform(req)).find((x) => x.id === Number(req.query.id))
  return f ? ok(res, feedbackView(f)) : notFound(res, '反馈')
})
contentRouter.post('/farm/feedback/reply', requirePlatform, (req, res) => {
  const f = db().feedbacks.find((x) => x.id === Number(req.body?.id))
  if (!f) return notFound(res, '反馈')
  if (!String(req.body.content ?? '').trim()) return badRequest(res, '请填写回复内容')
  const now = Date.now()
  f.replies.push({ id: f.replies.length + 1, content: String(req.body.content).trim(), creatorName: currentUser(req)?.nickname ?? '平台', createTime: now })
  if (f.status === 0) f.status = 10
  f.updateTime = now
  return ok(res, true)
})
contentRouter.put('/farm/feedback/update-status', requirePlatform, (req, res) => {
  const f = db().feedbacks.find((x) => x.id === Number(req.body?.id))
  if (!f) return notFound(res, '反馈')
  if (![0, 10, 20, 30].includes(Number(req.body.status))) return badRequest(res, '状态不正确')
  f.status = Number(req.body.status)
  f.updateTime = Date.now()
  return ok(res, true)
})

// ---------- 文件上传（后台、管家端；C 端另挂一份） ----------
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } })
export const uploadHandler: RequestHandler[] = [
  (req, res, next) => upload.single('file')(req, res, (err: unknown) => (err ? badRequest(res, '文件不能超过 5MB') : next())),
  (req, res) => {
    const f = req.file
    if (!f) return badRequest(res, '请选择文件')
    const url = `data:${f.mimetype || 'application/octet-stream'};base64,${f.buffer.toString('base64')}`
    db().files.push({ id: nextId(db().files), url })
    return ok(res, url)
  }
]
contentRouter.post('/infra/file/upload', adminLogin, ...uploadHandler)
