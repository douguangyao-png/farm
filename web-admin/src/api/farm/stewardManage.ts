import request from '@/config/axios'

/** 管家管理（第二期 A，只读，由管家账号+认养+任务+代管工单派生） */
export interface StewardManageVO {
  id: number
  name: string
  mobile: string
  roleName: string
  baseId: number | null
  baseName: string
  assetTotal: number
  assetAdopted: number
  memberCount: number
  monthTaskTotal: number
  monthTaskDone: number
  /** 百分比整数；本月没有任务时 null */
  monthTaskRate: number | null
  /** 分钟；没有接单记录时 null */
  avgResponseMinutes: number | null
  status: number
}

export interface StewardOverviewVO {
  ranking: { id: number; name: string; score: number }[]
  today: { pendingTasks: number; pendingRecords: number; pendingServiceOrders: number }
}

export const getStewardManagePage = (params: any) =>
  request.get({ url: '/farm/steward-manage/page', params })
export const getStewardOverview = (): Promise<StewardOverviewVO> =>
  request.get({ url: '/farm/steward-manage/overview' })
