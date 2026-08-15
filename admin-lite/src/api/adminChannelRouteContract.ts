import { buildAdminApiPath } from "./authRouteContract";

export type AdminChannelRouteId = string | number;
export type AdminChannelListPathParams = {
  page?: number;
  size?: number;
  keyword?: string;
};

const ADMIN_CHANNEL_BASE_PATH = buildAdminApiPath("channels");

function encodeAdminChannelRouteId(channelId: AdminChannelRouteId) {
  return encodeURIComponent(String(channelId));
}

export const ADMIN_CHANNEL_ROUTE_CONTRACT = {
  collection: ADMIN_CHANNEL_BASE_PATH,
  testAll: `${ADMIN_CHANNEL_BASE_PATH}/test-all`,
  fetchModels: `${ADMIN_CHANNEL_BASE_PATH}/fetch-models`
} as const;

export function buildAdminChannelListPath(params: AdminChannelListPathParams = {}) {
  const query = new URLSearchParams();
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  if (params.keyword) query.set("keyword", params.keyword);
  return `${ADMIN_CHANNEL_ROUTE_CONTRACT.collection}?${query.toString()}`;
}

export function buildAdminChannelDetailPath(channelId: AdminChannelRouteId) {
  return `${ADMIN_CHANNEL_ROUTE_CONTRACT.collection}/${encodeAdminChannelRouteId(channelId)}`;
}

export function buildAdminChannelStatusPath(channelId: AdminChannelRouteId) {
  return `${buildAdminChannelDetailPath(channelId)}/status`;
}

export function buildAdminChannelTestPath(channelId: AdminChannelRouteId) {
  return `${buildAdminChannelDetailPath(channelId)}/test`;
}

export function buildAdminChannelUsagePath(hours = 24) {
  return `${ADMIN_CHANNEL_ROUTE_CONTRACT.collection}/usage?hours=${encodeURIComponent(String(hours))}`;
}

export function buildAdminChannelKeysPath(channelId: AdminChannelRouteId) {
  return `${buildAdminChannelDetailPath(channelId)}/keys`;
}
