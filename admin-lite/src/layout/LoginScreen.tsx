import { type FormEvent, useMemo, useState } from "react";
import { ADMIN_AUTH_ROUTE_CONTRACT } from "../api/authRouteContract";
import { getApiBase, setAdminToken } from "../api/http";
import { LoginMetaPanel } from "./LoginMetaPanel";
import { LoginPasswordForm } from "./LoginPasswordForm";
import {
  extractToken,
  getAuthApiBase,
  requestJson,
  toFriendlyError,
} from "./loginScreenShared";

export function LoginScreen({ onLogin }: { onLogin: () => void }) {
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const adminApiBase = getApiBase();
  const authApiBase = useMemo(() => getAuthApiBase(adminApiBase), [adminApiBase]);
  const likelyFrontendBase = /^https?:\/\/(localhost|127\.0\.0\.1):5\d{3}$/i.test(adminApiBase);
  const passwordDisabled = useMemo(
    () => !identifier.trim() || !password || loading,
    [identifier, password, loading]
  );

  async function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault();
    if (passwordDisabled) return;

    setLoading(true);
    setError("");
    try {
      // Admin login does not require captcha - skip challenge token to avoid IP mismatch issues
      const payload: Record<string, string> = {
        identifier: identifier.trim(),
        password,
      };
      const data = await requestJson(`${authApiBase}${ADMIN_AUTH_ROUTE_CONTRACT.adminLogin}`, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      const nextToken = extractToken(data);
      if (!nextToken) {
        throw new Error("Login succeeded but no JWT token was returned.");
      }
      setAdminToken(nextToken);
      // Login succeeded - proceed directly without waiting for session validation.
      // The dashboard will fetch data via apiFetch which carries the token.
      onLogin();
    } catch (err) {
      setAdminToken("");
      setError(toFriendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-panel">
        <div className="login-kicker">{"\u7ba1\u7406\u540e\u53f0"}</div>
        <h1>{"\u7ba1\u7406\u5458\u767b\u5f55"}</h1>
        <p>{"\u8bf7\u4f7f\u7528\u8d26\u53f7\u5bc6\u7801\u767b\u5f55\u3002"}</p>

        <LoginMetaPanel
          adminApiBase={adminApiBase}
          authApiBase={authApiBase}
          likelyFrontendBase={likelyFrontendBase}
        />

        <LoginPasswordForm
          identifier={identifier}
          password={password}
          disabled={passwordDisabled}
          loading={loading}
          onIdentifierChange={setIdentifier}
          onPasswordChange={setPassword}
          onSubmit={handlePasswordSubmit}
        />

        {error ? <div className="login-error">{error}</div> : null}
      </div>
    </div>
  );
}
