import { getDb, type Db, type Asset } from '../store.js'
import { CARE_MODE_NAME } from './common.js'
import { CATEGORY_NAME } from './views.js'

/**
 * 第二期 B：交易流水、分账结算、经营分析、资质证照、资金公示、IoT 设备、视频监控、托管模式配置、消息模板。
 * 能由订单/资产/基地/农资派生的全部派生；派生不出来的用确定性的演示数据，并在返回里标 demo。
 * 新增的少量可写状态（证照、公示开关、消息模板、设备台账）放内存，挂在当前 Db 对象上，/__mock/reset 换新 Db 后自动清空。
 */
const db = () => getDb()
const DAY = 86400_000
const pad = (n: number, w = 2) => String(n).padStart(w, '0')
const r1 = (n: number) => Math.round(n * 10) / 10
const startOfDay = (t = Date.now()) => new Date(new Date(t).toDateString()).getTime()
const ymd = (t: number) => {
  const d = new Date(t)
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
}

/** 平台服务费率 3%（原型口径，演示） */
export const FEE_RATE = 0.03
/** 结算周期 T+1：支付满 24 小时视为已结算 */
const SETTLE_MS = DAY

// ======================= 交易流水（订单派生） =======================
export type FlowType = 'adopt' | 'supply' | 'service'
export const FLOW_TYPE_NAME: Record<FlowType, string> = { adopt: '认养支付', supply: '农资购买', service: '代管服务' }

export interface FlowRow {
  key: string
  tradeNo: string
  memberId: number
  memberName: string
  type: FlowType
  typeName: string
  /** 交易金额，分 */
  amount: number
  fee: number
  net: number
  settleStatus: 'settled' | 'settling'
  settleStatusName: string
  time: number
  refNo: string
}

export function flowRows(t: number): FlowRow[] {
  const d = db()
  const nick = (id: number) => d.members.find((m) => m.id === id)?.nickname ?? ''
  const raw: { key: string; memberId: number; type: FlowType; amount: number; time: number; refNo: string }[] = []
  for (const o of d.orders) if (o.tenantId === t && o.payTime && o.status !== 30) raw.push({ key: `o${o.id}`, memberId: o.memberId, type: 'adopt', amount: o.amount, time: o.payTime, refNo: o.no })
  for (const s of d.supplyOrders) if (s.tenantId === t) raw.push({ key: `s${s.id}`, memberId: s.memberId, type: 'supply', amount: s.amount, time: s.createTime, refNo: s.no })
  for (const s of d.serviceOrders) if (s.tenantId === t && s.status === 20 && s.price > 0) raw.push({ key: `w${s.id}`, memberId: s.memberId, type: 'service', amount: s.price, time: s.finishTime ?? s.createTime, refNo: s.no })
  raw.sort((a, b) => a.time - b.time || a.key.localeCompare(b.key))
  const seq = new Map<string, number>()
  const now = Date.now()
  const rows = raw.map((r): FlowRow => {
    const day = ymd(r.time)
    const n = (seq.get(day) ?? 0) + 1
    seq.set(day, n)
    const fee = Math.round(r.amount * FEE_RATE)
    const settled = now - r.time >= SETTLE_MS
    return {
      key: r.key,
      tradeNo: `T${day}${pad(n, 3)}`,
      memberId: r.memberId,
      memberName: nick(r.memberId),
      type: r.type,
      typeName: FLOW_TYPE_NAME[r.type],
      amount: r.amount,
      fee,
      net: r.amount - fee,
      settleStatus: settled ? 'settled' : 'settling',
      settleStatusName: settled ? '已结算' : '结算中',
      time: r.time,
      refNo: r.refNo
    }
  })
  return rows.sort((a, b) => b.time - a.time)
}

