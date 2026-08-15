export const ADMIN_API_BASE_PATH = "/api/v1/admin";

const AUTH_API_BASE_PATH = "/api/v1/auth";
const RISK_API_BASE_PATH = "/api/v1/risk";

function normalizePath(path: string) {
  return path.trim().replace(/^\/+|\/+$/g, "");
}

function joinPath(basePath: string, path: string) {
  const normalizedPath = normalizePath(path);
  if (!normalizedPath) return basePath;
  return `${basePath}/${normalizedPath}`;
}

export function buildAdminApiPath(path: string) {
  return joinPath(ADMIN_API_BASE_PATH, path);
}

function buildAuthApiPath(path: string) {
  return joinPath(AUTH_API_BASE_PATH, path);
}

function buildRiskApiPath(path: string) {
  return joinPath(RISK_API_BASE_PATH, path);
}

export const ADMIN_AUTH_ROUTE_CONTRACT = {
  oauthProviders: `${buildAuthApiPath("oauth/providers")}?scene=admin`,
  oauthConsumeTicket: buildAuthApiPath("oauth/consume-ticket"),
  passwordLogin: buildAuthApiPath("password/login"),
  adminLogin: buildAuthApiPath("admin/login"),
  buildOAuthStartPath(provider: string) {
    return buildAuthApiPath(`oauth/${encodeURIComponent(provider)}/start`);
  }
} as const;

export const ADMIN_RISK_ROUTE_CONTRACT = {
  captchaGenerate: buildRiskApiPath("captcha/generate"),
  captchaVerify: buildRiskApiPath("captcha/verify")
} as const;
