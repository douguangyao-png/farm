import request from '@/config/axios'

/** 基地（契约 2.2） */
export interface BaseVO {
  id?: number
  name: string
  address: string
  lng?: number
  lat?: number
  areaMu?: number
  certNo?: string
  cropSummary?: string
  status: number
  assetCount?: number
  stewardCount?: number
  createTime?: number
}

export const getBasePage = (params: any) => request.get({ url: '/farm/base/page', params })
export const getBaseList = (): Promise<{ id: number; name: string }[]> =>
  request.get({ url: '/farm/base/list' })
export const getBase = (id: number) => request.get({ url: '/farm/base/get', params: { id } })
export const createBase = (data: BaseVO) => request.post({ url: '/farm/base/create', data })
export const updateBase = (data: BaseVO) => request.put({ url: '/farm/base/update', data })
export const deleteBase = (id: number) =>
  request.delete({ url: '/farm/base/delete', params: { id } })
