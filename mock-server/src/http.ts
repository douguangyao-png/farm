import type { Request, Response, NextFunction } from 'express'
import { randomUUID } from 'node:crypto'

/** 芋道统一响应包：{ code, data, msg }，code=0 为成功 */
export const ok = (res: Response, data: unknown) => res.json({ code: 0, data, msg: '' })
export const fail = (res: Response, code: number, msg: string, data: unknown = null) => res.json({ code, data, msg })

/** 芋道的错误码（沿用 GlobalErrorCodeConstants） */
export const ERR = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  NOT_IMPLEMENTED: 501,
  // system 模块
  AUTH_LOGIN_BAD_CREDENTIALS: 1_002_000_000,
  TENANT_NOT_EXISTS: 1_002_015_000
} as const

/** 平台自己的租户编号，和芋道默认一致 */
export const PLATFORM_TENANT_ID = 1

export interface Session { token: string; scope: 'admin' | 'member'; userId: number; tenantId: number; refreshToken: string }

const sessions = new Map<string, Session>()

export function createSession(scope: Session['scope'], userId: number, tenantId: number) {
  const s: Session = { token: randomUUID().replace(/-/g, ''), refreshToken: randomUUID().replace(/-/g, ''), scope, userId, tenantId }
  sessions.set(s.token, s)
  return s
}

export const dropSession = (token?: string) => token && sessions.delete(token)
export const clearSessions = () => sessions.clear()

export function findByRefresh(refreshToken: string) {
  for (const s of sessions.values()) if (s.refreshToken === refreshToken) return s
  return undefined
}

declare module 'express-serve-static-core' {
  interface Request { session?: Session; tenantId?: number }
}

/** 解析 Authorization: Bearer <token> 和 tenant-id 请求头 */
export function attachContext(req: Request, _res: Response, next: NextFunction) {
  const auth = req.header('authorization')
  const token = auth?.startsWith('Bearer ') ? auth.slice(7) : undefined
  if (token) req.session = sessions.get(token)
  const t = req.header('tenant-id')
  if (t && /^\d+$/.test(t)) req.tenantId = Number(t)
  next()
}

/** 需要登录的接口：没 token 或 token 失效返回 401（芋道前端据此跳登录页） */
export function requireLogin(scope: Session['scope']) {
  return (req: Request, res: Response, next: NextFunction) => {
    const s = req.session
    if (!s || s.scope !== scope) return fail(res, ERR.UNAUTHORIZED, '账号未登录')
    // 登录态里的租户和请求头的租户不一致：视为越权（R-6）
    // 平台租户（id=1）的管理员可以访问所有租户
    if (s.tenantId !== PLATFORM_TENANT_ID && req.tenantId !== undefined && req.tenantId !== s.tenantId) return fail(res, ERR.FORBIDDEN, '没有该租户的访问权限')
    next()
  }
}
