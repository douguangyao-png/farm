import request from '@/config/axios'

/** 农资商品、农资订单（契约 2.8） */
export interface SupplyVO {
  id?: number
  name: string
  cat: string
  cover?: string
  price: number // 分
  cost: number // 分
  stock: number
  unit: string
  spec: string
  fitModes: string[]
  batchNo: string
  retailAllowed: boolean
  needWorkorder: boolean
  status: number
  sold?: number
  description: string
}

export const getSupplyPage = (params: any) => request.get({ url: '/farm/supply/page', params })
export const getSupply = (id: number): Promise<SupplyVO> =>
  request.get({ url: '/farm/supply/get', params: { id } })
export const createSupply = (data: SupplyVO) => request.post({ url: '/farm/supply/create', data })
export const updateSupply = (data: SupplyVO) => request.put({ url: '/farm/supply/update', data })

export const getSupplyOrderPage = (params: any) =>
  request.get({ url: '/farm/supply-order/page', params })
export const traceSupplyOrder = (data: { id: number; usedAt: number; dosage: string }) =>
  request.put({ url: '/farm/supply-order/trace', data })
