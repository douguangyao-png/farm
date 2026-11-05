import { http } from '@/http/http'

/**
 * 管家端接口（契约 docs/api/farm-接口契约.md 第 3 节）
 * 时间为毫秒时间戳，金额为整数分
 */
const baseUrl = '/farm/steward'

export type TaskSource = 'calendar' | 'iot' | 'manual' | 'service'
export type CareMode = 'full' | 'semi' | 'self'

/** 环境快照 */
export interface FarmEnv {
  airTemp: number
  airHumidity: number
  soilMoisture: number
  soilTemp?: number
  ph?: number
  light?: number
  updateTime?: number
}

export interface StewardHome {
  stewardName: string
  baseName: string
  todo: {
    taskCount: number
    overdueCount?: number
    serviceOrderPending: number
    serviceOrderMine: number
  }
  todayDone: number
  monthDone: number
  pendingAuditCount?: number
  rejectedCount?: number
}

export interface StewardTask {
  id: number
  assetId: number
  assetCode: string
  baseName: string
  memberName: string
  careMode: CareMode | null
  title: string
  description: string
  source: TaskSource
  serviceOrderId: number | null
  deadline: number
  status: number // 0 待办、10 已完成、20 已委托代办
  finishTime: number | null
  overdue?: boolean
}

export interface StewardTaskDetail extends StewardTask {
  asset: { id: number, code: string, name: string, crop: string, areaM2: number | null, cameraName: string | null }
  env: FarmEnv
}

export interface CheckinReq {
  taskId: number
  content: string
  photos: string[]
  lng?: number
  lat?: number
}

export interface CheckinResp {
  recordId: number
  checks: { exifTime: boolean, gpsFence: boolean, duplicate: boolean }
  env: FarmEnv
}

export interface ServiceOrder {
  id: number
  no: string
  taskTitle: string
  assetId: number
  assetCode: string
  memberName: string
  stewardName: string
  serviceType: string
  price: number
  status: number // 0 待接单、10 已接单、20 已完成、30 已取消
  split: { steward: number, farm: number, platform: number }
  remark: string
  createTime: number
  acceptTime: number | null
  finishTime: number | null
}

export interface GrowthRecord {
  id: number
  title: string
  content: string
  photos: string[]
  status: number // 0 待审核、10 已通过、20 已驳回
  stewardName: string
  createTime: number
  checks: { exifTime: boolean, gpsFence: boolean, duplicate: boolean }
}

export interface StewardAssetDetail {
  asset: { id: number, code: string, name: string, assetType: 'plot' | 'animal' | 'tree', baseName: string, specs: { areaM2?: number, crop?: string, breed?: string, count?: number }, cameraName: string | null, sensorName: string | null }
  adoption: { id: number, careMode: CareMode, careModeName: string, startAt: number, endAt: number, promisedJin: number | null, harvestedJin: number } | null
  member: { name: string, mobile: string } | null
  env: { current: FarmEnv, series: { ts: number, airTemp: number, airHumidity: number, soilMoisture: number }[], sensorName: string | null, demo: boolean }
  records: GrowthRecord[]
  selfTaskWeek: { total: number, done: number, entrusted: number, overdue: number }
}

/** 工作台 */
export function getStewardHome() {
  return http.get<StewardHome>(`${baseUrl}/home`)
}

/** 我的任务列表（status 默认 0 待办） */
export function getStewardTaskList(status = 0) {
  return http.get<StewardTask[]>(`${baseUrl}/task/list`, { status })
}

/** 任务详情 */
export function getStewardTask(id: number) {
  return http.get<StewardTaskDetail>(`${baseUrl}/task/get`, { id })
}

/** 农事打卡 */
export function checkinStewardTask(data: CheckinReq) {
  return http.post<CheckinResp>(`${baseUrl}/task/checkin`, data as any)
}

/** 代管工单列表：pool 工单池 / mine 我的 */
export function getServiceOrderList(scope: 'pool' | 'mine') {
  return http.get<ServiceOrder[]>(`${baseUrl}/service-order/list`, { scope })
}

/** 接单 */
export function acceptServiceOrder(id: number) {
  return http.put<boolean>(`${baseUrl}/service-order/accept`, { id })
}

/** 标的详情 */
export function getStewardAsset(id: number) {
  return http.get<StewardAssetDetail>(`${baseUrl}/asset/get`, { id })
}
