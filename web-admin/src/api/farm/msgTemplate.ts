import request from '@/config/axios'

/** 消息模板（第二期 B）。违禁词命中返回 code 1030000000，用 bizError 让页面展示命中词 */
export interface MsgTemplateVO {
  id?: number
  name: string
  trigger: string
  channels: string[]
  content: string
  status?: number
}
export const getMsgTemplatePage = (params: any) =>
  request.get({ url: '/farm/msg-template/page', params })
export const createMsgTemplate = (data: MsgTemplateVO) =>
  request.post({ url: '/farm/msg-template/create', data, bizError: true })
export const updateMsgTemplate = (data: MsgTemplateVO) =>
  request.put({ url: '/farm/msg-template/update', data, bizError: true })
export const updateMsgTemplateStatus = (id: number, status: number) =>
  request.put({ url: '/farm/msg-template/update-status', data: { id, status } })
export const testSendMsgTemplate = (id: number) =>
  request.post({ url: '/farm/msg-template/test-send', data: { id } })
