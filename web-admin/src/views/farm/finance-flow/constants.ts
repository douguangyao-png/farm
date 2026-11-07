/**
 * 交易流水页（第二期 B）。权限串：farm:financeflow:query / farm:financeflow:export
 * 流水由认养订单、农资订单、已完成的代管工单派生；服务费率 3%、T+1 结算为演示口径，不是真实支付流水。
 */
import type { EnumItem } from '../constants'

export const FLOW_TYPES: EnumItem<string>[] = [
  { value: 'adopt', label: '认养支付', type: 'success' },
  { value: 'supply', label: '农资购买', type: 'primary' },
  { value: 'service', label: '代管服务', type: 'warning' }
]

export const SETTLE_STATUS: EnumItem<string>[] = [
  { value: 'settled', label: '已结算', type: 'success' },
  { value: 'settling', label: '结算中', type: 'warning' }
]
