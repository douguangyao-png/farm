import request from '@/config/axios'

/** 平台端（契约第 1 节）：平台总览、风控预警。租户、套餐用芋道自带的 api/system/tenant */

export interface TenantRankVO {
  tenantId: number
  tenantName: string
  packageName?: string
  gmvMonth: number
  adoptingCount?: number
}

export interface PlatformOverviewVO {
  tenantCount: number
  activeTenantCount: number
  gmvMonth: number
  orderCountMonth: number
  pendingAlertCount: number
  feedbackPendingCount: number
  tenantRank: TenantRankVO[]
}

export interface RiskAlertVO {
  id: number
  tenantId: number
  tenantName: string
  type: string
  level: number
  title: string
  content: string
  source: string
  status: number
  handleRemark?: string
  createTime: number
  handleTime?: number
}

export const getPlatformOverview = (): Promise<PlatformOverviewVO> =>
  request.get({ url: '/farm/platform/overview' })

export const getRiskAlertPage = (params: any): Promise<PageResult<RiskAlertVO[]>> =>
  request.get({ url: '/farm/risk-alert/page', params })

/** status 10 已处理 / 20 已忽略；handleRemark 必填 */
export const handleRiskAlert = (data: { id: number; status: number; handleRemark: string }) =>
  request.put({ url: '/farm/risk-alert/handle', data })
