import { buildAdminApiPath } from "./authRouteContract";

export type AdminAuditLogsPathParams = {
  page?: number;
  size?: number;
  action?: string;
  userIdFilter?: string;
  hours?: string;
};

export const ADMIN_CORE_ROUTE_CONTRACT = {
  dashboardOverview: buildAdminApiPath("dashboard/overview"),
  systemInfo: buildAdminApiPath("system/info"),
  auditStats: buildAdminApiPath("audit/stats"),
  auditActions: buildAdminApiPath("audit/actions")
} as const;

export function buildAdminAuditLogsPath(params: AdminAuditLogsPathParams = {}) {
  const query = new URLSearchParams();
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  if (params.action) query.set("action", params.action);
  if (params.userIdFilter) query.set("userIdFilter", params.userIdFilter);
  query.set("hours", params.hours ?? "24");
  return `${buildAdminApiPath("audit/logs")}?${query.toString()}`;
}
