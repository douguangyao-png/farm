import request from '@/config/axios'

/** 工作台汇总（契约 2.1） */
export const getDashboardSummary = () => request.get({ url: '/farm/dashboard/summary' })

/** 风险自检（契约 2.1） */
export const getRiskSelfCheck = () => request.get({ url: '/farm/risk/self-check' })

/** 平台公告（平台在文章管理里发布的公告、帮助） */
export const getNoticeList = () => request.get({ url: '/farm/notice/list' })
export const getNotice = (id: number) => request.get({ url: '/farm/notice/get', params: { id } })
