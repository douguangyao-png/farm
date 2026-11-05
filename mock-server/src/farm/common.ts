import type { Request, Response, NextFunction } from 'express'
import { getDb, type Asset, type Env, type CareMode } from '../store.js'
import { fail, ERR, PLATFORM_TENANT_ID } from '../http.js'

/** 业务错误码（契约 0.2） */
export const BIZ = {
  BANNED_WORDS: 1_030_000_000,
  CONTRACT_NOT_AGREED: 1_030_000_001,
  ORDER_STATE: 1_030_000_002,
  NO_IDLE_ASSET: 1_030_000_003,
  SUPPLY_NEED_ADOPTION: 1_030_000_004,
  SUPPLY_NOT_RETAIL: 1_030_000_005,
  TASK_STATE: 1_030_000_006,
  NOT_MY_TASK: 1_030_000_007
} as const

export const CARE_MODE_NAME: Record<CareMode, string> = { full: '全托管', semi: '半托管', self: '不托管' }

export function page<T>(req: Request, rows: T[]) {
  const pageNo = Math.max(1, Number(req.query.pageNo ?? 1) || 1)
  const pageSize = Math.min(200, Math.max(1, Number(req.query.pageSize ?? 10) || 10))
  return { list: rows.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: rows.length }
}

/** 查询参数：空字符串、undefined 视为不过滤 */
export const q = (req: Request, key: string): string | undefined => {
  const v = req.query[key]
  return v === undefined || v === '' ? undefined : String(v)
}
export const like = (v: unknown, s: string | undefined) => s === undefined || String(v ?? '').includes(s)
export const eqNum = (v: unknown, s: string | undefined) => s === undefined || Number(s) === Number(v)
export const byNewest = <T extends { createTime: number }>(a: T, b: T) => b.createTime - a.createTime

/** 当前登录账号的租户（后台、管家端、C 端都用它；不用请求头，防止伪造） */
export const tenantOf = (req: Request): number => req.session!.tenantId
export const isPlatform = (req: Request) => req.session?.tenantId === PLATFORM_TENANT_ID
/** C 端浏览类接口不需要登录：租户取登录态，没登录取请求头 */
export const browseTenant = (req: Request): number | undefined => req.session?.tenantId ?? req.tenantId

export const currentUser = (req: Request) => getDb().users.find((u) => u.id === req.session!.userId)
export const currentMember = (req: Request) => getDb().members.find((m) => m.id === req.session!.userId)

export function requirePlatform(req: Request, res: Response, next: NextFunction) {
  if (!isPlatform(req)) return fail(res, ERR.FORBIDDEN, '只有平台管理员可以操作')
  next()
}
export function requireSteward(req: Request, res: Response, next: NextFunction) {
  if (currentUser(req)?.kind !== 'steward') return fail(res, ERR.FORBIDDEN, '只有管家账号可以使用管家端')
  next()
}

/** 按 id 取本租户的数据，取不到返回 undefined（调用方回 404） */
export function own<T extends { id: number; tenantId: number }>(list: T[], id: unknown, tenantId: number): T | undefined {
  return list.find((x) => x.id === Number(id) && x.tenantId === tenantId)
}
export const notFound = (res: Response, what = '数据') => fail(res, ERR.NOT_FOUND, `${what}不存在`)
export const badRequest = (res: Response, msg: string) => fail(res, ERR.BAD_REQUEST, msg)

export const maskMobile = (m?: string | null) => (m ? m.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '')

let seq = 0
/** 业务单号：前缀 + 年月日 + 3 位序号（演示用，重启后从 900 起避免和种子撞号） */
export function makeNo(prefix: string): string {
  seq = (seq + 1) % 100
  const d = new Date()
  const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  return `${prefix}${ymd}${String(900 + seq).padStart(3, '0')}`
}

// ---------- 环境数据（IoT 演示）：按资产 id 和小时确定性生成，刷新不乱跳 ----------
function wave(seed: number, hour: number, base: number, amp: number, period = 24) {
  const phase = (seed * 37) % period
  const noise = Math.sin(seed * 12.9898 + hour * 78.233) * 0.25
  return base + amp * Math.sin(((hour + phase) / period) * 2 * Math.PI) + noise
}
const round1 = (n: number) => Math.round(n * 10) / 10

export function envAt(asset: Asset, ts: number): Env & { soilTemp: number; ph: number; light: number } {
  const hour = Math.floor(ts / 3600_000)
  const dryPlot = asset.code === 'A-03' // A-03 演示「土壤湿度低于 22% 触发浇水任务」
  return {
    airTemp: round1(wave(asset.id, hour, 18, 5)),
    airHumidity: Math.round(wave(asset.id + 3, hour, 68, 12)),
    soilMoisture: round1(dryPlot ? wave(asset.id, hour, 19.5, 1.2) : wave(asset.id + 7, hour, 30, 3)),
    soilTemp: round1(wave(asset.id + 1, hour, 15, 2.5)),
    ph: round1(6.6 + Math.sin(asset.id) * 0.3),
    light: Math.max(0, Math.round(wave(asset.id, hour, 18000, 22000)))
  }
}

export function envView(asset: Asset) {
  const now = Date.now()
  const cur = envAt(asset, now)
  const series = Array.from({ length: 24 }, (_, i) => {
    const ts = Math.floor(now / 3600_000) * 3600_000 - (23 - i) * 3600_000
    const e = envAt(asset, ts)
    return { ts, airTemp: e.airTemp, airHumidity: e.airHumidity, soilMoisture: e.soilMoisture }
  })
  return { current: { ...cur, updateTime: now }, series, sensorName: asset.sensorName, demo: true }
}

export const envSnapshot = (asset: Asset): Env => {
  const e = envAt(asset, Date.now())
  return { airTemp: e.airTemp, airHumidity: e.airHumidity, soilMoisture: e.soilMoisture }
}
