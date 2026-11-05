import request from '@/config/axios'

/** 农事记录审核（契约 2.6） */
export const getRecordPage = (params: any) => request.get({ url: '/farm/record/page', params })
export const getRecord = (id: number) => request.get({ url: '/farm/record/get', params: { id } })
export const auditRecord = (data: { id: number; pass: boolean; remark?: string }) =>
  request.put({ url: '/farm/record/audit', data })
