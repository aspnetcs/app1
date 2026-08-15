import { buildAdminApiPath } from "./authRouteContract";
import { apiFetch } from "./http";

export type KnowledgeAdminConfig = {
  enabled: boolean;
  maxBasesPerUser: number;
  maxChunksPerDocument: number;
};

export type KnowledgeBaseSummary = {
  id: string;
  name: string;
  status: string;
  description?: string;
  documentCount: number;
  chunkCount: number;
  retrievalMode?: string;
  usageMode?: string;
  aiUsageInstruction?: string;
  lastIngestAt?: string | null;
  updatedAt?: string | null;
};

export type KnowledgeIngestJob = {
  id: string;
  baseId?: string;
  baseName?: string;
  status: string;
  phase?: string;
  totalDocuments?: number;
  processedDocuments?: number;
  errorMessage?: string;
  updatedAt?: string | null;
};

const KNOWLEDGE_CONFIG_PATH = buildAdminApiPath("knowledge/config");
const KNOWLEDGE_BASES_PATH = buildAdminApiPath("knowledge/bases");
const KNOWLEDGE_JOBS_PATH = buildAdminApiPath("knowledge/jobs");

type KnowledgeItemsResponse<T> = {
  items?: T[];
};

const DEFAULT_KNOWLEDGE_ADMIN_CONFIG: KnowledgeAdminConfig = {
  enabled: true,
  maxBasesPerUser: 12,
  maxChunksPerDocument: 200,
};

function toPositiveInteger(value: unknown, fallback: number) {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.trunc(value);
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number.parseInt(value.trim(), 10);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
  }
  return fallback;
}

function normalizeKnowledgeAdminConfig(payload: Partial<KnowledgeAdminConfig> | null | undefined): KnowledgeAdminConfig {
  return {
    enabled: typeof payload?.enabled === "boolean" ? payload.enabled : DEFAULT_KNOWLEDGE_ADMIN_CONFIG.enabled,
    maxBasesPerUser: toPositiveInteger(payload?.maxBasesPerUser, DEFAULT_KNOWLEDGE_ADMIN_CONFIG.maxBasesPerUser),
    maxChunksPerDocument: toPositiveInteger(
      payload?.maxChunksPerDocument,
      DEFAULT_KNOWLEDGE_ADMIN_CONFIG.maxChunksPerDocument
    ),
  };
}

function unwrapKnowledgeItems<T>(payload: T[] | KnowledgeItemsResponse<T> | null | undefined): T[] {
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload?.items)) {
    return payload.items;
  }
  return [];
}

export async function getKnowledgeAdminConfig() {
  const payload = await apiFetch<Partial<KnowledgeAdminConfig>>(KNOWLEDGE_CONFIG_PATH);
  return normalizeKnowledgeAdminConfig(payload);
}

export async function saveKnowledgeAdminConfig(payload: KnowledgeAdminConfig) {
  const response = await apiFetch<Partial<KnowledgeAdminConfig>>(KNOWLEDGE_CONFIG_PATH, {
    method: "PUT",
    body: JSON.stringify(normalizeKnowledgeAdminConfig(payload)),
  });
  return normalizeKnowledgeAdminConfig(response);
}

export async function listKnowledgeBases() {
  const payload = await apiFetch<KnowledgeBaseSummary[] | KnowledgeItemsResponse<KnowledgeBaseSummary>>(KNOWLEDGE_BASES_PATH);
  return unwrapKnowledgeItems(payload);
}

export async function listKnowledgeIngestJobs() {
  const payload = await apiFetch<KnowledgeIngestJob[] | KnowledgeItemsResponse<KnowledgeIngestJob>>(KNOWLEDGE_JOBS_PATH);
  return unwrapKnowledgeItems(payload);
}
