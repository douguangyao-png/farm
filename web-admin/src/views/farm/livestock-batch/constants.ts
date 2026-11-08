import type { EnumItem } from '../constants'

/** 畜禽批次状态（第一期补齐约定 4） */
export const LIVESTOCK_STATUS: EnumItem<number>[] = [
  { value: 0, label: '在栏', type: 'success' },
  { value: 10, label: '出栏中', type: 'warning' },
  { value: 20, label: '已出栏', type: 'info' }
]
