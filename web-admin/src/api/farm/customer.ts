import request from '@/config/axios'

/** 客户管理 / 客户分层（第二期 A，只读，由会员+订单派生）。金额一律分 */
export interface CustomerVO {
  id: number
  name: string
  /** 已脱敏 */
  mobile: string
  city: string
  /** V1-V4，按累计消费的演示区间，不是会员积分体系 */
  level: string
  adoptCount: number
  totalAmount: number
  tags: string[]
  lastActive: number | null
  tier: 'core' | 'growth' | 'new' | 'sleep' | 'other'
  tierName: string
  renewIntent: string
  activeAdoptionCount: number
  expiringCount: number
}

export interface CustomerStatsVO {
  total: number
  newThisMonth: number
  adopting: number
  expiring30: number
  repeatRate: number | null
  avgOrderAmount: number
  cities: string[]
  tags: string[]
}

export interface TierVO {
  key: string
  name: string
  level: string
  levelType: 'success' | 'warning' | 'info' | 'danger'
  rule: string
  action: string
  count: number
}

export interface TierSummaryVO {
  total: number
  tiers: TierVO[]
  expiring: {
    customerCount: number
    adoptionCount: number
    estimateAmount: number
    remindDays: number[]
  }
  packs: { name: string; count: number; buildWay: string; tier: string | null; query: string }[]
  updatedAt: number
}

export const getCustomerPage = (params: any) => request.get({ url: '/farm/customer/page', params })
export const getCustomerStats = (): Promise<CustomerStatsVO> =>
  request.get({ url: '/farm/customer/stats' })
export const getTierSummary = (): Promise<TierSummaryVO> =>
  request.get({ url: '/farm/customer-tier/summary' })
