/** 资质证照页枚举与说明（第二期 B）。权限串：farm:compliancecert:query / create / update */
import type { EnumItem } from '../constants'

export const CERT_TYPES: EnumItem<string>[] = [
  { value: '必备', label: '必备', type: 'danger' },
  { value: '建议', label: '建议', type: 'warning' },
  { value: '按需', label: '按需', type: 'info' }
]

export const VERIFY_STATUS: EnumItem<string>[] = [
  { value: 'verified', label: '已核验', type: 'success' },
  { value: 'pending', label: '待核验', type: 'warning' },
  { value: 'renew', label: '待更新', type: 'warning' },
  { value: 'expired', label: '已过期', type: 'danger' }
]

/** 公示规则（原型 08 cmpDoc 的说明文案；限制上架、自动下架等联动本期未做，仅作说明） */
export const RULES: { k: string; v: string }[] = [
  { k: '必备证照缺失', v: '禁止上架商品' },
  { k: '证照过期', v: '自动下架相关品类' },
  { k: 'C 端公示', v: '脱敏展示 + 可核验二维码' },
  { k: '更新留痕', v: '历史版本不可删除，供审计' }
]
