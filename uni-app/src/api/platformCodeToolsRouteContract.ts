import { buildPlatformApiPath } from './platformUserRouteContract'

function encodeRouteParam(value: string) {
  return encodeURIComponent(value)
}

export const PLATFORM_CODETOOLS_ROUTE_CONTRACT = {
  tasks: buildPlatformApiPath('code-tools/tasks'),
} as const

export function buildCodeToolsTaskPath(id: string) {
  return `${PLATFORM_CODETOOLS_ROUTE_CONTRACT.tasks}/${encodeRouteParam(id)}`
}

export function buildCodeToolsTaskLogsPath(id: string) {
  return `${buildCodeToolsTaskPath(id)}/logs`
}

export function buildCodeToolsTaskArtifactsPath(id: string) {
  return `${buildCodeToolsTaskPath(id)}/artifacts`
}

