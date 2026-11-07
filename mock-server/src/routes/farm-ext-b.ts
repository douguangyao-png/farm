import { Router } from 'express'
import { ok, fail, requireLogin } from '../http.js'
import { page, q, like, eqNum, tenantOf, BIZ, badRequest, notFound } from '../farm/common.js'
import { findBannedWords, bannedMessage, SUGGESTION } from '../compliance.js'
import {
  ext, certRecords, flowRows, flowStats, splitView, analysisView, devicesOf, deviceStats, cameraView, hostingConfigView, FUND_FIELDS, FEE_RATE
} from '../farm/ext-b.js'
import { getDb } from '../store.js'

/**
 * 第二期 B 的后台接口（/admin-api/farm/**）：交易流水、分账结算、经营分析、资质证照、资金公示、
 * IoT 设备、视频监控、托管模式配置、消息模板。契约见 docs/plans/第二期-登记/B.md。
 * 托管模式的保存不在这里：页面直接调已有的 PUT /farm/product/update（同一份 careModes，违禁词校验也在那里）。
 */
export const farmExtBRouter = Router()
farmExtBRouter.use(requireLogin('admin'))

const DAY = 86400_000

// ---------- 交易流水 ----------
farmExtBRouter.get('/finance/flow/page', (req, res) => {
  const t = tenantOf(req)
  const all = flowRows(t)
  const begin = q(req, 'beginTime') ? Number(q(req, 'beginTime')) : undefined
  const end = q(req, 'endTime') ? Number(q(req, 'endTime')) : undefined
  const rows = all.filter(
    (r) =>
      like(r.tradeNo, q(req, 'tradeNo')) &&
      like(r.memberName, q(req, 'memberName')) &&
      (q(req, 'type') === undefined || r.type === q(req, 'type')) &&
      (q(req, 'settleStatus') === undefined || r.settleStatus === q(req, 'settleStatus')) &&
      (begin === undefined || r.time >= begin) &&
      (end === undefined || r.time <= end)
  )
  // 统计看全部流水，不受筛选影响
  return ok(res, { ...page(req, rows), stats: flowStats(all) })
})

// ---------- 分账结算 ----------
farmExtBRouter.get('/finance/split/get', (req, res) => ok(res, { ...splitView(tenantOf(req)), feeRate: FEE_RATE * 100 }))

// ---------- 经营分析 ----------
farmExtBRouter.get('/analysis/summary', (req, res) => {
  const days = [7, 30, 90].includes(Number(req.query.days)) ? Number(req.query.days) : 30
  return ok(res, analysisView(tenantOf(req), days))
})

// ---------- 资质证照 ----------
function certView(c: ReturnType<typeof ext>['certs'][number]) {
  const now = Date.now()
  const expired = c.validUntil !== null && c.validUntil < now
  const daysLeft = c.validUntil === null ? null : Math.ceil((c.validUntil - now) / DAY)
  const verify = expired ? 'expired' : c.verify
  const names: Record<string, string> = { verified: '已核验', pending: '待核验', renew: '待更新', expired: '已过期' }
  return {
    ...c,
    verify,
    verifyName: names[verify],
    daysLeft,
    expiringSoon: !expired && daysLeft !== null && daysLeft <= 30,
    publicName: c.publicMode === 'public' ? '公示' : '不公示（可预约查阅）',
    editable: c.source === 'upload'
  }
}

farmExtBRouter.get('/cert/page', (req, res) => {
  const t = tenantOf(req)
  const e = ext(t)
  const all = e.certs.map(certView)
  const rows = all.filter((c) => like(c.name, q(req, 'name')) && (q(req, 'type') === undefined || c.type === q(req, 'type')))
  const required = ['营业执照', '土地流转合同', '政策性农业保险保单']
  const stats = {
    total: all.length,
    expiringSoon: all.filter((c) => c.expiringSoon).length,
    expired: all.filter((c) => c.verify === 'expired').length,
    missingRequired: required.filter((n) => !all.some((c) => c.name === n)).length
  }
  return ok(res, { ...page(req, rows), stats: { ...stats, records: certRecords(t).sort((a, b) => b.time - a.time) } })
})
farmExtBRouter.post('/cert/create', (req, res) => {
  const e = ext(tenantOf(req))
  const b = req.body ?? {}
  if (!String(b.name ?? '').trim()) return badRequest(res, '请填写证照名称')
  if (!String(b.no ?? '').trim()) return badRequest(res, '请填写证照编号')
  if (!['必备', '建议', '按需'].includes(b.type)) return badRequest(res, '请选择证照类型')
  const validUntil = b.validUntil === null || b.validUntil === undefined || b.validUntil === '' ? null : Number(b.validUntil)
  if (validUntil !== null && !Number.isFinite(validUntil)) return badRequest(res, '有效期格式不对')
  const id = e.certs.reduce((m, c) => Math.max(m, c.id < 9000 ? c.id : 0), 0) + 1
  const now = Date.now()
  e.certs.push({ id, name: String(b.name).trim(), no: String(b.no).trim(), type: b.type, validUntil, verify: 'pending', publicMode: b.publicMode === 'private' ? 'private' : 'public', fileUrl: b.fileUrl ?? '', source: 'upload', demo: false, createTime: now })
  return ok(res, id)
})
farmExtBRouter.put('/cert/update-public', (req, res) => {
  const e = ext(tenantOf(req))
  const c = e.certs.find((x) => x.id === Number(req.body?.id))
  if (!c) return notFound(res, '证照')
  if (c.source === 'base') return badRequest(res, '基地备案证来自基地资料，请到基地管理修改')
  c.publicMode = req.body?.publicMode === 'private' ? 'private' : 'public'
  return ok(res, true)
})

