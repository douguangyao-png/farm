/** 经营者后台共用的枚举和格式化（取值见 docs/api/farm-接口契约.md 0.1） */
import { formatDate } from '@/utils/formatTime'

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
export interface EnumItem<T = string | number> {
  value: T
  label: string
  type?: TagType
  color?: string
}

export const CARE_MODES: EnumItem<string>[] = [
  { value: 'full', label: '全托管', type: 'success', color: '#2e7d32' },
  { value: 'semi', label: '半托管', type: 'warning', color: '#f9a825' },
  { value: 'self', label: '不托管', type: 'primary', color: '#1976d2' }
]

export const ASSET_TYPES: EnumItem<string>[] = [
  { value: 'plot', label: '地块', type: 'success' },
  { value: 'animal', label: '畜禽', type: 'warning' },
  { value: 'tree', label: '果树', type: 'primary' }
]

export const ASSET_STATUS: EnumItem<number>[] = [
  { value: 0, label: '空闲', type: 'info' },
  { value: 10, label: '已认养', type: 'success' },
  { value: 20, label: '停用', type: 'danger' }
]

export const BASE_STATUS: EnumItem<number>[] = [
  { value: 0, label: '正常', type: 'success' },
  { value: 1, label: '停用', type: 'danger' }
]

export const PRODUCT_STATUS: EnumItem<number>[] = [
  { value: 0, label: '上架', type: 'success' },
  { value: 1, label: '下架', type: 'info' }
]

export const ORDER_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待支付', type: 'warning' },
  { value: 10, label: '认养中', type: 'success' },
  { value: 20, label: '已完成', type: 'primary' },
  { value: 30, label: '已取消', type: 'info' }
]

export const RECORD_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待审核', type: 'warning' },
  { value: 10, label: '已通过', type: 'success' },
  { value: 20, label: '已驳回', type: 'danger' }
]

export const RECORD_KIND: EnumItem<string>[] = [
  { value: 'farming', label: '农事' },
  { value: 'harvest', label: '采收' },
  { value: 'service', label: '代管服务' }
]

export const SERVICE_ORDER_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待接单', type: 'warning' },
  { value: 10, label: '已接单', type: 'primary' },
  { value: 20, label: '已完成', type: 'success' },
  { value: 30, label: '已取消', type: 'info' }
]

export const SUPPLY_CATS: EnumItem<string>[] = [
  { value: 'seed', label: '种子', type: 'success' },
  { value: 'fert', label: '肥料', type: 'warning' },
  { value: 'tool', label: '农具', type: 'info' },
  { value: 'bio', label: '生物防治', type: 'success' },
  { value: 'svc', label: '代管服务', type: 'primary' },
  { value: 'ins', label: '保险', type: 'primary' },
  { value: 'pesticide', label: '化学农药', type: 'danger' }
]

export const SUPPLY_STATUS: EnumItem<number>[] = [
  { value: 0, label: '上架', type: 'success' },
  { value: 1, label: '下架', type: 'info' }
]

export const DELIVER_WAYS: EnumItem<string>[] = [
  { value: 'store', label: '寄存基地' },
  { value: 'express', label: '快递到家' },
  { value: 'pickup', label: '基地自提' },
  { value: 'apply', label: '农场代施' }
]

export const TRACE_STATE: EnumItem<number>[] = [
  { value: 0, label: '待登记', type: 'warning' },
  { value: 10, label: '已写入', type: 'success' }
]

export const SHIPMENT_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待发货', type: 'info' },
  { value: 10, label: '运输中', type: 'warning' },
  { value: 20, label: '已签收', type: 'success' }
]

/** 13 项农事责任矩阵的默认条目（与种子商品 1 一致） */
export const DUTY_ITEMS = [
  '整地',
  '播种 / 育苗',
  '浇水',
  '除草',
  '追肥',
  '搭架',
  '病虫害统防',
  '日常巡田',
  '采收',
  '分拣包装',
  '配送（12 次）',
  '监控与环境数据',
  '技术指导'
]

/** 取枚举项；找不到时返回原值作为文字 */
export function enumOf<T extends string | number>(
  list: EnumItem<T>[],
  value: T | null | undefined
) {
  return (
    list.find((x) => x.value === value) ?? {
      value,
      label: value == null ? '-' : String(value),
      type: 'info' as TagType
    }
  )
}
export const enumLabel = <T extends string | number>(
  list: EnumItem<T>[],
  value: T | null | undefined
) => enumOf(list, value).label

/** 分 → ¥1,280.00 */
export function formatMoney(fen: number | null | undefined) {
  if (fen == null || isNaN(Number(fen))) return '-'
  return (
    '¥' +
    (Number(fen) / 100).toLocaleString('zh-CN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })
  )
}

/** 毫秒时间戳 → 字符串 */
export function fmtTime(ms: number | null | undefined, format = 'YYYY-MM-DD HH:mm') {
  return ms ? formatDate(new Date(ms), format) : '-'
}
export const fmtDay = (ms: number | null | undefined) => fmtTime(ms, 'YYYY-MM-DD')

/** el-table formatter：金额列 */
export const moneyFormatter = (_r: any, _c: any, v: any) => formatMoney(v)
/** el-table formatter：时间列 */
export const timeFormatter = (_r: any, _c: any, v: any) => fmtTime(v)
