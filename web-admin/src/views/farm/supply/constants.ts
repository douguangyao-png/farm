import type { EnumItem } from '../constants'

/** 农资类目名按原型 08 的页签叫法（取值 value 与接口一致；共享 constants.ts 里的叫法是旧的，这里不改它） */
export const SUPPLY_CAT_PROTO: EnumItem<string>[] = [
  { value: 'seed', label: '种子种苗', type: 'success' },
  { value: 'fert', label: '肥料基质', type: 'warning' },
  { value: 'tool', label: '农具耗材', type: 'info' },
  { value: 'bio', label: '绿色防控', type: 'success' },
  { value: 'svc', label: '代管服务', type: 'primary' },
  { value: 'ins', label: '保险加装', type: 'primary' },
  { value: 'pesticide', label: '化学农药', type: 'danger' }
]

/** 页签：全部 + 原型的 6 类（化学农药只在「全部」里看到，原型没有单独页签） */
export const SUPPLY_TABS: { name: string; label: string }[] = [
  { name: 'all', label: '全部' },
  ...SUPPLY_CAT_PROTO.filter((c) => c.value !== 'pesticide').map((c) => ({
    name: c.value as string,
    label: c.label
  }))
]

/** 商品状态（stateName）→ 标签颜色：正常 / 低库存 / 需工单 / 已下架 */
export const SUPPLY_STATE_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  正常: 'success',
  低库存: 'warning',
  需工单: 'info',
  已下架: 'danger'
}

/** 农资订单状态（stateName）：口径见接口契约 2.8 */
export const SUPPLY_ORDER_STATES = ['待使用', '已使用', '已安装', '待派单', '已完成', '已生效']
export const SUPPLY_ORDER_STATE_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  待使用: 'info',
  已使用: 'success',
  已安装: 'success',
  待派单: 'warning',
  已完成: 'success',
  已生效: 'success'
}
