import request from '@/config/axios'

/** 项目文档（R-13，只给平台管理员） */
export interface DocItemVO {
  path: string
  title: string
  category: string
  type: 'md' | 'html'
  size: number
  updateTime: number
}

export interface DocVO extends DocItemVO {
  content: string
}

export const getDocList = (): Promise<DocItemVO[]> => request.get({ url: '/farm/doc/list' })

export const getDoc = (path: string): Promise<DocVO> =>
  request.get({ url: '/farm/doc/get', params: { path } })
