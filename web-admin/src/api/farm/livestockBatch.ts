import request from '@/config/axios'

/** 畜禽批次（第一期补齐约定 4）：状态 0 在栏 / 10 出栏中 / 20 已出栏 */
export interface LivestockBatchVO {
  id?: number
  no?: string
  baseId?: number
  baseName?: string
  species: string
  breed?: string
  inDate?: number
  count: number
  outCount?: number
  status?: number
  pen?: string
  remark?: string
}
export const getLivestockBatchPage = (params: any) =>
  request.get({ url: '/farm/livestock-batch/page', params })
export const getLivestockBatch = (id: number) =>
  request.get({ url: '/farm/livestock-batch/get', params: { id } })
export const createLivestockBatch = (data: LivestockBatchVO) =>
  request.post({ url: '/farm/livestock-batch/create', data })
export const updateLivestockBatch = (data: LivestockBatchVO) =>
  request.put({ url: '/farm/livestock-batch/update', data })
export const outLivestockBatch = (data: { id: number; count: number }) =>
  request.put({ url: '/farm/livestock-batch/out', data })
export const deleteLivestockBatch = (id: number) =>
  request.delete({ url: '/farm/livestock-batch/delete', params: { id } })
