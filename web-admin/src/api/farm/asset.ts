import request from '@/config/axios'

/** 资产：地块、畜禽、果树（契约 2.3） */
export interface AssetVO {
  id?: number
  baseId?: number
  baseName?: string
  assetType: 'plot' | 'animal' | 'tree'
  code: string
  name?: string
  specs: { areaM2?: number; crop?: string; breed?: string; count?: number }
  status?: number
  cameraName?: string | null
  sensorName?: string | null
  adoptionId?: number | null
  memberName?: string
  careMode?: string | null
  createTime?: number
}

export const getAssetPage = (params: any) => request.get({ url: '/farm/asset/page', params })
export const getAsset = (id: number) => request.get({ url: '/farm/asset/get', params: { id } })
export const createAsset = (data: AssetVO) => request.post({ url: '/farm/asset/create', data })
export const updateAsset = (data: AssetVO) => request.put({ url: '/farm/asset/update', data })
export const getAssetEnv = (assetId: number) =>
  request.get({ url: '/farm/asset/env', params: { assetId } })