export function flowStats(rows: FlowRow[]) {
  const now = Date.now()
  const d0 = startOfDay(now)
  const w1 = now - 30 * DAY
  const w0 = now - 60 * DAY
  const today = rows.filter((r) => r.time >= d0)
  const cur = rows.filter((r) => r.time >= w1)
  const prev = rows.filter((r) => r.time >= w0 && r.time < w1)
  const sum = (a: FlowRow[], k: 'amount' | 'fee' | 'net') => a.reduce((s, r) => s + r[k], 0)
  const prevAmt = sum(prev, 'amount')
  return {
    todayAmount: sum(today, 'amount'),
    todayCount: today.length,
    // 原型是「本月」，演示库的数据集中在过去几周，改用滚动 30 天，避免月初全是 0
    last30Amount: sum(cur, 'amount'),
    last30Count: cur.length,
    last30Growth: prevAmt > 0 ? r1(((sum(cur, 'amount') - prevAmt) / prevAmt) * 100) : null,
    last30Fee: sum(cur, 'fee'),
    feeRate: FEE_RATE * 100,
    pendingAmount: sum(rows.filter((r) => r.settleStatus === 'settling'), 'net'),
    pendingCount: rows.filter((r) => r.settleStatus === 'settling').length
  }
}

// ======================= 分账结算 =======================
/** 默认分账规则（原型 finSplit，演示）：经营主体 88 / 平台 3 / 土地流转方 6 / 渠道 3 */
const SPLIT_RULES = [
  { key: 'operator', name: '', type: '经营主体', percent: 88 },
  { key: 'platform', name: '平台技术服务费', type: '平台', percent: 3 },
  { key: 'land', name: '土地流转方（村集体）', type: '合作方', percent: 6 },
  { key: 'channel', name: '渠道推广（小程序）', type: '渠道', percent: 3 }
]

function splitAmount(total: number) {
  const parts = SPLIT_RULES.map((r) => Math.round((total * r.percent) / 100))
  parts[0] += total - parts.reduce((a, b) => a + b, 0) // 尾差给经营主体，合计恒等于总额
  return parts
}

export function splitView(t: number) {
  const tenant = db().tenants.find((x) => x.id === t)
  const tenantName = tenant?.name ?? ''
  const products = db().products.filter((p) => p.tenantId === t).sort((a, b) => a.id - b.id)
  const sample = products[0]
  const exampleAmount = sample?.basePrice ?? 128000
  const exParts = splitAmount(exampleAmount)
  const rules = SPLIT_RULES.map((r, i) => ({ ...r, name: i === 0 ? tenantName : r.name, exampleAmount: exParts[i] }))
  const rows = flowRows(t)
  const now = Date.now()
  const total = rows.filter((r) => r.time >= now - 30 * DAY).reduce((s, r) => s + r.amount, 0)
  const mParts = splitAmount(total)
  const monthItems = SPLIT_RULES.map((r, i) => ({ key: r.key, label: i === 0 ? '经营者到账' : r.key === 'platform' ? '平台服务费' : r.key === 'land' ? '土地流转分成' : '渠道分成', amount: mParts[i] }))
  // 代管工单的分账比例（管家/农场/平台）是真实数据：取近 30 天已完成工单
  const svc = db().serviceOrders.filter((s) => s.tenantId === t && s.status === 20 && (s.finishTime ?? s.createTime) >= now - 30 * DAY)
  const svcSum = (k: 'steward' | 'farm' | 'platform') => svc.reduce((s, x) => s + Math.round((x.price * x.split[k]) / 100), 0)
  // 发票：演示。取金额最高的 3 笔已支付认养订单，抬头用认养人昵称，状态按序固定
  const paid = db().orders.filter((o) => o.tenantId === t && o.payTime && o.status !== 30).sort((a, b) => b.amount - a.amount).slice(0, 3)
  const invoices = paid.map((o, i) => ({
    id: o.id,
    title: `个人（${db().members.find((m) => m.id === o.memberId)?.nickname ?? ''}）`,
    amount: o.amount,
    kind: '增值税普票',
    statusName: i === 2 ? '申请中' : '已开具',
    orderNo: o.no
  }))
  return {
    demo: true,
    sampleProductTitle: sample?.title ?? '',
    exampleAmount,
    rules,
    // 结算账户：演示信息，真实对公账户接入已排除
    account: { type: '对公银行账户', name: tenantName, bank: '（演示）中国农业银行 湖州分行', cycle: 'T+1（节假日顺延）', invoiceBy: '本平台代开 · 可下载电子发票', verified: true },
    month: { label: '近 30 天', total, items: monthItems },
    serviceSplit: { count: svc.length, total: svc.reduce((s, x) => s + x.price, 0), steward: svcSum('steward'), farm: svcSum('farm'), platform: svcSum('platform') },
    invoices
  }
}

