import { apiFetch } from "./http";
import {
  buildAdminAgentDetailPath,
  ADMIN_ENTITY_ROUTE_CONTRACT,
} from "./adminEntityRouteContract";

export type AgentScope = "SYSTEM" | "USER";

export type AgentItem = {
  id: string;
  name?: string;
  description?: string;
  category?: string;
  modelId?: string;
  scope?: AgentScope;
  enabled?: boolean;
  featured?: boolean;
  sortOrder?: number;
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  tags?: string;
  author?: string;
  systemPrompt?: string;
  firstMessage?: string;
  updatedAt?: string;
  createdAt?: string;
  userId?: string;
  [key: string]: unknown;
};

export type AgentListParams = {
  page?: number;
  size?: number;
  scope?: AgentScope;
  category?: string;
  search?: string;
  enabled?: boolean;
  featured?: boolean;
};

export type AgentsListResponse = {
  items: AgentItem[];
  total: number;
  page: number;
  size: number;
};

export type AgentUpsertPayload = {
  name: string;
  modelId: string;
  systemPrompt?: string;
  description?: string;
  firstMessage?: string;
  tags?: string;
  author?: string;
  category?: string;
  scope?: AgentScope;
  enabled?: boolean;
  featured?: boolean;
  sortOrder?: number;
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  avatar?: string;
  icon?: string;
  requiredToolsJson?: string;
  contextMessagesJson?: string;
  userId?: string | null;
};

const AGENTS_BASE_PATH = ADMIN_ENTITY_ROUTE_CONTRACT.agents;

function buildAgentListPath(params: AgentListParams = {}) {
  const query = new URLSearchParams();
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  if (params.scope) query.set("scope", params.scope);
  if (params.category) query.set("category", params.category);
  if (params.search) query.set("search", params.search);
  if (params.enabled != null) query.set("enabled", String(params.enabled));
  if (params.featured != null) query.set("featured", String(params.featured));
  return `${AGENTS_BASE_PATH}?${query.toString()}`;
}

export function listAgentsByFilter(params: AgentListParams = {}) {
  return apiFetch<AgentsListResponse>(buildAgentListPath(params));
}

export function createAgent(payload: AgentUpsertPayload) {
  return apiFetch<AgentItem>(AGENTS_BASE_PATH, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateAgent(id: string | number, payload: AgentUpsertPayload) {
  return apiFetch<AgentItem>(buildAdminAgentDetailPath(id), {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function deleteAgent(id: string | number) {
  return apiFetch<void>(buildAdminAgentDetailPath(id), {
    method: "DELETE",
  });
}
