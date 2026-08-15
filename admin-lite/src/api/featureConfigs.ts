import { buildAdminApiPath } from "./authRouteContract";
import { apiFetch } from "./http";

export function buildAdminFeatureConfigPath(featureKey: string) {
  return `${buildAdminApiPath(`features/${encodeURIComponent(featureKey)}/config`)}`;
}

export function getFeatureConfig(featureKey: string) {
  return apiFetch<Record<string, unknown>>(buildAdminFeatureConfigPath(featureKey));
}

export function updateFeatureConfig(featureKey: string, payload: Record<string, unknown>) {
  return apiFetch<Record<string, unknown>>(buildAdminFeatureConfigPath(featureKey), {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}
