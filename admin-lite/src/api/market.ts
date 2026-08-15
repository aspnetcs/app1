import { buildAdminApiPath } from "./authRouteContract";
import { apiFetch } from "./http";

export type MarketAssetType = "AGENT" | "KNOWLEDGE" | "MCP" | "SKILL";

export type MarketCatalogItem = {
  id: string;
  assetType: MarketAssetType;
  sourceId: string;
  title: string;
  summary?: string;
  description?: string;
  category?: string;
  tags?: string;
  tagList?: string[];
  cover?: string;
  featured: boolean;
  enabled: boolean;
  sortOrder: number;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type MarketCatalogResponse = {
  items: MarketCatalogItem[];
  total: number;
};

export type MarketCatalogMutationPayload = {
  assetType: MarketAssetType;
  sourceId: string;
  title?: string;
  summary?: string;
  description?: string;
  category?: string;
  tags?: string;
  cover?: string;
  featured?: boolean;
  enabled?: boolean;
  sortOrder?: number;
};

export type MarketSourceOption = {
  assetType: MarketAssetType;
  sourceId: string;
  title: string;
  summary?: string;
  category?: string;
  cover?: string;
  enabled?: boolean;
  available?: boolean;
  contentFormat?: string;
  usageMode?: string;
  aiUsageInstruction?: string;
  source?: Record<string, unknown>;
};

export type ListMarketCatalogParams = {
  assetType?: MarketAssetType;
  keyword?: string;
};

const MARKET_CATALOG_PATH = buildAdminApiPath("market/catalog");
const MARKET_SOURCE_OPTIONS_PATH = buildAdminApiPath("market/source-options");

function buildCatalogPath(params: ListMarketCatalogParams = {}) {
  const query = new URLSearchParams();
  if (params.assetType) {
    query.set("assetType", params.assetType);
  }
  if (params.keyword?.trim()) {
    query.set("keyword", params.keyword.trim());
  }
  const suffix = query.toString();
  return suffix ? `${MARKET_CATALOG_PATH}?${suffix}` : MARKET_CATALOG_PATH;
}

export function listMarketCatalog(params: ListMarketCatalogParams = {}) {
  return apiFetch<MarketCatalogResponse>(buildCatalogPath(params));
}

export function createMarketCatalogItem(payload: MarketCatalogMutationPayload) {
  return apiFetch<MarketCatalogItem>(MARKET_CATALOG_PATH, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateMarketCatalogItem(id: string, payload: Partial<MarketCatalogMutationPayload>) {
  return apiFetch<MarketCatalogItem>(`${MARKET_CATALOG_PATH}/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function listMarketSourceOptions(assetType: MarketAssetType) {
  return apiFetch<MarketSourceOption[]>(
    `${MARKET_SOURCE_OPTIONS_PATH}?assetType=${encodeURIComponent(assetType)}`
  );
}
