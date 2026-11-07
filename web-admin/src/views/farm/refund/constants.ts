/**
 * 退款审核页自己用的常量。
 * 权限串（第二期权限清单「A」小节）：
 *   farm:refund:query   查看退款单与统计
 *   farm:refund:audit   审核（通过并退款 / 驳回）
 *   farm:refund:export  导出退款台账（演示，暂未实现）
 */
import type { EnumItem } from '../constants'

/** 退款单状态（接口 refund.status） */
export const REFUND_STATUS: EnumItem<number>[] = [
  { value: 0, label: '待审核', type: 'warning' },
  { value: 10, label: '已通过', type: 'success' },
  { value: 20, label: '已驳回', type: 'danger' }
]

/** 页签：value 为空 = 全部 */
export const REFUND_TABS: { value: number | undefined; label: string }[] = [
  { value: undefined, label: '全部' },
  { value: 0, label: '待审核' },
  { value: 10, label: '已通过' },
  { value: 20, label: '已驳回' }
]
