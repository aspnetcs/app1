import { http } from './http'
import {
  PLATFORM_CODETOOLS_ROUTE_CONTRACT,
  buildCodeToolsTaskArtifactsPath,
  buildCodeToolsTaskLogsPath,
  buildCodeToolsTaskPath,
} from './platformCodeToolsRouteContract'

export type CodeToolsTaskApproval = {
  status?: string
  decidedBy?: string
  decidedAt?: string
  note?: string
}

export type CodeToolsTaskItem = {
  id: string
  userId?: string
  kind?: string
  status?: string
  inputJson?: string
  createdAt?: string
  updatedAt?: string
  approval?: CodeToolsTaskApproval | null
}

export type CodeToolsTaskListResponse = {
  items: CodeToolsTaskItem[]
  total: number
  page: number
  size: number
}

export type CodeToolsTaskLogItem = {
  id?: string
  taskId?: string
  level?: string
  message?: string
  createdAt?: string
}

export type CodeToolsTaskArtifactItem = {
  id?: string
  taskId?: string
  artifactType?: string
  name?: string
  mime?: string
  contentText?: string
  contentUrl?: string
  createdAt?: string
}

export function createCodeToolsTask(kind: string, input?: Record<string, unknown>) {
  return http.post<CodeToolsTaskItem>(
    PLATFORM_CODETOOLS_ROUTE_CONTRACT.tasks,
    { kind, input: input || undefined },
    { auth: true, silent: true },
  )
}

export function listCodeToolsTasks(page = 0, size = 20) {
  return http.get<CodeToolsTaskListResponse>(
    `${PLATFORM_CODETOOLS_ROUTE_CONTRACT.tasks}?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`,
    undefined,
    { auth: true, silent: true },
  )
}

export function getCodeToolsTask(id: string) {
  return http.get<CodeToolsTaskItem>(buildCodeToolsTaskPath(id), undefined, { auth: true, silent: true })
}

export function listCodeToolsTaskLogs(id: string) {
  return http.get<CodeToolsTaskLogItem[]>(buildCodeToolsTaskLogsPath(id), undefined, { auth: true, silent: true })
}

export function listCodeToolsTaskArtifacts(id: string) {
  return http.get<CodeToolsTaskArtifactItem[]>(
    buildCodeToolsTaskArtifactsPath(id),
    undefined,
    { auth: true, silent: true },
  )
}

