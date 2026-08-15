import { ADMIN_AUTH_ROUTE_CONTRACT, ADMIN_RISK_ROUTE_CONTRACT } from "../api/authRouteContract";

type LoginResponse = {
  accessToken?: string;
  token?: string;
  access_token?: string;
};

type ApiEnvelope<T> = {
  code?: number;
  message?: string;
  data?: T;
};

type CaptchaGenerateResponse = {
  captchaId?: string;
  type?: string;
  question?: string;
};

type CaptchaVerifyResponse = {
  token?: string;
};

const AUTO_CAPTCHA_ATTEMPTS = 8;

export type OAuthProvider = {
  provider: string;
  displayName?: string;
};

export function normalizeLocalHttpBase(raw: string | null): string | null {
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

export function getAuthApiBase(adminApiBase: string) {
  const params = new URLSearchParams(window.location.search);
  const fromQuery = normalizeLocalHttpBase(params.get("authApiBase"));
  if (fromQuery) return fromQuery;

  if (/^https?:\/\/(localhost|127\.0\.0\.1):8081$/i.test(adminApiBase)) {
    return adminApiBase.replace(/:8081$/i, ":8080");
  }
  if (/^https?:\/\/(localhost|127\.0\.0\.1):5\d{3}$/i.test(adminApiBase)) {
    return "http://localhost:8080";
  }
  return adminApiBase;
}

export function parseMathQuestion(question: string): number | null {
  const match = question.match(/(-?\d+)\s*([+\-*xX\u00d7\xd7])\s*(-?\d+)/);
  if (!match) return null;

  const left = Number(match[1]);
  const operator = match[2];
  const right = Number(match[3]);
  if (!Number.isFinite(left) || !Number.isFinite(right)) return null;

  if (operator === "+") return left + right;
  if (operator === "-") return left - right;
  return left * right;
}

export function extractToken(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const data = payload as LoginResponse;
  const token = data.accessToken || data.token || data.access_token;
  return token && token.trim() ? token.trim() : null;
}

const REQUEST_TIMEOUT_MS = 10000;

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers || {});
  if (!headers.has("Content-Type") && init?.body) {
    headers.set("Content-Type", "application/json");
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers, signal: controller.signal });
  } catch (err) {
    clearTimeout(timer);
    if (err instanceof DOMException && err.name === "AbortError") {
      throw new Error(`请求超时，请检查后端服务是否已启动: ${url}`);
    }
    throw new Error(`无法连接到后端服务: ${url}`);
  }
  clearTimeout(timer);
  const text = await response.text();
  let parsed: unknown = null;

  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      throw new Error(`Unexpected non-JSON response from ${url}`);
    }
  }

  if (response.status >= 400) {
    if (parsed && typeof parsed === "object" && "message" in (parsed as Record<string, unknown>)) {
      throw new Error(String((parsed as Record<string, unknown>).message || "Request failed"));
    }
    throw new Error(`HTTP ${response.status}`);
  }

  if (parsed && typeof parsed === "object" && "code" in (parsed as Record<string, unknown>)) {
    const envelope = parsed as ApiEnvelope<T>;
    if (envelope.code !== 0 && envelope.code !== 200) {
      throw new Error(envelope.message || "Request failed");
    }
    return (envelope.data ?? null) as T;
  }

  return parsed as T;
}

export async function issueChallengeToken(authApiBase: string): Promise<string | null> {
  for (let i = 0; i < AUTO_CAPTCHA_ATTEMPTS; i += 1) {
    try {
      const captcha = await requestJson<CaptchaGenerateResponse>(
        `${authApiBase}${ADMIN_RISK_ROUTE_CONTRACT.captchaGenerate}`
      );
      const captchaId = captcha?.captchaId;
      if (!captchaId || captcha.type !== "math") continue;

      const answer = parseMathQuestion(captcha.question || "");
      if (answer == null) continue;

      const verified = await requestJson<CaptchaVerifyResponse>(
        `${authApiBase}${ADMIN_RISK_ROUTE_CONTRACT.captchaVerify}`,
        {
          method: "POST",
          body: JSON.stringify({ captchaId, data: { answer } })
        }
      );
      if (verified?.token) return verified.token;
    } catch {
      continue;
    }
  }

  return null;
}

export function clearOAuthParams(url: URL) {
  ["oauthTicket", "oauthError", "oauthMessage", "provider"].forEach((key) => url.searchParams.delete(key));
  window.history.replaceState(null, document.title, url.toString());
}

export function buildOAuthStartUrl(authApiBase: string, provider: string) {
  return `${authApiBase}${ADMIN_AUTH_ROUTE_CONTRACT.buildOAuthStartPath(provider)}`;
}

export function toFriendlyError(err: unknown) {
  const message = (err instanceof Error ? err.message : String(err || "")).trim();

  if (
    message.includes("Unexpected token '<'") ||
    message.includes("<!DOCTYPE") ||
    message.includes("non-JSON response")
  ) {
    return "API base \u5f53\u524d\u8fd4\u56de\u7684\u662f\u524d\u7aef HTML\uff0c\u8bf7\u786e\u8ba4\u540e\u7aef\u5df2\u542f\u52a8\u3002admin-api \u9ed8\u8ba4 8081\uff0cplatform-api \u9ed8\u8ba4 8080\u3002";
  }
  if (message.includes("Failed to fetch") || message.startsWith("HTTP 5")) {
    return "\u65e0\u6cd5\u8fde\u63a5\u5230\u540e\u7aef API\uff0c\u8bf7\u786e\u8ba4 admin-api (8081) \u548c platform-api (8080) \u90fd\u5df2\u542f\u52a8\u3002";
  }
  if (/captcha|challenge/i.test(message)) {
    return "验证码验证失败，请确认后端服务已启动（Redis + platform-api）。";
  }
  if (/wrong password|unauthorized|admin required|admin only|\u6743\u9650|\u5bc6\u7801/i.test(message)) {
    return "\u767b\u5f55\u5931\u8d25\uff1a\u8d26\u53f7\u6216\u5bc6\u7801\u9519\u8bef\uff0c\u6216\u5f53\u524d\u8d26\u53f7\u6ca1\u6709\u7ba1\u7406\u5458\u6743\u9650\u3002";
  }
  return message || "\u767b\u5f55\u5931\u8d25";
}
