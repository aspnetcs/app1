import { Route, Routes } from "react-router-dom";
import { LiteNav } from "./LiteNav";
import { AgentsWorkbench } from "../features/agents/AgentsWorkbench";
import { ChannelsWorkbench } from "../features/channels/ChannelsWorkbench";
import { LiteHomePage } from "../features/home/LiteHomePage";
import { KnowledgeWorkbench } from "../features/knowledge/KnowledgeWorkbench";
import { McpWorkbench } from "../features/mcp/McpWorkbench";
import { MemoryPage } from "../features/memory/MemoryPage";
import { ResearchPage } from "../features/research/ResearchPage";
import { TranslationPage } from "../features/translation/TranslationPage";

function NotFoundPage() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 text-gray-400">
      <span className="text-4xl">404</span>
      <p className="text-sm">未找到该页面</p>
    </div>
  );
}

type LiteShellProps = {
  onLogout: () => void;
};

export function LiteShell({ onLogout }: LiteShellProps) {
  return (
    <div className="lite-shell" style={{ height: "100vh", overflow: "hidden" }}>
      <LiteNav onLogout={onLogout} />
      <main className="lite-content">
        <Routes>
          <Route index element={<LiteHomePage />} />
          <Route path="agents" element={<AgentsWorkbench />} />
          <Route path="knowledge" element={<KnowledgeWorkbench />} />
          <Route path="mcp" element={<McpWorkbench />} />
          <Route path="memory" element={<MemoryPage />} />
          <Route path="channels" element={<ChannelsWorkbench />} />
          <Route path="translation" element={<TranslationPage />} />
          <Route path="research" element={<ResearchPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </div>
  );
}
