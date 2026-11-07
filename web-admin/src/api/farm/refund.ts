import request from '@/config/axios'

/** 退款审核（第二期 A，登记见 docs/plans/第二期-登记/A.md）。金额一律分 */
export interface RefundVO {
  id: number
  no: string
  orderId: number
  orderNo: string
  orderStatus: number | null
  memberId: number
  memberName: string
  memberMobile: string
  productTitle: string
  assetCode: string
  careMode: string | null
  careModeName: string
  /** 实付 */
  amount: number
  /** 已发生农事投入 */
  investDeduct: number
  /** 已交付农产品折算 */
  deliverDeduct: number
  /** 扣减合计 = investDeduct + deliverDeduct */
  deduct: number
  /** 应退 = amount - deduct */
  refundAmount: number
  reason: string
  /** 0 待审核 / 10 已通过 / 20 已驳回 */
  status: number
  statusName: string
  auditRemark: string | null
  createTime: number
  auditTime: number | null
}

export interface RefundDetailVO extends RefundVO {
  adoption: {
    id: number
    startAt: number
    endAt: number
    promisedJin: number | null
    harvestedJin: number
  } | null
  /** 申请时已过周期比例 0-1 */
  elapsedRate: number
}

export interface RefundStatsVO {
  counts: Record<string, number>
  monthApply: number
  refundRate: number | null
  pending: number
  pendingOver24h: number
  avgHandleHours: number | null
  refundedAmount: number
  refundedCount: number
  reasonAlert: { reason: string; count: number } | null
}

export const getRefundPage = (params: any) => request.get({ url: '/farm/refund/page', params })
export const getRefundStats = (): Promise<RefundStatsVO> => request.get({ url: '/farm/refund/stats' })
export const getRefund = (id: number): Promise<RefundDetailVO> =>
  request.get({ url: '/farm/refund/get', params: { id } })
export const auditRefund = (data: { id: number; approve: boolean; remark?: string }) =>
  request.put({ url: '/farm/refund/audit', data })
