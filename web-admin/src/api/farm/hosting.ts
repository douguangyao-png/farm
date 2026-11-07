import request from '@/config/axios'

/** 托管模式配置：只读汇总；保存直接调 updateProduct（PUT /farm/product/update），口径与认养商品表单同一份 careModes */
export const getHostingConfig = (productId?: number) =>
  request.get({ url: '/farm/hosting-config/get', params: { productId } })
