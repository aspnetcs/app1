import {
  ADMIN_AI_ROUTE_CONTRACT,
  buildAdminMcpServerPath,
  buildAdminMcpServerRefreshPath,
  buildAdminMcpServerTestPath
} from "./adminAiRouteContract";
import { apiFetch } from "./http";

export type McpConfig = {
  enabled: boolean;
  maxServers: number;
  requestTimeoutMs: number;
};

export type McpServer = {
  id: number;
  name: string;
  description: string;
  endpointUrl: string;
  transportType: string;
  enabled: boolean;
  lastRefreshedAt?: string | null;
  createdAt?: string | null;
};

export type McpServerPayload = {
  name: string;
  description: string;
  endpointUrl: string;
  transportType?: string;
  authToken?: string;
  enabled: boolean;
};

export type McpServerMutationResult = {
  id: number;
  name: string;
};

export type McpToolSummary = {
  name: string;
  description: string;
};

export type McpConnectionResult = {
  success: boolean;
  toolCount?: number;
  tools?: McpToolSummary[];
  error?: string;
};

export function getMcpConfig() {
  return apiFetch<McpConfig>(ADMIN_AI_ROUTE_CONTRACT.mcpConfig);
}

export function updateMcpConfig(payload: Partial<McpConfig>) {
  return apiFetch<McpConfig>(ADMIN_AI_ROUTE_CONTRACT.mcpConfig, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function listMcpServers() {
  return apiFetch<McpServer[]>(ADMIN_AI_ROUTE_CONTRACT.mcpServers);
}

export function createMcpServer(payload: McpServerPayload) {
  return apiFetch<McpServerMutationResult>(ADMIN_AI_ROUTE_CONTRACT.mcpServers, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updateMcpServer(id: string | number, payload: Partial<McpServerPayload>) {
  return apiFetch<McpServerMutationResult>(buildAdminMcpServerPath(id), {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deleteMcpServer(id: string | number) {
  return apiFetch<void>(buildAdminMcpServerPath(id), {
    method: "DELETE"
  });
}

export function testMcpServer(id: string | number) {
  return apiFetch<McpConnectionResult>(buildAdminMcpServerTestPath(id), {
    method: "POST"
  });
}

export function refreshMcpServer(id: string | number) {
  return apiFetch<McpToolSummary[]>(buildAdminMcpServerRefreshPath(id), {
    method: "POST"
  });
}