// ======================= 经营分析 =======================
export function analysisView(t: number, days: number) {
  const d = db()
  const now = Date.now()
  const from = now - days * DAY
  const orders = d.orders.filter((o) => o.tenantId === t)
  const inWin = orders.filter((o) => o.createTime >= from)
  const paid = inWin.filter((o) => o.payTime && o.status !== 30)
  const signed = paid.filter((o) => o.contractAgreed)
  const placed = inWin.length
  // 曝光、浏览没有埋点：按原型转化率（浏览→下单 24.6%，曝光→浏览 26.3%）由下单数反推，标演示
  const browse = placed ? Math.round(placed / 0.246) : 0
  const exposure = browse ? Math.round(browse / 0.263) : 0
  const renew = Math.round(signed.length * 0.628)
  const stages = [
    { name: '曝光次数', value: exposure, demo: true },
    { name: '商品浏览', value: browse, demo: true },
    { name: '进入下单', value: placed, demo: false },
    { name: '完成支付', value: paid.length, demo: false },
    { name: '签署合同', value: signed.length, demo: false },
    { name: '首季续费', value: renew, demo: true }
  ].map((s, i, a) => ({ ...s, rate: i === 0 ? 100 : a[i - 1].value ? r1((s.value / a[i - 1].value) * 100) : 0 }))

  // 渠道来源：系统没有渠道字段，按固定占比拆分已支付订单数，标演示（最大余数法保证合计等于订单数）
  const chShare: [string, number][] = [['小程序自然流量', 34], ['短视频/小红书', 24], ['老客推荐', 18], ['到园转化', 11], ['异业合作', 7], ['朋友圈海报', 6]]
  const raw = chShare.map(([name, p]) => ({ name, exact: (paid.length * p) / 100 }))
  const channels = raw.map((r) => ({ name: r.name, value: Math.floor(r.exact), frac: r.exact - Math.floor(r.exact) }))
  let left = paid.length - channels.reduce((s, c) => s + c.value, 0)
  for (const c of [...channels].sort((a, b) => b.frac - a.frac)) {
    if (left <= 0) break
    c.value++
    left--
  }

  // 品类结构：真实 GMV（认养订单按商品资产类型，农资订单、代管工单单列）
  const cat = new Map<string, number>()
  for (const o of paid) {
    const type = d.products.find((p) => p.id === o.productId)?.assetType ?? ''
    const name = CATEGORY_NAME[type] ?? '其他'
    cat.set(name, (cat.get(name) ?? 0) + o.amount)
  }
  const supplyGmv = d.supplyOrders.filter((s) => s.tenantId === t && s.createTime >= from).reduce((s, x) => s + x.amount, 0)
  const svcGmv = d.serviceOrders.filter((s) => s.tenantId === t && s.status === 20 && (s.finishTime ?? s.createTime) >= from).reduce((s, x) => s + x.price, 0)
  if (supplyGmv) cat.set('农资商品', supplyGmv)
  if (svcGmv) cat.set('代管服务', svcGmv)
  const categories = [...cat.entries()].map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)

  // 复购与留存
  const allPaid = orders.filter((o) => o.payTime && o.status !== 30)
  const perMember = new Map<number, number>()
  for (const o of allPaid) perMember.set(o.memberId, (perMember.get(o.memberId) ?? 0) + 1)
  const repeat = [...perMember.values()].filter((n) => n >= 2).length
  const cancelledPaid = orders.filter((o) => o.status === 30 && o.payTime).length
  const ads = d.adoptions.filter((a) => a.tenantId === t)
  const avgYears = ads.length ? r1(ads.reduce((s, a) => s + (a.endAt - a.startAt), 0) / ads.length / (365 * DAY)) : 0
  const retention = [
    { label: '累计复购率', value: perMember.size ? r1((repeat / perMember.size) * 100) : 0, unit: '%', demo: false, hint: '已支付 2 单及以上的认养人 / 已支付认养人' },
    { label: '年度续费率', value: 62.8, unit: '%', demo: true, hint: '系统尚无续费数据' },
    { label: '平均认养周期', value: avgYears, unit: '年', demo: false, hint: '认养起止时间平均' },
    { label: '退费率', value: allPaid.length + cancelledPaid ? r1((cancelledPaid / (allPaid.length + cancelledPaid)) * 100) : 0, unit: '%', demo: false, hint: '已支付后取消的订单占比；退款闭环上线后以退款单为准' },
    { label: '投诉率', value: 0.4, unit: '%', demo: true, hint: '系统尚无投诉分类' }
  ]

  // 履约质量
  const active = ads.filter((a) => a.status === 10)
  const withRecord = active.filter((a) => d.records.some((r) => r.adoptionId === a.id && r.status === 10)).length
  const full = ads.filter((a) => a.promisedJin)
  const promised = full.reduce((s, a) => s + (a.promisedJin ?? 0), 0)
  const harvested = full.reduce((s, a) => s + Math.min(a.harvestedJin, a.promisedJin ?? 0), 0)
  const cams = devicesOf(t).filter((x) => x.kind === 'camera')
  const quality = [
    { label: '按时交付率', value: 98.6, unit: '%', demo: true, hint: '系统尚无承诺送达时间' },
    { label: '农事记录覆盖率', value: active.length ? r1((withRecord / active.length) * 100) : 0, unit: '%', demo: false, hint: '在养认养中至少有 1 条已发布记录的占比' },
    { label: '监控在线率', value: cams.length ? r1((cams.filter((x) => x.status === 'online').length / cams.length) * 100) : 0, unit: '%', demo: true, hint: '设备状态为演示数据' },
    { label: '约定交付完成率', value: promised ? r1((harvested / promised) * 100) : 0, unit: '%', demo: false, hint: '已收斤数 / 约定交付斤数（全托管）' },
    { label: '客服平均响应', value: 8, unit: '分钟', demo: true, hint: '系统尚无客服数据' }
  ]
  return { days, funnel: stages, channels: channels.map(({ name, value }) => ({ name, value, demo: true })), categories, retention, quality, paidCount: paid.length }
}

