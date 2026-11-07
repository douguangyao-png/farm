/** 认养商品列表页专用枚举（对齐原型 08 goodsList；共享 constants.ts 不改） */
import type { EnumItem } from '../constants'

/** 页签 / 状态文案：原型用「在售 / 已下架」，对应接口 status 0（上架）/ 1（下架） */
export const PRODUCT_SALE_STATUS: EnumItem<number>[] = [
  { value: 0, label: '在售', type: 'success' },
  { value: 1, label: '已下架', type: 'info' }
]

/** 页签：全部 / 在售 / 已下架（'all' 不传 status） */
export const PRODUCT_TABS = [
  { name: 'all', label: '全部' },
  { name: '0', label: '在售' },
  { name: '1', label: '已下架' }
]

/** 认养分类（接口 assetType → 分类名，与接口字段 categoryName 一致） */
export const PRODUCT_CATEGORIES: EnumItem<string>[] = [
  { value: 'plot', label: '土地认养' },
  { value: 'animal', label: '家禽认养' },
  { value: 'tree', label: '果树认养' }
]

/** 分类图标（原型里用 emoji） */
export const CATEGORY_ICON: Record<string, string> = { plot: '🥬', animal: '🐓', tree: '🍑' }

/** 周期文案：>=90 天按月显示，否则按天（365 天 → 12个月） */
export const cycleText = (days: number | null | undefined) =>
  !days ? '-' : days >= 90 ? `${Math.round(days / 30)}个月` : `${days}天`
