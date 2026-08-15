import { buildAdminApiPath } from "./authRouteContract";

export type AdminEntityRouteId = string | number;

export type AdminModelsListPathParams = {
  page?: number;
  size?: number;
  keyword?: string;
};

export type AdminUsersListPathParams = {
  page?: number;
  size?: number;
  search?: string;
  roleFilter?: string;
};

function encodeAdminEntityRouteId(id: AdminEntityRouteId) {
  return encodeURIComponent(String(id));
}

function buildPagedPath(basePath: string, page = 0, size = 20) {
  return `${basePath}?page=${page}&size=${size}`;
}

function buildDetailPath(basePath: string, id: AdminEntityRouteId) {
  return `${basePath}/${encodeAdminEntityRouteId(id)}`;
}

export const ADMIN_ENTITY_ROUTE_CONTRACT = {
  agents: buildAdminApiPath("agents"),
  banners: buildAdminApiPath("banners"),
  configTransfer: buildAdminApiPath("config-transfer"),
  conversations: buildAdminApiPath("conversations"),

  groups: buildAdminApiPath("groups"),
  messages: buildAdminApiPath("messages"),
  models: buildAdminApiPath("models"),
  oauth: buildAdminApiPath("oauth"),
  pwa: buildAdminApiPath("pwa"),
  users: buildAdminApiPath("users")
} as const;

export const ADMIN_ENTITY_ROUTE_ENDPOINTS = {
  configTransferConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.configTransfer}/config`,
  configTransferExport: `${ADMIN_ENTITY_ROUTE_CONTRACT.configTransfer}/export`,
  configTransferPreview: `${ADMIN_ENTITY_ROUTE_CONTRACT.configTransfer}/preview`,
  configTransferImport: `${ADMIN_ENTITY_ROUTE_CONTRACT.configTransfer}/import`,
  conversationsTimelineConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.conversations}/timeline-config`,
  conversationsPinStarConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.conversations}/pin-star-config`,
  conversationsMessageVersionConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.conversations}/message-version-config`,
  messagesVersionConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.messages}/version-config`,
  modelsReorder: `${ADMIN_ENTITY_ROUTE_CONTRACT.models}/reorder`,
  oauthConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.oauth}/config`,
  pwaConfig: `${ADMIN_ENTITY_ROUTE_CONTRACT.pwa}/config`,
  usersStats: `${ADMIN_ENTITY_ROUTE_CONTRACT.users}/stats`
} as const;

export function buildAdminBannersListPath(page = 0, size = 20) {
  return buildPagedPath(ADMIN_ENTITY_ROUTE_CONTRACT.banners, page, size);
}

export function buildAdminBannerDetailPath(id: AdminEntityRouteId) {
  return buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.banners, id);
}



export function buildAdminGroupsListPath(page = 0, size = 20) {
  return buildPagedPath(ADMIN_ENTITY_ROUTE_CONTRACT.groups, page, size);
}

export function buildAdminGroupDetailPath(id: AdminEntityRouteId) {
  return buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.groups, id);
}

export function buildAdminMessageRestorePath(parentId: AdminEntityRouteId) {
  return `${buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.messages, parentId)}/restore`;
}

export function buildAdminModelsListPath(params: AdminModelsListPathParams = {}) {
  const query = new URLSearchParams();
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  if (params.keyword) query.set("keyword", params.keyword);
  return `${ADMIN_ENTITY_ROUTE_CONTRACT.models}?${query.toString()}`;
}

export function buildAdminModelDetailPath(modelId: AdminEntityRouteId) {
  return buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.models, modelId);
}

export function buildAdminAgentsListPath(page = 0, size = 20, scope?: string, category?: string) {
  const query = new URLSearchParams();
  query.set("page", String(page));
  query.set("size", String(size));
  if (scope) query.set("scope", scope);
  if (category) query.set("category", category);
  return `${ADMIN_ENTITY_ROUTE_CONTRACT.agents}?${query.toString()}`;
}

export function buildAdminAgentDetailPath(id: AdminEntityRouteId) {
  return buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.agents, id);
}

export function buildAdminUsersListPath(params: AdminUsersListPathParams = {}) {
  const query = new URLSearchParams();
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  if (params.search) query.set("search", params.search);
  if (params.roleFilter) query.set("roleFilter", params.roleFilter);
  return `${ADMIN_ENTITY_ROUTE_CONTRACT.users}?${query.toString()}`;
}

export function buildAdminUserDetailPath(userId: AdminEntityRouteId) {
  return buildDetailPath(ADMIN_ENTITY_ROUTE_CONTRACT.users, userId);
}
