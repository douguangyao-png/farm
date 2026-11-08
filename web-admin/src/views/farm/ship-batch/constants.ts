import type { EnumItem } from '../constants'

/** 配送批次状态（第一期补齐约定 2） */
export const BATCH_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待发', type: 'danger' },
  { value: 10, label: '已发出', type: 'warning' },
  { value: 20, label: '已完成', type: 'success' }
]
