import request from '@/config/axios'

/** IoT 设备、视频监控（第二期 B）。状态、最近上报、固件为演示数据 */
export const getDevicePage = (params: any) => request.get({ url: '/farm/device/page', params })
export const createDevice = (data: {
  kind: string
  typeName?: string
  name: string
  position?: string
  bindText?: string
}) => request.post({ url: '/farm/device/create', data })
export const updateDevice = (data: { code: string; position?: string; firmware?: string }) =>
  request.put({ url: '/farm/device/update', data })
export const dispatchDevice = (code: string) =>
  request.put({ url: '/farm/device/dispatch', data: { code } })
export const getCameraOverview = () => request.get({ url: '/farm/camera/overview' })
