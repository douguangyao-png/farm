import request from '@/config/axios'

/** 问题反馈（R-12，契约 2.10） */
export interface FeedbackReplyVO {
  id: number
  content: string
  creatorName: string
  createTime: number
}

export interface FeedbackVO {
  id: number
  no: string
  tenantId: number
  tenantName: string
  type: 'bug' | 'advice' | 'ask'
  content: string
  screenshots: string[]
  pageTitle: string
  pagePath: string
  userAgent: string
  creatorName: string
  status: number
  replies: FeedbackReplyVO[]
  createTime: number
  updateTime: number
}

export interface FeedbackCreateReqVO {
  type: FeedbackVO['type']
  content: string
  screenshots: string[]
  pageTitle: string
  pagePath: string
  userAgent: string
}

export const createFeedback = (data: FeedbackCreateReqVO): Promise<{ id: number; no: string }> =>
  request.post({ url: '/farm/feedback/create', data })

export const getFeedbackPage = (params: any): Promise<PageResult<FeedbackVO[]>> =>
  request.get({ url: '/farm/feedback/page', params })

export const getFeedback = (id: number): Promise<FeedbackVO> =>
  request.get({ url: '/farm/feedback/get', params: { id } })

/** 仅平台 */
export const replyFeedback = (data: { id: number; content: string }) =>
  request.post({ url: '/farm/feedback/reply', data })

/** 仅平台；status 0 待处理 10 处理中 20 已解决 30 已关闭 */
export const updateFeedbackStatus = (data: { id: number; status: number }) =>
  request.put({ url: '/farm/feedback/update-status', data })