// ======================= IoT 设备 / 视频监控 =======================
export interface DeviceRow {
  code: string
  kind: 'camera' | 'sensor'
  typeName: string
  name: string
  position: string
  bindText: string
  assetCodes: string[]
  status: 'online' | 'offline'
  statusName: string
  lastReportTime: number
  firmware: string
  dispatched: boolean
  custom: boolean
}

interface CustomDevice { code: string; kind: 'camera' | 'sensor'; typeName: string; name: string; position: string; bindText: string; firmware: string }
interface Cert { id: number; name: string; no: string; type: '必备' | '建议' | '按需'; validUntil: number | null; verify: 'verified' | 'pending' | 'renew'; publicMode: 'public' | 'private'; fileUrl: string; source: 'upload' | 'base'; demo: boolean; createTime: number }
interface CertRecord { text: string; done: boolean; by: string; time: number }
export interface MsgTemplate { id: number; name: string; trigger: string; channels: string[]; content: string; status: number; createTime: number; updateTime: number }

interface TenantExt {
  customDevices: CustomDevice[]
  /** 设备编号 -> 覆盖项（配置修改位置/固件、派单标记） */
  deviceOverride: Record<string, { position?: string; firmware?: string; dispatched?: boolean }>
  certs: Cert[]
  fund: Record<string, boolean>
  fundUpdateTime: number | null
  templates: MsgTemplate[]
}

const states = new WeakMap<Db, Map<number, TenantExt>>()

export const FUND_FIELDS: { key: string; label: string; recommend: boolean }[] = [
  { key: 'flowChart', label: '资金流向图', recommend: true },
  { key: 'payeeName', label: '收款主体名称', recommend: true },
  { key: 'channelName', label: '支付通道名称', recommend: true },
  { key: 'feeRate', label: '平台服务费率', recommend: true },
  { key: 'policyNo', label: '保单编号', recommend: true },
  { key: 'monthlyTotal', label: '月度认养总额', recommend: false },
  { key: 'memberTotal', label: '认养人总数', recommend: false }
]

