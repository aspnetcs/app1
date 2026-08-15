import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ADMIN_AUTH_EXPIRED_EVENT,
  clearAdminToken,
  getAdminToken,
} from "../../api/http";
import { LoginScreen } from "../../layout/LoginScreen";
import { LoadingBlock } from "../../components/LoadingBlock";
import { LiteShell } from "../layout/LiteShell";
import "../styles/lite.css";

export function LiteApp() {
  const navigate = useNavigate();
  // If a token exists, assume authenticated — skip blocking session check to avoid
  // hanging on "正在验证会话..." when admin-api is slow or unreachable.
  // The dashboard's own data fetches (apiFetch) will surface real auth errors.
  const [checking] = useState(false);
  const [authenticated, setAuthenticated] = useState(() => Boolean(getAdminToken()));

  useEffect(() => {
    const handleExpired = () => {
      clearAdminToken();
      setAuthenticated(false);
      navigate("/", { replace: true });
    };
    window.addEventListener(ADMIN_AUTH_EXPIRED_EVENT, handleExpired);
    return () => window.removeEventListener(ADMIN_AUTH_EXPIRED_EVENT, handleExpired);
  }, [navigate]);

  const handleLogin = () => {
    setAuthenticated(true);
  };

  const handleLogout = () => {
    clearAdminToken();
    setAuthenticated(false);
    navigate("/", { replace: true });
  };

  if (checking) {
    return (
      <div className="admin-loading-screen">
        <LoadingBlock label="正在验证会话..." />
      </div>
    );
  }

  if (!authenticated) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  return <LiteShell onLogout={handleLogout} />;
}
