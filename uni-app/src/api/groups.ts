import { http } from './http'
import { PLATFORM_USER_ROUTE_CONTRACT } from './platformUserRouteContract'
import type { UserGroupProfileResponse } from './types'

export const getMyGroupProfile = () =>
  http.get<UserGroupProfileResponse>(PLATFORM_USER_ROUTE_CONTRACT.groupsMe, undefined, { auth: true, silent: true })
