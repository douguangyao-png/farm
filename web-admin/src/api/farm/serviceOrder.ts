import request from '@/config/axios'

/** 代管工单（契约 2.7） */
export const getServiceOrderPage = (params: any) =>
  request.get({ url: '/farm/service-order/page', params })
export const getServiceOrder = (id: number) =>
  request.get({ url: '/farm/service-order/get', params: { id } })