function seedTemplates(now: number): MsgTemplate[] {
  const rows: [string, string, string[], string][] = [
    ['支付成功通知', '订单支付完成', ['订阅消息'], '您的认养已生效，合同编号 HT-XXXXXXXX，专属管家将尽快与您联系。'],
    ['农事动态提醒', '管家发布记录', ['订阅消息'], '您家 {地块编号} 有新动态：{记录标题}，点击查看现场照片。'],
    ['配送发货通知', '批次发货', ['订阅消息', '短信'], '您的第 {n} 次配送已发出，{承运商}，物流单号 {单号}。'],
    ['到期续费提醒', '到期前 30/15/3 天', ['订阅消息'], '您的认养将于 {到期日} 到期，续养可享老客专属价格。'],
    ['异常预警', '设备离线 / 画面静止', ['App 推送'], '{设备编号} 已离线 30 分钟，请及时处理。'],
    ['退款进度', '退款状态变更', ['订阅消息'], '您的退款申请已通过，款项将在 3 个工作日内原路退回。']
  ]
  return rows.map(([name, trigger, channels, content], i) => ({ id: i + 1, name, trigger, channels, content, status: 0, createTime: now - 30 * DAY, updateTime: now - 30 * DAY }))
}

function seedCerts(t: number, now: number): Cert[] {
  const day = startOfDay(now)
  const at = (s: string) => new Date(s + 'T00:00:00+08:00').getTime()
  const demo = (id: number, name: string, no: string, type: Cert['type'], validUntil: number | null, verify: Cert['verify'], publicMode: Cert['publicMode']): Cert =>
    ({ id, name, no, type, validUntil, verify, publicMode, fileUrl: '', source: 'upload', demo: true, createTime: now - 200 * DAY })
  const certs: Cert[] = [
    demo(1, '营业执照', '9233****A', '必备', null, 'verified', 'public'),
    demo(2, '土地流转合同', 'LH-2021-0088', '必备', at('2029-03-01'), 'verified', 'private'),
    demo(3, '农产品质量检测报告', 'CNAS-2026-0918', '建议', day + 25 * DAY, 'verified', 'public'),
    demo(4, '政策性农业保险保单', 'PICC-2026-0188', '必备', at('2027-03-15'), 'verified', 'public'),
    demo(5, '动物防疫条件合格证', 'FZ-2024-0116', '必备', at('2027-06-30'), 'verified', 'public'),
    demo(6, '食品经营许可证', 'JY-2023-0421', '按需', at('2028-05-20'), 'renew', 'public')
  ]
  // 基地资料里的备案编号是真实数据：每个基地一行，只读
  for (const b of db().bases.filter((x) => x.tenantId === t && x.certNo))
    certs.push({ id: 9000 + b.id, name: `基地备案证（${b.name}）`, no: b.certNo, type: '必备', validUntil: null, verify: 'verified', publicMode: 'public', fileUrl: '', source: 'base', demo: false, createTime: b.createTime })
  return certs
}

/** 核验记录：3 条演示 + 每份经营者上传的证照一条（Java 侧同样派生，不单独建表） */
export function certRecords(t: number): CertRecord[] {
  const now = Date.now()
  const at = (s: string) => new Date(s + 'T00:00:00+08:00').getTime()
  const base: CertRecord[] = [
    { text: '营业执照通过人工复核', done: true, by: '平台风控', time: at('2026-03-02') },
    { text: '保险保单与标的匹配成功', done: true, by: '系统', time: at('2026-03-05') },
    { text: '检测报告即将到期，已提醒上传新版', done: false, by: '系统', time: now }
  ]
  const mine = ext(t).certs.filter((c) => c.source === 'upload' && !c.demo).map((c): CertRecord => ({ text: `上传《${c.name}》，${c.verify === 'pending' ? '等待平台人工复核' : '已核验'}`, done: c.verify !== 'pending', by: '系统', time: c.createTime }))
  return [...base, ...mine]
}

export function ext(t: number): TenantExt {
  const d = db()
  let m = states.get(d)
  if (!m) states.set(d, (m = new Map()))
  let s = m.get(t)
  if (!s) {
    const now = Date.now()
    const certs = seedCerts(t, now)
    s = {
      customDevices: [],
      deviceOverride: {},
      certs,
      fund: Object.fromEntries(FUND_FIELDS.map((f) => [f.key, f.recommend])),
      fundUpdateTime: null,
      templates: seedTemplates(now)
    }
    m.set(t, s)
  }
  return s
}

const bindText = (codes: string[]) => (codes.length <= 3 ? codes.join('、') : `${codes[0]} ~ ${codes[codes.length - 1]}（${codes.length} 块）`)

