import { buildAdminApiPath } from "./authRouteContract";
import { apiFetch } from "./http";

export type MemoryAdminConfig = {
  enabled: boolean;
  requireConsent: boolean;
  maxEntriesPerUser: number;
  maxCharsPerEntry: number;
  retentionDays: number;
  summaryModel: string;
};

export type MemoryAdminStats = {
  enabled: boolean;
  totalUsers: number;
  totalEntries: number;
  pendingReviews: number;
  averageEntriesPerUser: number;
};

export type MemoryAuditEntry = {
  id: string;
  userId: string;
  action: string;
  summary: string;
  status: string;
  createdAt?: string | null;
};

export type MemoryAuditPage = {
  items: MemoryAuditEntry[];
  total: number;
  page: number;
  size: number;
};

const MEMORY_CONFIG_PATH = buildAdminApiPath("memory/config");
const MEMORY_STATS_PATH = buildAdminApiPath("memory/stats");
const MEMORY_AUDITS_PATH = buildAdminApiPath("memory/audits");

export function getMemoryAdminConfig() {
  return apiFetch<MemoryAdminConfig>(MEMORY_CONFIG_PATH);
}

export function saveMemoryAdminConfig(payload: MemoryAdminConfig) {
  return apiFetch<MemoryAdminConfig>(MEMORY_CONFIG_PATH, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function getMemoryAdminStats() {
  return apiFetch<MemoryAdminStats>(MEMORY_STATS_PATH);
}

export function getMemoryAuditPage(page = 0, size = 20) {
  return apiFetch<MemoryAuditPage>(`${MEMORY_AUDITS_PATH}?page=${page}&size=${size}`);
}
