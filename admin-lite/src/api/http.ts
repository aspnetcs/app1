import { ADMIN_CORE_ROUTE_CONTRACT } from "./adminCoreRouteContract";

export type ApiEnvelope<T> = {
  code?: number;
  message?: string;
  data?: T;
};

export const ADMIN_AUTH_EXPIRED_EVENT = "admin-auth-expired";

const ADMIN_TOKEN_KEY = "admin_token";
const LOCAL_HOSTNAME_PATTERN = /^(localhost|127\.0\.0\.1)$/i;
const DEFAULT_FETCH_TIMEOUT_MS = 10000;

function normalizeApiBase(raw: string | null): string | null {
  if (!raw) return null;
  try {
    const url = new URL(raw, window.location.href);
    const host = (url.hostname || "").toLowerCase();
    const isLocal = host === "localhost" || host === "127.0.0.1";
    if (!isLocal) return null;
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    return url.origin.replace(/\/$/, "");
  } catch {
    return null;
  }
}

export function getApiBase(): string {
  const params = new URLSearchParams(window.location.search);
  if (params.has("apiBase")) {
    const normalized = normalizeApiBase(params.get("apiBase"));
    if (normalized) return normalized;
  }
  if (window.location.protocol === "file:") return "http://localhost:8081";

  const { hostname, origin, port, protocol } = window.location;
  if (!LOCAL_HOSTNAME_PATTERN.test(hostname)) {
    return origin;
  }
  if (port === "8080" || /^5\d{3}$/.test(port)) {
    return "";
  }
  return origin;
}

function parseResponsePayload<T>(text: string, response: Response): ApiEnvelope<T> | T | null {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as ApiEnvelope<T> | T;
  } catch {
    if (!response.ok) {
      throw new Error(text.trim() || `HTTP ${response.status}`);
    }
    return text as T;
  }
}

function extractResponseErrorMessage<T>(payload: ApiEnvelope<T> | T | null, response: Response): string {
  if (payload && typeof payload === "object") {
    if ("message" in payload) {
      const message = (payload as { message?: unknown }).message;
      if (typeof message === "string" && message.trim()) {
        return message.trim();
      }
    }
    if ("error" in payload) {
      const error = (payload as { error?: unknown }).error;
      if (typeof error === "string" && error.trim()) {
        return error.trim();
      }
    }
  }
  return `HTTP ${response.status}`;
}

function notifyAdminSessionExpired(message: string, path: string) {
  clearAdminToken();
  window.dispatchEvent(
    new CustomEvent(ADMIN_AUTH_EXPIRED_EVENT, {
      detail: {
        message,
        path
      }
    })
  );
}

export function getAdminToken(): string {
  return (window.localStorage.getItem(ADMIN_TOKEN_KEY) || "").trim();
}

export function setAdminToken(token: string) {
  const normalized = token.trim();
  if (!normalized) {
    window.localStorage.removeItem(ADMIN_TOKEN_KEY);
    return;
  }
  window.localStorage.setItem(ADMIN_TOKEN_KEY, normalized);
}

export function clearAdminToken() {
  window.localStorage.removeItem(ADMIN_TOKEN_KEY);
}

function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...init, signal: controller.signal }).finally(() => clearTimeout(timer));
}

export async function validateAdminSession() {
  return apiFetch(ADMIN_CORE_ROUTE_CONTRACT.dashboardOverview);
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers || {});
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAdminToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const apiBase = getApiBase();
  let response: Response;
  try {
    response = await fetchWithTimeout(`${apiBase}${path}`, { ...init, headers }, DEFAULT_FETCH_TIMEOUT_MS);
  } catch {
    throw new Error(`管理端接口不可用: ${apiBase}${path}`);
  }

  if (response.status === 401) {
    const message = "管理员登录已失效，请重新登录";
    notifyAdminSessionExpired(message, path);
    throw new Error(message);
  }

  const text = await response.text();
  const payload = parseResponsePayload<T>(text, response);

  if (!response.ok) {
    throw new Error(extractResponseErrorMessage(payload, response));
  }

  if (payload && typeof payload === "object" && "code" in payload) {
    const envelope = payload as ApiEnvelope<T>;
    if (envelope.code === 401) {
      const message = envelope.message?.trim() || "管理员登录已失效，请重新登录";
      notifyAdminSessionExpired(message, path);
      throw new Error(message);
    }
    if (envelope.code !== 0 && envelope.code !== 200) {
      throw new Error(envelope.message || "请求失败");
    }
    return (envelope.data ?? null) as T;
  }

  return payload as T;
}
