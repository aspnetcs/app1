import { useNavigate } from "react-router-dom";
import {
  Bot,
  Brain,
  Database,
  FlaskConical,
  GitBranch,
  Languages,
  LayoutDashboard,
  Plug,
} from "lucide-react";

type ModuleCard = {
  key: string;
  label: string;
  description: string;
  path: string;
  icon: React.ReactNode;
};

const MODULE_CARDS: ModuleCard[] = [
  {
    key: "agents",
    label: "智能体",
    description: "角色配置、提示词和运行资源总览",
    path: "/lite/agents",
    icon: <Bot size={24} />,
  },
  {
    key: "knowledge",
    label: "知识库",
    description: "知识源、导入任务和检索配置管理",
    path: "/lite/knowledge",
    icon: <Database size={24} />,
  },
  {
    key: "mcp",
    label: "MCP",
    description: "独立管理 MCP 服务、连接测试和工具刷新",
    path: "/lite/mcp",
    icon: <Plug size={24} />,
  },
  {
    key: "memory",
    label: "记忆库",
    description: "记忆策略、统计信息和审计辅助",
    path: "/lite/memory",
    icon: <Brain size={24} />,
  },
  {
    key: "channels",
    label: "渠道",
    description: "按服务商维护 API 密钥、地址和模型列表",
    path: "/lite/channels",
    icon: <GitBranch size={24} />,
  },
  {
    key: "translation",
    label: "翻译",
    description: "翻译模型和语种默认值配置",
    path: "/lite/translation",
    icon: <Languages size={24} />,
  },
  {
    key: "research",
    label: "科研助理",
    description: "科研流程使用的模型与推理参数设置",
    path: "/lite/research",
    icon: <FlaskConical size={24} />,
  },
];

export function LiteHomePage() {
  const navigate = useNavigate();

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="lite-section-header">
        <div className="flex items-center gap-2">
          <LayoutDashboard size={18} className="text-blue-500" />
          <h1 className="text-base font-semibold text-gray-800 dark:text-gray-100">
            Admin Lite 总控台
          </h1>
        </div>
        <p className="text-xs text-gray-400">按模块进入配置工作区</p>
      </div>

      <div className="lite-home-grid">
        {MODULE_CARDS.map((card) => (
          <button
            key={card.key}
            type="button"
            className="lite-home-card text-left"
            onClick={() => navigate(card.path)}
          >
            <div className="mb-3 text-blue-500">{card.icon}</div>
            <div className="mb-1 text-sm font-semibold text-gray-800 dark:text-gray-100">
              {card.label}
            </div>
            <div className="text-xs leading-relaxed text-gray-500 dark:text-gray-400">
              {card.description}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
