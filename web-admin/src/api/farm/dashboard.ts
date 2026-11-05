import request from '@/config/axios'

/** 工作台汇总（契约 2.1） */
export const getDashboardSummary = () => request.get({ url: '/farm/dashboard/summary' })

/** 风险自检（契约 2.1） */
export const getRiskSelfCheck = () => request.get({ url: '/farm/risk/self-check' })
