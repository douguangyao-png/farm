/**
 * 平台端、问题反馈、项目文档共用的枚举（取值见 docs/api/farm-接口契约.md 0.1 节）。
 * 经营者业务页面的枚举在 views/farm/constants.ts，由业务页面维护，这里不重复。
 */
import { getTenantId } from '@/utils/auth'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
export interface EnumItem<T = string | number> {
  value: T
  label: string
  tag: TagType
}

/** 平台租户 id（平台运营中心） */
export const PLATFORM_TENANT_ID = 1

/** 当前登录的是不是平台管理员（按登录租户判断，不看「切换租户」后的访问租户） */
export const isPlatformUser = () => Number(getTenantId()) === PLATFORM_TENANT_ID

export const findEnum = <T>(list: EnumItem<T>[], value: T) => list.find((x) => x.value === value)

/** 风控预警类型 riskAlert.type */
export const RISK_ALERT_TYPES: EnumItem<string>[] = [
  { value: 'promise', label: '承诺收益', tag: 'danger' },
  { value: 'fake_base', label: '虚构基地', tag: 'danger' },
  { value: 'brush', label: '刷单', tag: 'warning' },
  { value: 'scope', label: '超范围经营', tag: 'warning' }
]

/** 风控预警等级 level：3 高 2 中 1 低 */
export const RISK_ALERT_LEVELS: EnumItem<number>[] = [
  { value: 3, label: '高', tag: 'danger' },
  { value: 2, label: '中', tag: 'warning' },
  { value: 1, label: '低', tag: 'info' }
]

/** 预警状态 riskAlert.status */
export const RISK_ALERT_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待处理', tag: 'warning' },
  { value: 10, label: '已处理', tag: 'success' },
  { value: 20, label: '已忽略', tag: 'info' }
]

/** 反馈类型 feedback.type */
export const FEEDBACK_TYPES: EnumItem<string>[] = [
  { value: 'bug', label: '问题', tag: 'danger' },
  { value: 'advice', label: '建议', tag: 'primary' },
  { value: 'ask', label: '咨询', tag: 'info' }
]

/** 反馈状态 feedback.status */
export const FEEDBACK_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待处理', tag: 'warning' },
  { value: 10, label: '处理中', tag: 'primary' },
  { value: 20, label: '已解决', tag: 'success' },
  { value: 30, label: '已关闭', tag: 'info' }
]

/** 项目文档分类的显示顺序（R-13） */
export const DOC_CATEGORY_ORDER = ['需求', '设计', '方案与任务', '原型', '演示', '调研与定价']

/** 金额：分 → 「¥1,280.00」 */
export const fenToYuan = (fen?: number | null) =>
  fen === undefined || fen === null
    ? '-'
    : '¥' +
      (fen / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