/** 设备台账 = 资产上的摄像头/传感器名称（去重）+ 手动添加的设备。状态、最近上报、固件没有真实接入，用确定性演示值 */
export function devicesOf(t: number): DeviceRow[] {
  const assets = db().assets.filter((a) => a.tenantId === t).sort((a, b) => a.id - b.id)
  const e = ext(t)
  const now = Date.now()
  const groups = (pick: (a: Asset) => string | null) => {
    const m = new Map<string, string[]>()
    for (const a of assets) {
      const n = pick(a)
      if (n) m.set(n, [...(m.get(n) ?? []), a.code])
    }
    return [...m.entries()]
  }
  const cams = groups((a) => a.cameraName)
  const sens = groups((a) => a.sensorName)
  const rows: DeviceRow[] = []
  const push = (kind: 'camera' | 'sensor', typeName: string, code: string, name: string, codes: string[], firmware: string, offline: boolean, idx: number, custom?: CustomDevice) => {
    const ov = e.deviceOverride[code] ?? {}
    rows.push({
      code,
      kind,
      typeName,
      name,
      position: ov.position ?? custom?.position ?? name,
      bindText: custom?.bindText ?? bindText(codes),
      assetCodes: codes,
      status: offline ? 'offline' : 'online',
      statusName: offline ? '离线' : '在线',
      lastReportTime: now - (offline ? 2 * 3600_000 : ((idx * 3 + 2) % 9 + 1) * 60_000),
      firmware: ov.firmware ?? firmware,
      dispatched: !!ov.dispatched,
      custom: !!custom
    })
  }
  cams.forEach(([name, codes], i) => push('camera', '摄像头', `CAM-${pad(i + 1)}`, name, codes, 'v3.2.1', cams.length >= 3 && i === cams.length - 1, i))
  sens.forEach(([name, codes], i) => push('sensor', name.includes('气象') ? '气象站' : '土壤墒情站', `SEN-${pad(i + 1)}`, name, codes, name.includes('气象') ? 'v2.4.0' : 'v1.8.0', false, i + 5))
  e.customDevices.forEach((c, i) => push(c.kind, c.typeName, c.code, c.name, [], c.firmware, false, i + 9, c))
  return rows
}

export function deviceStats(rows: DeviceRow[]) {
  const cams = rows.filter((r) => r.kind === 'camera')
  const online = rows.filter((r) => r.status === 'online')
  return {
    total: rows.length,
    cameraCount: cams.length,
    sensorCount: rows.length - cams.length,
    onlineCount: online.length,
    offlineCount: rows.length - online.length,
    // 5 分钟上报一次 = 288 次/天/台，只算在线设备（演示）
    todayReports: online.length * 288,
    retainDays: 30,
    storageUsedGb: 42,
    storageTotalGb: 100
  }
}

export function cameraView(t: number) {
  const d = db()
  const rows = devicesOf(t).filter((r) => r.kind === 'camera')
  const cameras = rows.map((r, i) => {
    const assetIds = d.assets.filter((a) => a.tenantId === t && r.assetCodes.includes(a.code)).map((a) => a.id)
    const members = new Set(d.adoptions.filter((a) => a.tenantId === t && a.status === 10 && assetIds.includes(a.assetId)).map((a) => a.memberId))
    return { ...r, visibleMembers: r.status === 'offline' ? 0 : members.size, todayViews: r.status === 'offline' ? 0 : 12 + ((i * 37) % 41) }
  })
  const online = cameras.filter((c) => c.status === 'online')
  const views = cameras.reduce((s, c) => s + c.todayViews, 0)
  return {
    demo: true,
    stats: {
      cameraCount: cameras.length,
      onlineCount: online.length,
      offlineCount: cameras.length - online.length,
      todayViews: views,
      watchHours: r1((views * 3.1) / 60),
      retainDays: 30,
      alertCount: cameras.length - online.length
    },
    cameras,
    policy: [
      { k: '可见范围', v: '仅该标的认养人可见' },
      { k: '游客模式', v: '可开启 15 秒试看（用于拉新）' },
      { k: '水印', v: '画面叠加时间戳 + 地块编号（防录播造假）' },
      { k: '回放权限', v: '认养人可调阅近 30 天录像片段' },
      { k: '离线策略', v: '展示最后在线快照 + 在线状态提示' },
      { k: '画面静止检测', v: '已开启 · 6h 无变化触发复核' }
    ]
  }
}

