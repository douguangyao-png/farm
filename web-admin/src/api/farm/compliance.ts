import request from '@/config/axios'

/** 合规中心：资质证照、资金公示（第二期 B） */
export interface CertVO {
  id: number
  name: string
  no: string
  type: '必备' | '建议' | '按需'
  validUntil: number | null
  verify: 'verified' | 'pending' | 'renew' | 'expired'
  verifyName: string
  publicMode: 'public' | 'private'
  publicName: string
  daysLeft: number | null
  expiringSoon: boolean
  editable: boolean
  source: 'upload' | 'base'
  demo: boolean
  fileUrl?: string
}
export const getCertPage = (params: any) => request.get({ url: '/farm/cert/page', params })
export const createCert = (data: {
  name: string
  no: string
  type: string
  validUntil: number | null
  publicMode: string
  fileUrl?: string
}) => request.post({ url: '/farm/cert/create', data })
export const updateCertPublic = (id: number, publicMode: string) =>
  request.put({ url: '/farm/cert/update-public', data: { id, publicMode } })

export const getFundNotice = () => request.get({ url: '/farm/fund-notice/get' })
export const saveFundNotice = (fields: Record<string, boolean>) =>
  request.put({ url: '/farm/fund-notice/save', data: { fields } })
