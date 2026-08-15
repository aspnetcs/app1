import { useLocation, useNavigate } from "react-router-dom";
import {
  Bot,
  Brain,
  Database,
  FlaskConical,
  GitBranch,
  Languages,
  LayoutDashboard,
  LogOut,
  Plug,
} from "lucide-react";

type NavItem = {
  key: string;
  label: string;
  path: string;
  icon: React.ReactNode;
};

const NAV_ITEMS: NavItem[] = [
  { key: "home", label: "总览", path: "/lite", icon: <LayoutDashboard size={16} /> },
  { key: "agents", label: "智能体", path: "/lite/agents", icon: <Bot size={16} /> },
  { key: "knowledge", label: "知识库", path: "/lite/knowledge", icon: <Database size={16} /> },
  { key: "mcp", label: "MCP", path: "/lite/mcp", icon: <Plug size={16} /> },
  { key: "memory", label: "记忆库", path: "/lite/memory", icon: <Brain size={16} /> },
  { key: "channels", label: "渠道", path: "/lite/channels", icon: <GitBranch size={16} /> },
  { key: "translation", label: "翻译", path: "/lite/translation", icon: <Languages size={16} /> },
  { key: "research", label: "科研助理", path: "/lite/research", icon: <FlaskConical size={16} /> },
];

type LiteNavProps = {
  onLogout: () => void;
};

function resolveLegacyAdminHref() {
  const configured = import.meta.env.VITE_LEGACY_ADMIN_URL?.trim();
  if (configured) {
    return configured;
  }

  const { protocol, hostname, port } = window.location;
  if (/^(localhost|127\.0\.0\.1)$/i.test(hostname)) {
    if (port === "5175") {
      return `${protocol}//${hostname}:5174/#/`;
    }
    if (port === "4175") {
      return `${protocol}//${hostname}:4174/#/`;
    }
  }

  return "/#/";
}

export function LiteNav({ onLogout }: LiteNavProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const legacyAdminHref = resolveLegacyAdminHref();

  function isActive(item: NavItem) {
    if (item.key === "home") {
      return location.pathname === "/lite" || location.pathname === "/lite/";
    }
    return location.pathname.startsWith(item.path);
  }

  return (
    <nav className="lite-nav">
      <div className="lite-nav-brand">
        <div className="text-sm font-semibold text-gray-800 dark:text-gray-100">Admin Lite</div>
        <div className="mt-0.5 text-xs text-gray-400">轻量工作台</div>
      </div>

      <div className="lite-nav-items">
        {NAV_ITEMS.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`lite-nav-item ${isActive(item) ? "active" : ""}`}
            onClick={() => navigate(item.path)}
          >
            {item.icon}
            <span>{item.label}</span>
          </button>
        ))}
      </div>

      <div className="lite-nav-footer">
        <button
          type="button"
          className="lite-nav-item w-full text-gray-500 dark:text-gray-400"
          onClick={onLogout}
        >
          <LogOut size={15} />
          <span>退出登录</span>
        </button>
        <div className="mt-2 px-2">
          <a href={legacyAdminHref} className="text-xs text-blue-500 hover:underline">
            返回旧版管理端
          </a>
        </div>
      </div>
    </nav>
  );
}
