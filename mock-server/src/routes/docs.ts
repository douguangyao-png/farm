import { Router } from 'express'
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs'
import { join, resolve, relative, sep } from 'node:path'
import { ok, requireLogin } from '../http.js'
import { notFound, requirePlatform } from '../farm/common.js'

/**
 * 项目文档（R-13）：后台「系统管理 → 项目文档」查看需求、设计、方案与任务、原型。
 * 读仓库的 docs/ 目录（服务器上部署到 /opt/farmer-saas/docs）。只给平台管理员看：里面有定价、资金流向等内部内容。
 */
export const docsRouter = Router()
docsRouter.use(requireLogin('admin'), requirePlatform)

/** mock 服务的工作目录是 mock-server/，文档在它的上一级 docs/；可用 FARM_DOCS_DIR 覆盖 */
const DOCS_DIR = resolve(process.env.FARM_DOCS_DIR ?? join(process.cwd(), '..', 'docs'))

const CATEGORY: [RegExp, string][] = [
  [/^plans\//, '方案与任务'],
  [/^api\//, '设计'],
  [/^demo\//, '演示'],
  [/^(01|10|12)-/, '需求'],
  [/^(02|04|11)-/, '设计'],
  [/\.html$/, '原型'],
  [/./, '调研与定价']
]

interface DocItem { path: string; title: string; category: string; type: 'md' | 'html'; size: number; updateTime: number }

function walk(dir: string): string[] {
  if (!existsSync(dir)) return []
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const full = join(dir, e.name)
    if (e.isDirectory()) return walk(full)
    return /\.(md|html)$/.test(e.name) ? [full] : []
  })
}

function titleOf(full: string, type: 'md' | 'html'): string {
  const text = readFileSync(full, 'utf-8').slice(0, 4000)
  const m = type === 'md' ? /^#\s+(.+)$/m.exec(text) : /<title>([^<]+)<\/title>/i.exec(text)
  return m ? m[1].trim() : full.split(sep).pop()!
}

function listDocs(): DocItem[] {
  return walk(DOCS_DIR).map((full) => {
    const path = relative(DOCS_DIR, full).split(sep).join('/')
    const type: DocItem['type'] = path.endsWith('.html') ? 'html' : 'md'
    const st = statSync(full)
    return { path, title: titleOf(full, type), category: CATEGORY.find(([re]) => re.test(path))![1], type, size: st.size, updateTime: st.mtimeMs }
  }).sort((a, b) => a.path.localeCompare(b.path))
}

docsRouter.get('/list', (_req, res) => ok(res, listDocs()))

docsRouter.get('/get', (req, res) => {
  // 只允许取列表里有的文件，防止 ../ 越权读服务器上的其他文件
  const item = listDocs().find((d) => d.path === String(req.query.path ?? ''))
  if (!item) return notFound(res, '文档')
  return ok(res, { ...item, content: readFileSync(join(DOCS_DIR, item.path), 'utf-8') })
})
