import request from '@/config/axios'

/** 配送批次（第一期补齐约定 2）：一个批次 = 一批收成 + 若干配送单，状态 0 待发 / 10 已发出 / 20 已完成 */
export const getShipBatchPage = (params: any) =>
  request.get({ url: '/farm/ship-batch/page', params })
export const getShipBatch = (id: number) => request.get({ url: '/farm/ship-batch/get', params: { id } })
export const createShipBatch = (data: {
  harvestId: number
  planDate: number
  remark?: string
  shipmentIds: number[]
}) => request.post({ url: '/farm/ship-batch/create', data })
export const shipShipBatch = (id: number) => request.put({ url: '/farm/ship-batch/ship', data: { id } })
export const finishShipBatch = (id: number) =>
  request.put({ url: '/farm/ship-batch/finish', data: { id } })
