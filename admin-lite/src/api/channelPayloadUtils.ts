import { asBoolean, asNumber, asString, extractArrayPayload, invalidPayloadError, isRecord, type RawRecord } from "./apiPayloadUtils";
import type {
  ChannelItem,
  ChannelKeysConfig,
  ChannelsResponse,
  ChannelTestResult,
  ChannelUsage,
  FetchModelsResult
} from "./channels";
import { logger } from "../utils/logger";

type RawChannelMutationPayload = {
  channel?: unknown;
  [key: string]: unknown;
};

type RawChannelTestAllPayload = {
  results?: unknown;
  [key: string]: unknown;
};

function inferModelCount(raw: RawRecord): number | undefined {
  const direct = asNumber(raw.modelCount);
  if (direct !== undefined) return direct;

  const models = raw.models;
  if (Array.isArray(models)) return models.length;
  if (typeof models === "string") {
    return models
      .split(/[,\n]/)
      .map((item) => item.trim())
      .filter(Boolean).length;
  }
  return undefined;
}

function normalizeModelsValue(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    const items = value.map((item) => asString(item)).filter(Boolean);
    return items.length > 0 ? items.join(",") : undefined;
  }
  return asString(value);
}

export function normalizeChannelItem(raw: unknown): ChannelItem | null {
  if (!isRecord(raw)) return null;

  const id = asString(raw.id);
  if (!id) return null;

  return {
    ...raw,
    id,
    name: asString(raw.name) ?? id,
    type: asString(raw.type),
    baseUrl: asString(raw.baseUrl) ?? asString(raw.base_url),
    models: normalizeModelsValue(raw.models),
    testModel: asString(raw.testModel) ?? asString(raw.test_model),
    priority: asNumber(raw.priority),
    weight: asNumber(raw.weight),
    maxConcurrent: asNumber(raw.maxConcurrent) ?? asNumber(raw.max_concurrent),
    keyCount: asNumber(raw.keyCount) ?? asNumber(raw.key_count),
    enabled: asBoolean(raw.enabled),
    status: typeof raw.status === "number" || typeof raw.status === "string" ? raw.status : undefined,
    modelCount: inferModelCount(raw)
  };
}

export function normalizeListResponse(
  raw: unknown,
  params: { page?: number; size?: number; keyword?: string }
): ChannelsResponse {
  const items = extractArrayPayload(raw);
  if (!items) {
    logger.warn("[channels] unexpected list payload:", typeof raw, raw);
    throw invalidPayloadError("channel list", "expected array");
  }

  const normalized = items.map((item, index) => {
    const normalizedItem = normalizeChannelItem(item);
    if (!normalizedItem) {
      throw invalidPayloadError("channel list", `item ${index} is malformed`);
    }
    return normalizedItem;
  });

  const keyword = (params.keyword ?? "").trim().toLowerCase();
  const filtered = keyword
    ? normalized.filter((item) => {
        const haystack = `${item.id} ${item.name ?? ""} ${item.type ?? ""} ${item.status ?? ""}`.toLowerCase();
        return haystack.includes(keyword);
      })
    : normalized;

  const rawEnvelope = isRecord(raw) ? raw : null;
  const rawDataEnvelope = rawEnvelope && isRecord(rawEnvelope.data) ? rawEnvelope.data : null;
  const upstreamTotal = asNumber(rawEnvelope?.total) ?? asNumber(rawDataEnvelope?.total);
  const upstreamPage = asNumber(rawEnvelope?.page) ?? asNumber(rawDataEnvelope?.page);
  const upstreamSize = asNumber(rawEnvelope?.size) ?? asNumber(rawDataEnvelope?.size);
  const hasUpstreamPagination = upstreamTotal !== undefined || upstreamPage !== undefined || upstreamSize !== undefined;

  if (hasUpstreamPagination) {
    const resolvedSize = upstreamSize ?? params.size ?? normalized.length;
    return {
      items: normalized,
      total: Math.max(normalized.length, upstreamTotal ?? normalized.length),
      page: Math.max(0, Math.floor(upstreamPage ?? params.page ?? 0)),
      size: Math.max(1, Math.floor(resolvedSize))
    };
  }

  const size = Math.max(1, Math.floor(upstreamSize ?? params.size ?? 20));
  const requestedPage = Math.max(0, Math.floor(upstreamPage ?? params.page ?? 0));
  const total = keyword ? filtered.length : Math.max(filtered.length, upstreamTotal ?? filtered.length);
  const maxPage = total > 0 ? Math.max(0, Math.ceil(total / size) - 1) : 0;
  const page = Math.min(requestedPage, maxPage);
  const start = page * size;

  return {
    items: filtered.slice(start, start + size),
    total,
    page,
    size
  };
}

export function unwrapChannelPayload(raw: unknown): ChannelItem {
  const envelope = isRecord(raw) ? (raw as RawChannelMutationPayload) : {};
  const candidate = "channel" in envelope ? envelope.channel : envelope;
  const item = normalizeChannelItem(candidate);
  if (!item) {
    throw new Error("Invalid channel payload");
  }
  return item;
}

export function normalizeTestResult(raw: unknown, scope = "channel test"): ChannelTestResult {
  if (!isRecord(raw)) {
    throw invalidPayloadError(scope, "expected object");
  }
  const success = asBoolean(raw.ok) ?? asBoolean(raw.success);
  if (success === undefined) {
    throw invalidPayloadError(scope, "missing ok/success boolean");
  }
  return {
    success,
    latencyMs: asNumber(raw.durationMs) ?? asNumber(raw.latencyMs),
    message: asString(raw.message)
  };
}

export function normalizeTestAllPayload(raw: unknown): ChannelTestResult[] {
  if (!isRecord(raw)) {
    throw invalidPayloadError("channel test-all", "expected object");
  }

  const mapped = (raw as RawChannelTestAllPayload).results;
  if (!Array.isArray(mapped)) {
    throw invalidPayloadError("channel test-all", "missing results array");
  }

  return mapped.map((item, index) => normalizeTestResult(item, `channel test-all result ${index}`));
}

export function normalizeKeysPayload(raw: unknown): ChannelKeysConfig {
  if (!Array.isArray(raw)) {
    throw invalidPayloadError("channel keys", "expected array");
  }
  return raw.map((item, index) => {
    if (!isRecord(item)) {
      throw invalidPayloadError("channel keys", `item ${index} is malformed`);
    }
    return item;
  });
}

export function normalizeUsagePayload(raw: unknown): ChannelUsage {
  if (!isRecord(raw)) {
    throw invalidPayloadError("channel usage", "expected object");
  }
  if (!Array.isArray(raw.channels)) {
    throw invalidPayloadError("channel usage", "missing channels array");
  }

  const channels = raw.channels.map((item, index) => {
    if (!isRecord(item)) {
      throw invalidPayloadError("channel usage", `channels[${index}] is malformed`);
    }
    return item;
  });

  return { ...raw, channels };
}

export function normalizeFetchModelsResult(raw: unknown): FetchModelsResult {
  if (!isRecord(raw)) {
    throw new Error("Invalid fetch-models response");
  }
  const models = Array.isArray(raw.models) ? raw.models.map(String) : [];
  return { models, count: models.length };
}