// ======================= 托管模式配置 =======================
const MODES = ['full', 'semi', 'self'] as const
const CAT_ORDER = ['seed', 'fert', 'tool', 'bio', 'svc', 'ins']

export function hostingConfigView(t: number, productId?: number) {
  const d = db()
  const products = d.products.filter((p) => p.tenantId === t).sort((a, b) => a.id - b.id)
  const p = products.find((x) => x.id === productId) ?? products[0]
  if (!p) return null
  const fullPrice = p.careModes.find((m) => m.mode === 'full')?.price ?? p.basePrice
  const supplies = d.supplies.filter((s) => s.tenantId === t && s.status === 0 && s.retailAllowed !== false)
  const modes = MODES.map((mode) => {
    const c = p.careModes.find((m) => m.mode === mode)
    const farm = c ? c.duty.filter((x) => x.owner === 'farm').length : 0
    const member = c ? c.duty.length - farm : 0
    const pkg = supplies
      .filter((s) => s.fitModes.includes(mode))
      .sort((a, b) => CAT_ORDER.indexOf(a.cat) - CAT_ORDER.indexOf(b.cat) || a.id - b.id)
      .map((s, i) => ({ id: s.id, name: s.name, cat: s.cat, price: s.price, unit: s.unit, recommended: i === 0 }))
    return {
      mode,
      name: CARE_MODE_NAME[mode],
      configured: !!c,
      enabled: !!c?.enabled,
      price: c?.price ?? 0,
      priceDelta: c && mode !== 'full' ? c.price - fullPrice : null,
      slogan: c?.slogan ?? '',
      outputPromise: c?.outputPromise ?? '',
      contractVersion: c?.contractVersion ?? '',
      contractName: c ? `《认养服务合同（${CARE_MODE_NAME[mode]}）》${c.contractVersion}` : '',
      weeklyTodo: c?.weeklyTodo ?? 0,
      insurance: c?.insurance ?? '',
      duty: c?.duty ?? [],
      dutyFarmCount: farm,
      dutyMemberCount: member,
      supplyPackage: mode === 'full' ? [] : pkg
    }
  })
  const items: string[] = []
  for (const m of p.careModes) for (const x of m.duty) if (!items.includes(x.item)) items.push(x.item)
  const dutyMatrix = items.map((item) => ({ item, ...Object.fromEntries(MODES.map((mode) => [mode, p.careModes.find((m) => m.mode === mode)?.duty.find((x) => x.item === item)?.owner ?? null])) }))
  const now = Date.now()
  const overdue = d.serviceOrders.filter((s) => s.tenantId === t && s.fromTaskId && s.status === 20 && (s.finishTime ?? s.createTime) >= now - 30 * DAY)
  return {
    product: { id: p.id, title: p.title, basePrice: p.basePrice },
    products: products.map((x) => ({ id: x.id, title: x.title })),
    modes,
    dutyMatrix,
    switchRule: '每周期可切换 1 次；升级补差价，降级按剩余周期折算退差额（扣已发生投入）；地块编号、摄像头绑定、历史档案连续保留',
    trigger: {
      sources: ['作物生长日历', 'IoT 阈值', '管家手动'],
      example: '土壤墒情 < 22% 且 48h 无降雨 → 生成浇水任务',
      remind: '任务前 1 天 + 当天 9:00 双提醒',
      overdue: '逾期 48h 自动推送「委托管家代办」',
      overdueIncome: overdue.reduce((s, x) => s + x.price, 0),
      overdueCount: overdue.length
    },
    compliance: {
      pesticideBlocked: d.supplies.filter((s) => s.tenantId === t && (s.cat === 'pesticide' || s.retailAllowed === false)).length,
      rules: [
        { k: '农药零售', v: '化学农药不得对个人零售；生物防治产品仅可绑定「农场代施」工单', tag: '已拦截' },
        { k: '投入品登记', v: '认养人自购农资须登记批次与施用时间，统一进入溯源台账', tag: '强制' },
        { k: '产出表述', v: '文案禁止出现收益承诺类的金融词语，保存时自动拦截并给出改写建议', tag: '强制' }
      ]
    }
  }
}
