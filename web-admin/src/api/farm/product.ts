import request from '@/config/axios'

/** 认养商品（契约 2.4） */
export interface DutyItem {
  item: string
  owner: 'farm' | 'member'
}
export interface CareModeConfig {
  mode: 'full' | 'semi' | 'self'
  enabled: boolean
  price: number // 分
  slogan: string
  outputPromise: string
  contractVersion: string
  weeklyTodo: number
  insurance: string
  duty: DutyItem[]
}
export interface ProductVO {
  id?: number
  baseId?: number
  baseName?: string
  assetType: string
  title: string
  cover?: string
  description: string
  cycleDays: number
  areaM2?: number | null
  basePrice?: number
  stock: number
  sold?: number
  status: number
  careModes: CareModeConfig[]
  minPrice?: number
  enabledModes?: string[]
  createTime?: number
  /** 以下列表展示用，由接口派生，保存时不用传（契约 2.4） */
  no?: string
  categoryName?: string
  totalSlots?: number
  soldRate?: number
  insured?: boolean
  expectedOutput?: string
}

/** 违禁词拦截的业务错误码 */
export const BANNED_WORDS_CODE = 1_030_000_000

export const getProductPage = (params: any) => request.get({ url: '/farm/product/page', params })
export const getProduct = (id: number): Promise<ProductVO> =>
  request.get({ url: '/farm/product/get', params: { id } })
/** create / update 用 bizError：错误不弹通知，抛出 { code, msg, data }，由表单展示命中词 */
export const createProduct = (data: ProductVO) =>
  request.post({ url: '/farm/product/create', data, bizError: true })
export const updateProduct = (data: ProductVO) =>
  request.put({ url: '/farm/product/update', data, bizError: true })
export const updateProductStatus = (id: number, status: number) =>
  request.put({ url: '/farm/product/update-status', data: { id, status } })
