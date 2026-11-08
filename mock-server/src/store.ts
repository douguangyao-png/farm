import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

/** 种子目录：每个 json 文件是一个顶层集合，文件名即集合名 */
const SEED_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed')

// ---------- 系统 ----------
export interface Tenant { id: number; name: string; contactName?: string; contactMobile?: string; status: number; packageId: number; expireTime?: number; websites?: string[]; accountCount?: number; createTime?: number }
export interface TenantPackage { id: number; name: string; status: number; price: number; remark: string; menuIds: number[]; createTime: number }
export interface Menu { id: number; parentId: number; name: string; path: string; component?: string; componentName?: string; icon?: string; visible: boolean; keepAlive: boolean; alwaysShow?: boolean; permission?: string }
export interface Role { id: number; tenantId: number; code: string; name: string; menuIds: number[] | '*' }
export interface User { id: number; tenantId: number; username: string; password: string; nickname: string; avatar?: string; deptId?: number; roleIds: number[]; kind: 'admin' | 'steward'; mobile?: string; baseId?: number }
export interface Member { id: number; tenantId: number; mobile: string; password: string; nickname: string; avatar?: string; address?: string }
export interface DictType { id: number; name: string; type: string; status: number; remark?: string }
export interface DictData { dictType: string; value: string; label: string; colorType?: string; cssClass?: string }

// ---------- 业务（字段说明见 docs/api/farm-接口契约.md） ----------
export type CareMode = 'full' | 'semi' | 'self'
export interface Base { id: number; tenantId: number; name: string; address: string; lng: number; lat: number; areaMu: number; certNo: string; cropSummary: string; status: number; cover: string; createTime: number }
export interface Asset { id: number; tenantId: number; baseId: number; assetType: 'plot' | 'animal' | 'tree'; code: string; name: string; specs: { areaM2?: number; crop?: string; breed?: string; count?: number }; status: number; cameraName: string | null; sensorName: string | null; createTime: number }
export interface CareModeConfig { mode: CareMode; enabled: boolean; price: number; slogan: string; outputPromise: string; contractVersion: string; weeklyTodo: number; insurance: string; duty: { item: string; owner: 'farm' | 'member' }[] }
export interface Product { id: number; tenantId: number; baseId: number; assetType: Asset['assetType']; title: string; cover: string; description: string; cycleDays: number; areaM2: number | null; basePrice: number; stock: number; sold: number; status: number; careModes: CareModeConfig[]; createTime: number }
export interface Order { id: number; tenantId: number; no: string; memberId: number; productId: number; careMode: CareMode; amount: number; status: number; assetId: number | null; adoptionId: number | null; contractVersion: string; contractAgreed: boolean; receiverName: string | null; receiverMobile: string | null; address: string | null; createTime: number; payTime: number | null
  /** 第一期补齐：来源渠道 mini/h5/scan/steward；缺省按 h5 显示 */
  channel?: string; packageName?: string | null; couponName?: string | null; couponAmount?: number
  gpsLng?: number | null; gpsLat?: number | null; gpsText?: string | null
  /** express 快递到家 / pickup 到基地自提 / farm_deliver 基地配送 */
  deliverWay?: string
  /** 合同手写签名（PNG dataURL）与签署时间 */
  signature?: string | null; signedAt?: number | null }
export interface Adoption { id: number; tenantId: number; orderId: number; memberId: number; productId: number; assetId: number; careMode: CareMode; startAt: number; endAt: number; status: number; promisedJin: number | null; harvestedJin: number; stewardId: number }
export interface Env { airTemp: number; airHumidity: number; soilMoisture: number }
export interface GrowthRecord { id: number; tenantId: number; adoptionId: number | null; assetId: number; stewardId: number; taskId: number | null; kind: 'farming' | 'harvest' | 'service'; title: string; content: string; photos: string[]; lng: number; lat: number; env: Env; checks: { exifTime: boolean; gpsFence: boolean; duplicate: boolean }; status: number; auditRemark: string | null; createTime: number; auditTime: number | null }
export interface Task { id: number; tenantId: number; adoptionId: number | null; assetId: number; owner: 'steward' | 'member'; stewardId: number | null; title: string; description: string; source: 'calendar' | 'iot' | 'manual' | 'service'; serviceOrderId: number | null; deadline: number; status: number; entrustPrice: number | null; finishTime: number | null; createTime: number }
export interface ServiceOrder { id: number; tenantId: number; no: string; fromTaskId: number | null; taskTitle: string; adoptionId: number | null; assetId: number; memberId: number; stewardId: number | null; serviceType: string; price: number; status: number; split: { steward: number; farm: number; platform: number }; proofPhotos: string[]; remark: string; createTime: number; acceptTime: number | null; finishTime: number | null }
export interface Supply { id: number; tenantId: number; name: string; cat: string; cover: string; price: number; cost: number; stock: number; unit: string; spec: string; fitModes: CareMode[]; batchNo: string; retailAllowed: boolean; needWorkorder: boolean; status: number; sold: number; description: string }
export interface SupplyOrder { id: number; tenantId: number; no: string; memberId: number; adoptionId: number; assetId: number; spuId: number; qty: number; amount: number; deliverWay: string; batchNo: string; traceState: number; usedAt: number | null; dosage: string | null; receiverName?: string; receiverMobile?: string; address?: string; createTime: number }
export interface InputRecord { id: number; tenantId: number; assetId: number; source: 'farm' | 'member'; name: string; batchNo: string; usedAt: number; dosage: string; operator: string }
export interface EnvSnapshot { temp: number; humidity: number; soilMoisture: number; light: number; capturedAt: number; source: string }
export interface Harvest { id: number; tenantId: number; adoptionId: number; assetId: number; crop: string; weightJin: number; harvestDate: number; photos: string[]; remark: string; shipmentId: number | null; envSnapshot?: EnvSnapshot | null }
/** 配送批次：status 0 待发 / 10 已发出 / 20 已完成 */
export interface ShipBatch { id: number; tenantId: number; no: string; harvestId: number; planDate: number; status: number; remark: string; createTime: number }
/** 畜禽批次：status 0 在栏 / 10 出栏中 / 20 已出栏 */
export interface LivestockBatch { id: number; tenantId: number; no: string; baseId: number; species: string; breed: string; inDate: number; count: number; outCount: number; status: number; pen: string; remark: string; createTime: number }
export interface Shipment { id: number; tenantId: number; no: string; harvestId: number; adoptionId: number; memberId: number; receiverName: string; receiverMobile: string; address: string; weightJin: number; carrier: string; logisticsNo: string; coldChain: boolean; status: number; tracks: { time: number; content: string }[]; createTime: number; batchId?: number | null }
export interface RiskAlert { id: number; tenantId: number; type: string; level: number; title: string; content: string; source: string; status: number; handleRemark: string | null; createTime: number; handleTime: number | null }
export interface ArticleCategory { id: number; tenantId: number; name: string; picUrl: string; sort: number; status: number; createTime: number }
export interface Article { id: number; tenantId: number; categoryId: number; title: string; author: string; picUrl: string; introduction: string; content: string; sort: number; status: number; browseCount: number; recommendHot: boolean; recommendBanner: boolean; spuId: number; createTime: number }
export interface Dept { id: number; tenantId: number; parentId: number; name: string; sort: number; status: number; leaderUserId?: number | null; phone?: string; email?: string }
export interface Feedback { id: number; tenantId: number; no: string; type: string; content: string; screenshots: string[]; pageTitle: string; pagePath: string; userAgent: string; creatorId: number; creatorName: string; status: number; replies: { id: number; content: string; creatorName: string; createTime: number }[]; createTime: number; updateTime: number }

