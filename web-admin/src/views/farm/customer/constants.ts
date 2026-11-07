/**
 * 客户管理页自己用的常量（客户分层页也引用这里的分层/等级配色）。
 * 权限串（第二期权限清单「A」小节）：
 *   farm:customer:query    查看客户名单与统计
 *   farm:customer:export   导出名单（演示，暂未实现）
 *   farm:customer:tag      批量打标签（演示，暂未实现）
 *   farm:customer:create   新增客户（演示，暂未实现）
 */

/** 分层 key -> 标签颜色 */
export const TIER_TAG_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  core: 'success',
  growth: 'warning',
  new: 'info',
  sleep: 'danger',
  other: 'info'
}

export const TIER_OPTIONS = [
  { value: 'core', label: '核心客户' },
  { value: 'growth', label: '成长客户' },
  { value: 'new', label: '新客' },
  { value: 'sleep', label: '沉睡客户' },
  { value: 'other', label: '其他' }
]

/** 消费区间（元，转分后传给接口 minAmount / maxAmount） */
export const SPEND_RANGES: { label: string; min?: number; max?: number }[] = [
  { label: '1000 元以下', max: 99_999 },
  { label: '1000–3000 元', min: 100_000, max: 299_999 },
  { label: '3000–8000 元', min: 300_000, max: 799_999 },
  { label: '8000 元以上', min: 800_000 }
]

/** 最近活跃：N 天内有下单/支付动作 */
export const ACTIVE_RANGES: { label: string; days: number }[] = [
  { label: '7 天内', days: 7 },
  { label: '30 天内', days: 30 },
  { label: '90 天内', days: 90 }
]

/** 续费倾向配色 */
export const RENEW_TAG_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  高: 'success',
  中: 'warning',
  新客: 'info',
  低: 'danger'
}

export const COMPLIANCE_NOTE =
  '客户名单导出会附带《合规使用承诺书》，禁止用于任何形式的理财募资、转账返利场景。'
