/**
 * 资产上传 API
 */
import { http } from './http'
import { PLATFORM_ASSET_ROUTE_CONTRACT } from './platformAssetRouteContract'
import type { PresignRequest, PresignResponse, ConfirmRequest, ConfirmResponse } from './types'

export const getPresignUrl = (data: PresignRequest) =>
  http.post<PresignResponse>(PLATFORM_ASSET_ROUTE_CONTRACT.presign, data, { auth: true })

export const confirmUpload = (data: ConfirmRequest) =>
  http.post<ConfirmResponse>(PLATFORM_ASSET_ROUTE_CONTRACT.confirm, data, { auth: true })
