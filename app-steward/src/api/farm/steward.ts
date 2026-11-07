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

// ================= 第二期补充（mock：farm-steward-extra.ts）=================

export interface PendingHarvest {
  harvestId: number
  crop: string
  weightJin: number
  harvestDate: number
  remark: string
  assetCode: string
  memberName: string
}
export interface StewardShipment {
  id: number
  no: string
  assetCode: string
  memberName: string
  receiverName: string
  receiverMobile: string
  address: string
  weightJin: number
  carrier: string
  logisticsNo: string
  coldChain: boolean
  status: number // 10 运输中（已发货）、20 已签收
  tracks: { time: number, content: string }[]
  createTime: number
}
export interface StewardShipmentList {
  stats: { pending: number, shipped: number, done: number }
  pending: PendingHarvest[]
  list: StewardShipment[]
}
export interface SelfGuideItem {
  taskId: number
  assetCode: string
  memberId: number
  memberName: string
  careMode: CareMode
  careModeName: string
  title: string
  description: string
  deadline: number
  state: 'todo' | 'reminded' | 'over' | 'done'
  entrustPrice: number | null
  remindedAt: number | null
}
export interface SelfGuide {
  stats: { plots: number, weekTodo: number, reminded: number, overdue: number }
  counts: { todo: number, reminded: number, over: number }
  list: SelfGuideItem[]
}
export interface StewardRecordList {
  stats: { all: number, pending: number, published: number, rejected: number }
  list: (GrowthRecord & { assetCode: string, auditRemark?: string | null })[]
}
export interface StewardAssetItem {
  id: number
  code: string
  name: string
  assetType: 'plot' | 'animal' | 'tree'
  crop: string
  areaM2: number | null
  raising: boolean
  statusName: string
  memberName: string
  mine: boolean
  progress: number
  harvestedJin: number
  promisedJin: number | null
  leftDays: number
}
export interface SupplyTask {
  id: number
  no: string
  assetCode: string
  memberName: string
  supplyName: string
  cat: string
  batchNo: string
  kind: string
  careModeName: string
  hint: string
  done: boolean
  usedAt: number | null
  dosage: string | null
}
export interface StewardAlarm {
  id: number
  type: string
  level: '高' | '中'
  assetCode: string
  title: string
  content: string
  qty: number | null
  photos: string[]
  insuranceReported: boolean
  createTime: number
}
export interface Conversation {
  memberId: number
  memberName: string
  assetCode: string
  last: string
  lastTime: number | null
  unread: number
}
export interface ChatMessage {
  id: number
  from: 'steward' | 'member'
  content: string
  createTime: number
}
export interface Performance {
  stewardName: string
  baseName: string
  month: string
  assetCount: number
  memberCount: number
  monthCheckins: number
  completionRate: number | null
  passRate: number | null
  outputRate: number | null
  responseMinutes: number | null
  satisfaction: number | null
  score: number | null
}

/** 配送确认：我负责的待发货收成 + 配送单 */
export function getStewardShipments(status?: number) {
  return http.get<StewardShipmentList>(`${baseUrl}/shipment/list`, status === undefined ? {} : { status })
}
export function createStewardShipment(data: { harvestId: number, carrier: string, logisticsNo: string, coldChain?: boolean }) {
  return http.post<number>(`${baseUrl}/shipment/create`, data as any)
}
/** 核销/签收：id 或扫码得到的配送单号 no */
export function signStewardShipment(data: { id?: number, no?: string }) {
  return http.put<boolean>(`${baseUrl}/shipment/sign`, data as any)
}
export function getSelfGuide(state: string) {
  return http.get<SelfGuide>(`${baseUrl}/self-guide/list`, { state })
}
export function remindSelfTask(taskId: number, entrust = false) {
  return http.post<boolean>(`${baseUrl}/self-guide/remind`, { taskId, entrust } as any)
}
export function getStewardRecords(status?: number) {
  return http.get<StewardRecordList>(`${baseUrl}/record/list`, status === undefined ? {} : { status })
}
export function getStewardAssets(scope: 'all' | 'raising' | 'free') {
  return http.get<{ counts: { all: number, raising: number, free: number }, list: StewardAssetItem[] }>(`${baseUrl}/asset/list`, { scope })
}
export function getSupplyTasks(tab: 'todo' | 'done') {
  return http.get<{ counts: { todo: number, done: number }, list: SupplyTask[] }>(`${baseUrl}/supply-task/list`, { tab })
}
export function applySupplyTask(data: { id: number, dosage: string, usedAt?: number }) {
  return http.put<boolean>(`${baseUrl}/supply-task/apply`, data as any)
}
export function getStewardAlarms() {
  return http.get<StewardAlarm[]>(`${baseUrl}/alarm/list`)
}
export function createStewardAlarm(data: { type: string, assetId?: number, content: string, qty?: number, photos?: string[] }) {
  return http.post<number>(`${baseUrl}/alarm/create`, data as any)
}
export function getConversations() {
  return http.get<{ list: Conversation[], notices: number }>(`${baseUrl}/message/conversations`)
}
export function getChat(memberId: number) {
  return http.get<{ member: { id: number, name: string, mobile: string, assetCode: string }, list: ChatMessage[] }>(`${baseUrl}/message/list`, { memberId })
}
export function sendChat(memberId: number, content: string) {
  return http.post<boolean>(`${baseUrl}/message/send`, { memberId, content } as any)
}
export function getPerformance() {
  return http.get<Performance>(`${baseUrl}/performance`)
}
