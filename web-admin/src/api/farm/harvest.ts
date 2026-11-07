import request from '@/config/axios'

/** 收成与配送（契约 2.9） */
export const getHarvestPage = (params: any) => request.get({ url: '/farm/harvest/page', params })
export interface HarvestItem {
  crop: string
  weightJin: number
}
export const createHarvest = (data: {
  adoptionId: number
  /** 收获明细（可多行）；合计作为本批重量，品类名用「、」连接存进 crop */
  items: HarvestItem[]
  harvestDate: number
  /** 农事类型，不传按「采摘采收」 */
  farmType?: string
  photos?: string[]
  remark?: string
}) => request.post({ url: '/farm/harvest/create', data })
export const getAdoptionSimpleList = (): Promise<
  { id: number; label: string; assetCode: string; memberName: string; careMode: string }[]
> => request.get({ url: '/farm/adoption/simple-list' })

export const getShipmentPage = (params: any) => request.get({ url: '/farm/shipment/page', params })
export const createShipment = (data: {
  harvestId: number
  carrier: string
  logisticsNo: string
  coldChain?: boolean
}) => request.post({ url: '/farm/shipment/create', data })
export const signShipment = (id: number) =>
  request.put({ url: '/farm/shipment/sign', data: { id } })
