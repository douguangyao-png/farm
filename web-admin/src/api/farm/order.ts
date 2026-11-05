import request from '@/config/axios'

/** 认养订单（契约 2.5） */
export const getOrderPage = (params: any) => request.get({ url: '/farm/order/page', params })
export const getOrder = (id: number) => request.get({ url: '/farm/order/get', params: { id } })
