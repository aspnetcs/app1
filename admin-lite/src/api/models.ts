import { apiFetch } from "./http";
import {
  ADMIN_ENTITY_ROUTE_ENDPOINTS,
  buildAdminModelDetailPath,
  buildAdminModelsListPath
} from "./adminEntityRouteContract";
import { asBoolean, asNumber, asString, extractArrayPayload, invalidPayloadError, isRecord, type RawRecord } from "./apiPayloadUtils";

export type ModelItem = {
  id: string;
  name: string;
  provider?: string;
  enabled?: boolean;
  sortOrder?: number;
  contextWindow?: number;
  avatar?: string;
  description?: string;
  pinned?: boolean;
  defaultSelected?: boolean;
  multiChatEnabled?: boolean;
  billingEnabled?: boolean;
  requestPriceUsd?: number | null;
  promptPriceUsd?: number | null;
  inputPriceUsdPer1M?: number | null;
  outputPriceUsdPer1M?: number | null;
  supportsImageParsing?: boolean;
  supportsImageParsingSource?: "manual" | "inferred" | "unknown";
  imageParsingOverride?: boolean | null;
};

export type ModelsResponse = {
  items: ModelItem[];
  total: number;
  page: number;
  size: number;
};

export type ModelPayload = Record<string, unknown>;

function normalizeModelItem(raw: unknown): ModelItem | null {
  if (!isRecord(raw)) return null;

  const id = asString(raw.id) ?? asString(raw.modelId) ?? asString(raw.model_id);
  if (!id) return null;
  const supportsImageParsingSource = asString(raw.supportsImageParsingSource);

  const normalized: ModelItem & RawRecord = {
    ...raw,
    id,
    name: asString(raw.name) ?? id,
    provider: asString(raw.provider),
    enabled: asBoolean(raw.enabled),
    sortOrder: asNumber(raw.sortOrder) ?? asNumber(raw.sort_order),
    contextWindow: asNumber(raw.contextWindow) ?? asNumber(raw.context_window),
    avatar: asString(raw.avatar),
    description: asString(raw.description),
    pinned: asBoolean(raw.pinned),
    defaultSelected: asBoolean(raw.defaultSelected),
    multiChatEnabled: asBoolean(raw.multiChatEnabled),
    billingEnabled: asBoolean(raw.billingEnabled),
    requestPriceUsd: asNumber(raw.requestPriceUsd),
    promptPriceUsd: asNumber(raw.promptPriceUsd),
    inputPriceUsdPer1M: asNumber(raw.inputPriceUsdPer1M),
    outputPriceUsdPer1M: asNumber(raw.outputPriceUsdPer1M),
    supportsImageParsing: asBoolean(raw.supportsImageParsing),
    supportsImageParsingSource:
      supportsImageParsingSource === "manual" ||
      supportsImageParsingSource === "inferred" ||
      supportsImageParsingSource === "unknown"
        ? supportsImageParsingSource
        : undefined,
    imageParsingOverride: raw.imageParsingOverride == null ? null : asBoolean(raw.imageParsingOverride)
  };
  return normalized;
}

function normalizeModelsResponse(raw: unknown, fallback: { page?: number; size?: number } = {}): ModelsResponse {
  const items = extractArrayPayload(raw);
  if (!items) {
    throw invalidPayloadError("model list", "expected array");
  }

  const normalizedItems = items.map((item, index) => {
    const normalizedItem = normalizeModelItem(item);
    if (!normalizedItem) {
      throw invalidPayloadError("model list", `item ${index} is malformed`);
    }
    return normalizedItem;
  });

  const rawEnvelope = isRecord(raw) ? raw : null;
  const rawDataEnvelope = rawEnvelope && isRecord(rawEnvelope.data) ? rawEnvelope.data : null;
  const total = asNumber(rawEnvelope?.total) ?? asNumber(rawDataEnvelope?.total) ?? normalizedItems.length;
  const page = asNumber(rawEnvelope?.page) ?? asNumber(rawDataEnvelope?.page) ?? fallback.page ?? 0;
  const sizeCandidate =
    asNumber(rawEnvelope?.size) ?? asNumber(rawDataEnvelope?.size) ?? fallback.size ?? normalizedItems.length;
  const size = sizeCandidate && sizeCandidate > 0 ? sizeCandidate : 20;

  return {
    items: normalizedItems,
    total,
    page,
    size
  };
}

function unwrapModelPayload(raw: unknown, scope: string) {
  const item = normalizeModelItem(raw);
  if (!item) {
    throw invalidPayloadError(scope, "expected object");
  }
  return item;
}

export function listModels(params: { page?: number; size?: number; keyword?: string } = {}) {
  return apiFetch<unknown>(buildAdminModelsListPath(params)).then((payload) =>
    normalizeModelsResponse(payload, { page: params.page, size: params.size })
  );
}

export function updateModel(modelId: string, payload: ModelPayload) {
  return apiFetch<unknown>(buildAdminModelDetailPath(modelId), {
    method: "PUT",
    body: JSON.stringify(payload)
  }).then((raw) => unwrapModelPayload(raw, "model update"));
}

export function deleteModel(modelId: string) {
  return apiFetch<null>(buildAdminModelDetailPath(modelId), {
    method: "DELETE"
  });
}

export function reorderModels(orderedIds: string[]) {
  return apiFetch<null>(ADMIN_ENTITY_ROUTE_ENDPOINTS.modelsReorder, {
    method: "POST",
    body: JSON.stringify({ modelIds: orderedIds })
  });
}

export type ModelTestResult = {
  ok: boolean;
  latencyMs: number;
  statusCode?: number;
  responseText?: string;
  channelId?: number;
  keyId?: number;
  url?: string;
  errorCode?: string;
  errorMessage?: string;
};

export function testModelConnectivity(modelId: string): Promise<ModelTestResult> {
  return apiFetch<ModelTestResult>(`${buildAdminModelDetailPath(modelId)}/test`, {
    method: "POST"
  });
}
