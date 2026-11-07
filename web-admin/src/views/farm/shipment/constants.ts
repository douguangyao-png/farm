import type { EnumItem } from '../constants'

/** 配送状态文案按原型 08：待发货 / 已发货 / 已完成（库里 0 / 10 / 20，原来叫待发货 / 运输中 / 已签收） */
export const SHIP_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待发货', type: 'danger' },
  { value: 10, label: '已发货', type: 'warning' },
  { value: 20, label: '已完成', type: 'success' }
]

/** 页签：name 是传给接口的 status（'all' 不传），countKey 对应 stats.counts 的字段 */
export const SHIP_TABS: { name: string | number; label: string; countKey: string }[] = [
  { name: 'all', label: '全部', countKey: 'all' },
  { name: 0, label: '待发货', countKey: 'pending' },
  { name: 10, label: '已发货', countKey: 'shipped' },
  { name: 20, label: '已完成', countKey: 'done' }
]

/** 自动配送规则（原型 08 的说明文案；约定交付斤数不足的部分按同等品质补发） */
export const AUTO_RULES: { k: string; v: string }[] = [
  { k: '生成时机', v: '录入收成后进入配送，同一天发出的配送单归为一个批次' },
  { k: '最小起送', v: '2 斤 / 单' },
  { k: '不足处理', v: '自动顺延至下一批次并推送告知；约定交付斤数不足的部分，按同等品质补发' },
  { k: '自提客户', v: '生成到店核销码，7 天内有效' },
  { k: '异常件', v: '物流异常自动触发补发或赔付' }
]