// ---------- 资金公示 ----------
farmExtBRouter.get('/fund-notice/get', (req, res) => {
  const t = tenantOf(req)
  const e = ext(t)
  const d = getDb()
  const tenant = d.tenants.find((x) => x.id === t)
  const flows = flowRows(t)
  const now = Date.now()
  const m30 = flows.filter((r) => r.time >= now - 30 * DAY)
  const total = m30.reduce((s, r) => s + r.amount, 0)
  const fee = m30.reduce((s, r) => s + r.fee, 0)
  // 资金用途占比：系统没有成本台账，按原型占比演示；平台服务费用真实 3%，经营毛利取剩余
  const usage = [
    { name: '种苗 / 农资投入', percent: 13.1 },
    { name: '人工与管家成本', percent: 31.3 },
    { name: '物流配送', percent: 9.7 },
    { name: '保险保费', percent: 2.7 },
    { name: '基地维护与折旧', percent: 18.2 }
  ].map((u) => ({ ...u, amount: Math.round((total * u.percent) / 100), demo: true }))
  const used = usage.reduce((s, u) => s + u.amount, 0)
  const feePercent = FEE_RATE * 100
  const rest = total - used - fee
  const usageAll = [
    ...usage,
    { name: '平台技术服务费', percent: feePercent, amount: fee, demo: false },
    { name: '经营毛利', percent: Math.round((100 - usage.reduce((s, u) => s + u.percent, 0) - feePercent) * 10) / 10, amount: rest, demo: true }
  ]
  const policy = e.certs.find((c) => c.name.includes('保单'))
  const activeMembers = new Set(d.adoptions.filter((a) => a.tenantId === t).map((a) => a.memberId)).size
  return ok(res, {
    demo: true,
    fields: FUND_FIELDS.map((f) => ({ ...f, enabled: !!e.fund[f.key] })),
    preview: {
      steps: ['消费者支付', '第三方支付通道', '经营者对公账户', '按生产进度结算'],
      payeeName: tenant?.name ?? '',
      channelName: '微信支付',
      feeRate: feePercent,
      policyNo: policy?.no ?? '',
      monthlyTotal: total,
      memberTotal: activeMembers
    },
    usage: usageAll,
    usageTotal: total,
    updateTime: e.fundUpdateTime
  })
})
farmExtBRouter.put('/fund-notice/save', (req, res) => {
  const e = ext(tenantOf(req))
  const f = req.body?.fields
  if (!f || typeof f !== 'object') return badRequest(res, '缺少公示字段')
  for (const x of FUND_FIELDS) if (typeof f[x.key] === 'boolean') e.fund[x.key] = f[x.key]
  e.fundUpdateTime = Date.now()
  return ok(res, true)
})

// ---------- IoT 设备 ----------
farmExtBRouter.get('/device/page', (req, res) => {
  const all = devicesOf(tenantOf(req))
  const rows = all.filter((r) => like(r.code, q(req, 'code')) && (q(req, 'kind') === undefined || r.kind === q(req, 'kind')) && (q(req, 'status') === undefined || r.status === q(req, 'status')))
  return ok(res, { ...page(req, rows), stats: deviceStats(all) })
})
farmExtBRouter.post('/device/create', (req, res) => {
  const t = tenantOf(req)
  const e = ext(t)
  const b = req.body ?? {}
  if (!String(b.name ?? '').trim()) return badRequest(res, '请填写设备名称')
  const kind = b.kind === 'sensor' ? 'sensor' : 'camera'
  const typeName = kind === 'camera' ? '摄像头' : ['土壤墒情站', '气象站', 'RFID 耳标/脚环'].includes(b.typeName) ? b.typeName : '土壤墒情站'
  const code = `${kind === 'camera' ? 'CAM' : 'SEN'}-X${String(e.customDevices.length + 1).padStart(2, '0')}`
  e.customDevices.push({ code, kind, typeName, name: String(b.name).trim(), position: String(b.position ?? b.name).trim(), bindText: String(b.bindText ?? '').trim() || '未绑定', firmware: 'v1.0.0' })
  return ok(res, code)
})
farmExtBRouter.put('/device/update', (req, res) => {
  const t = tenantOf(req)
  const code = String(req.body?.code ?? '')
  if (!devicesOf(t).some((d) => d.code === code)) return notFound(res, '设备')
  const o = (ext(t).deviceOverride[code] ??= {})
  if (typeof req.body.position === 'string' && req.body.position.trim()) o.position = req.body.position.trim()
  if (typeof req.body.firmware === 'string' && req.body.firmware.trim()) o.firmware = req.body.firmware.trim()
  return ok(res, true)
})
farmExtBRouter.put('/device/dispatch', (req, res) => {
  const t = tenantOf(req)
  const code = String(req.body?.code ?? '')
  const d = devicesOf(t).find((x) => x.code === code)
  if (!d) return notFound(res, '设备')
  if (d.status !== 'offline') return badRequest(res, '只有离线设备可以派单')
  ;(ext(t).deviceOverride[code] ??= {}).dispatched = true
  return ok(res, true)
})

