import request from '@/config/axios'

/** 财务：交易流水、分账结算（第二期 B；契约见 docs/plans/第二期-登记/B.md）。金额单位：分 */
export const getFlowPage = (params: any) => request.get({ url: '/farm/finance/flow/page', params })
export const getSplit = () => request.get({ url: '/farm/finance/split/get' })

/** 经营分析：days = 7 / 30 / 90 */
export const getAnalysis = (days: number) =>
  request.get({ url: '/farm/analysis/summary', params: { days } })