export interface Db {
  tenants: Tenant[]; tenantPackages: TenantPackage[]; menus: Menu[]; roles: Role[]; users: User[]; members: Member[]
  diyTemplates: Record<string, unknown>[]; dictTypes: DictType[]; dictData: DictData[]
  bases: Base[]; assets: Asset[]; products: Product[]; orders: Order[]; adoptions: Adoption[]; records: GrowthRecord[]; tasks: Task[]
  serviceOrders: ServiceOrder[]; supplies: Supply[]; supplyOrders: SupplyOrder[]; inputRecords: InputRecord[]
  harvests: Harvest[]; shipments: Shipment[]; riskAlerts: RiskAlert[]; shipBatches: ShipBatch[]; livestockBatches: LivestockBatch[]
  articleCategories: ArticleCategory[]; articles: Article[]; feedbacks: Feedback[]; depts: Dept[]
  /** 上传的文件（data URL），重置后清空 */
  files: { id: number; url: string }[]
  /** 被违禁词拦截的发布记录（风险自检用），重置后清空 */
  complianceHits: { tenantId: number; productId: number | null; productTitle: string; words: string[]; time: number }[]
}

// ---------- 种子里的特殊写法 ----------
const ISO = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?([+-]\d{2}:\d{2}|Z)?$/
const OFFSET = /^OffsetH:(-?\d+(\.\d+)?)$/

/**
 * 种子里的三种写法在加载时换算：
 * - ISO 时间字符串 → 毫秒时间戳（芋道接口返回时间戳）
 * - "OffsetH:-50" → 现在往前 50 小时。「今日任务」「已逾期 2 天」在哪天演示都成立
 * - "svg:文字:#颜色" → 一张带文字的纯色占位图（data URL），离线可用，不依赖图床
 */
function convert(v: unknown, now: number): unknown {
  if (typeof v === 'string') {
    const off = OFFSET.exec(v)
    if (off) return Math.round(now + Number(off[1]) * 3600_000)
    if (ISO.test(v)) return new Date(v).getTime()
    if (v.startsWith('svg:')) {
      const [, label, color] = v.split(':')
      return placeholderImage(label, color)
    }
    return v
  }
  if (Array.isArray(v)) return v.map((x) => convert(x, now))
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, convert(x, now)]))
  }
  return v
}

export function placeholderImage(label = '', color = '#4caf50'): string {
  const safe = label.replace(/[<>&"']/g, '')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${color}"/><stop offset="1" stop-color="${color}" stop-opacity="0.65"/></linearGradient></defs>` +
    `<rect width="400" height="300" fill="url(#g)"/>` +
    `<text x="200" y="160" font-size="34" font-family="sans-serif" fill="#fff" text-anchor="middle">${safe}</text>` +
    `<text x="200" y="200" font-size="14" font-family="sans-serif" fill="#ffffffcc" text-anchor="middle">演示图片</text></svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

function loadSeed(): Db {
  const now = Date.now()
  const out: Record<string, unknown> = { files: [], complianceHits: [] }
  for (const f of readdirSync(SEED_DIR)) {
    if (!f.endsWith('.json')) continue
    out[f.slice(0, -5)] = convert(JSON.parse(readFileSync(join(SEED_DIR, f), 'utf-8')), now)
  }
  return out as unknown as Db
}

let db: Db = loadSeed()

export const getDb = (): Db => db

/** 恢复到种子数据（R-9） */
export function resetDb(): void {
  db = loadSeed()
}

/** 新记录的 id：取集合里最大 id + 1 */
export function nextId(list: { id: number }[]): number {
  return list.reduce((m, x) => Math.max(m, x.id), 0) + 1
}