// ---------- 视频监控 ----------
farmExtBRouter.get('/camera/overview', (req, res) => ok(res, cameraView(tenantOf(req))))

// ---------- 托管模式配置（只读；保存走 PUT /farm/product/update） ----------
farmExtBRouter.get('/hosting-config/get', (req, res) => {
  const id = q(req, 'productId')
  const v = hostingConfigView(tenantOf(req), id ? Number(id) : undefined)
  return v ? ok(res, v) : notFound(res, '认养商品')
})

// ---------- 消息模板 ----------
const CHANNELS = ['订阅消息', '短信', 'App 推送']
function checkTemplate(b: Record<string, unknown>) {
  if (!String(b.name ?? '').trim()) return '请填写模板名称'
  if (!String(b.trigger ?? '').trim()) return '请填写触发时机'
  if (!String(b.content ?? '').trim()) return '请填写模板内容'
  const ch = b.channels
  if (!Array.isArray(ch) || !ch.length || ch.some((c) => !CHANNELS.includes(String(c)))) return '请选择发送渠道'
  return null
}
farmExtBRouter.get('/msg-template/page', (req, res) => {
  const e = ext(tenantOf(req))
  const rows = e.templates.filter((x) => like(x.name, q(req, 'name')) && eqNum(x.status, q(req, 'status'))).sort((a, b) => a.id - b.id)
  return ok(res, page(req, rows))
})
farmExtBRouter.post('/msg-template/create', (req, res) => {
  const e = ext(tenantOf(req))
  const b = req.body ?? {}
  const err = checkTemplate(b)
  if (err) return badRequest(res, err)
  const words = findBannedWords(b.name, b.content)
  if (words.length) return fail(res, BIZ.BANNED_WORDS, bannedMessage(words), { words, suggestion: SUGGESTION })
  const now = Date.now()
  const id = e.templates.reduce((m, x) => Math.max(m, x.id), 0) + 1
  e.templates.push({ id, name: String(b.name).trim(), trigger: String(b.trigger).trim(), channels: b.channels, content: String(b.content).trim(), status: 0, createTime: now, updateTime: now })
  return ok(res, id)
})
farmExtBRouter.put('/msg-template/update', (req, res) => {
  const e = ext(tenantOf(req))
  const x = e.templates.find((v) => v.id === Number(req.body?.id))
  if (!x) return notFound(res, '消息模板')
  const b = { ...x, ...req.body }
  const err = checkTemplate(b)
  if (err) return badRequest(res, err)
  const words = findBannedWords(b.name, b.content)
  if (words.length) return fail(res, BIZ.BANNED_WORDS, bannedMessage(words), { words, suggestion: SUGGESTION })
  x.name = String(b.name).trim()
  x.trigger = String(b.trigger).trim()
  x.channels = b.channels
  x.content = String(b.content).trim()
  x.updateTime = Date.now()
  return ok(res, true)
})
farmExtBRouter.put('/msg-template/update-status', (req, res) => {
  const e = ext(tenantOf(req))
  const x = e.templates.find((v) => v.id === Number(req.body?.id))
  if (!x) return notFound(res, '消息模板')
  x.status = Number(req.body.status) === 1 ? 1 : 0
  x.updateTime = Date.now()
  return ok(res, true)
})
/** 测试发送：演示环境不真实下发，只返回把示例变量代入后的预览 */
farmExtBRouter.post('/msg-template/test-send', (req, res) => {
  const e = ext(tenantOf(req))
  const x = e.templates.find((v) => v.id === Number(req.body?.id))
  if (!x) return notFound(res, '消息模板')
  if (x.status !== 0) return badRequest(res, '模板已停用，请先启用')
  const preview = x.content.replace(/\{[^}]+\}/g, '示例')
  return ok(res, { sent: false, demo: true, channels: x.channels, preview })
})
