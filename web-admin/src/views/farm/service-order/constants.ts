import type { EnumItem } from '../constants'

/** 代管工单状态文案按原型 08：待接单 / 服务中 / 已完成（库里 0 / 10 / 20，30 已取消原型没有） */
export const SO_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待接单', type: 'danger' },
  { value: 10, label: '服务中', type: 'warning' },
  { value: 20, label: '已完成', type: 'success' },
  { value: 30, label: '已取消', type: 'info' }
]

/** 页签：name 是传给接口的 status（'all' 不传），countKey 对应 stats.counts 的字段 */
export const SO_TABS: { name: string | number; label: string; countKey: string }[] = [
  { name: 'all', label: '全部', countKey: 'all' },
  { name: 0, label: '待接单', countKey: 'pending' },
  { name: 10, label: '服务中', countKey: 'serving' },
  { name: 20, label: '已完成', countKey: 'done' },
  { name: 30, label: '已取消', countKey: 'cancelled' }
]

/** 分账规则（每单自动执行，演示，不真实分账） */
export const SPLIT_RULES = [
  { name: '执行管家', pct: 70, sample: '¥21' },
  { name: '农场（设施与耗材）', pct: 20, sample: '¥6' },
  { name: '平台技术服务费', pct: 10, sample: '¥3' }
]
