import { http } from './http'
import { PLATFORM_WEB_READ_ROUTE_CONTRACT } from './platformWebReadRouteContract'

export interface WebReadRequest {
  url: string
}

export interface WebReadResponse {
  url: string
  title: string
  content: string
  truncated: boolean
  contentLength: number
}

export const fetchWebRead = (data: WebReadRequest) =>
  http.post<WebReadResponse>(PLATFORM_WEB_READ_ROUTE_CONTRACT.fetch, data, { auth: true, silent: true })
