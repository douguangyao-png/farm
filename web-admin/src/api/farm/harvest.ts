import request from '@/config/axios'

/** 收成与配送（契约 2.9） */
export const getHarvestPage = (params: any) => request.get({ url: '/farm/harvest/page', params })
export const createHarvest = (data: {
  adoptionId: number
  crop: string
  weightJin: number
  harvestDate: number
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
