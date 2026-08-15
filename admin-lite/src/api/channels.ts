import { apiFetch } from "./http";
import {
  ADMIN_CHANNEL_ROUTE_CONTRACT,
  buildAdminChannelListPath,
  buildAdminChannelDetailPath,
  buildAdminChannelKeysPath,
  buildAdminChannelStatusPath,
  buildAdminChannelTestPath,
  buildAdminChannelUsagePath,
  type AdminChannelRouteId
} from "./adminChannelRouteContract";
import {
  normalizeFetchModelsResult,
  normalizeKeysPayload,
  normalizeListResponse,
  normalizeTestAllPayload,
  normalizeTestResult,
  normalizeUsagePayload,
  unwrapChannelPayload
} from "./channelPayloadUtils";

export type ChannelItem = {
  id: string;
  name: string;
  type?: string;
  baseUrl?: string;
  models?: string;
  testModel?: string;
  priority?: number;
  weight?: number;
  maxConcurrent?: number;
  keyCount?: number;
  enabled?: boolean;
  status?: string | number;
  modelCount?: number;
};

export type ChannelsResponse = {
  items: ChannelItem[];
  total: number;
  page: number;
  size: number;
};

export type ChannelPayload = Record<string, unknown>;

export type ChannelUsage = {
  from?: string;
  to?: string;
  hours?: number;
  total?: number;
  done?: number;
  error?: number;
  channels?: Array<Record<string, unknown>>;
  [key: string]: unknown;
};

export type ChannelKeyItem = {
  id?: string | number;
  keyHash?: string;
  enabled?: boolean;
  status?: string | number;
  weight?: number;
  consecutiveFailures?: number;
  successCount?: number;
  failCount?: number;
  lastSuccessAt?: string;
  lastFailAt?: string;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: unknown;
};

export type ChannelKeysConfig = ChannelKeyItem[];

export type ChannelTestResult = {
  success: boolean;
  latencyMs?: number;
  message?: string;
};

export async function listChannels(params: { page?: number; size?: number; keyword?: string } = {}) {
  const payload = await apiFetch<unknown>(buildAdminChannelListPath(params));
  return normalizeListResponse(payload, params);
}

export function createChannel(payload: ChannelPayload) {
  return apiFetch<unknown>(ADMIN_CHANNEL_ROUTE_CONTRACT.collection, {
    method: "POST",
    body: JSON.stringify(payload)
  }).then(unwrapChannelPayload);
}

export function updateChannel(channelId: AdminChannelRouteId, payload: ChannelPayload) {
  return apiFetch<unknown>(buildAdminChannelDetailPath(channelId), {
    method: "PUT",
    body: JSON.stringify(payload)
  }).then(unwrapChannelPayload);
}

export function deleteChannel(channelId: AdminChannelRouteId) {
  return apiFetch<null>(buildAdminChannelDetailPath(channelId), {
    method: "DELETE"
  });
}

export function updateChannelStatus(channelId: AdminChannelRouteId, enabled: boolean) {
  return apiFetch<unknown>(buildAdminChannelStatusPath(channelId), {
    method: "PUT",
    body: JSON.stringify({ enabled })
  }).then(unwrapChannelPayload);
}

export function testChannel(channelId: AdminChannelRouteId) {
  return apiFetch<unknown>(buildAdminChannelTestPath(channelId), {
    method: "POST"
  }).then(normalizeTestResult);
}

export function testAllChannels() {
  return apiFetch<unknown>(ADMIN_CHANNEL_ROUTE_CONTRACT.testAll, {
    method: "POST"
  }).then(normalizeTestAllPayload);
}

export function getChannelUsage(hours = 24) {
  return apiFetch<unknown>(buildAdminChannelUsagePath(hours)).then(normalizeUsagePayload);
}

export function getChannelKeys(channelId: AdminChannelRouteId) {
  return apiFetch<unknown>(buildAdminChannelKeysPath(channelId)).then(normalizeKeysPayload);
}

export type FetchModelsPayload = {
  base_url: string;
  api_key?: string;
  channel_id?: string | number;
};

export type FetchModelsResult = {
  models: string[];
  count: number;
};

export function fetchUpstreamModels(payload: FetchModelsPayload): Promise<FetchModelsResult> {
  return apiFetch<unknown>(ADMIN_CHANNEL_ROUTE_CONTRACT.fetchModels, {
    method: "POST",
    body: JSON.stringify(payload)
  }).then(normalizeFetchModelsResult);
}
