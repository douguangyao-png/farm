import request from '@/config/axios'

/** 合同管理台账（契约 2.5.1），由订单派生 */
export interface ContractVO {
  id: number
  no: string
  orderId: number
  orderNo: string
  memberName: string
  memberMobile: string
  productTitle: string
  careMode: string
  careModeName: string
  version: string
  title: string
  /** 10 已签署 / 0 待签署 / 20 已解除 */
  status: number
  signMethod: string
  caAuth: boolean
  chainProof: boolean
  signedAt: number | null
  createTime: number
}

export const getContractPage = (params: any) =>
  request.get({ url: '/farm/contract/page', params })
