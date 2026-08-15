import { useCallback, useEffect, useState } from "react";
import { Plus, RefreshCw, Search, Wifi } from "lucide-react";
import {
  listMarketCatalog,
  updateMarketCatalogItem,
  type MarketCatalogItem,
} from "../../../api/market";
import {
  listMcpServers,
  createMcpServer,
  updateMcpServer,
  deleteMcpServer,
  testMcpServer,
  refreshMcpServer,
  type McpServer,
  type McpServerPayload,
  type McpConnectionResult,
} from "../../../api/mcp";
import { StatusBadge } from "../../components/StatusBadge";
import { SaveBar } from "../../components/SaveBar";
import { AdvancedSection } from "../../components/AdvancedSection";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

type MarketTab = "skill" | "mcp";

// ─── Skill ───────────────────────────────────────────
function SkillListColumn({
  skills,
  loading,
  error,
  search,
  onSearchChange,
  selectedId,
  onSelect,
}: {
  skills: MarketCatalogItem[];
  loading: boolean;
  error: string | null;
  search: string;
  onSearchChange: (v: string) => void;
  selectedId: string | null;
  onSelect: (item: MarketCatalogItem) => void;
}) {
  const filtered = skills.filter(
    (s) =>
      !search ||
      s.title.toLowerCase().includes(search.toLowerCase()) ||
      (s.category ?? "").toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <div className="lite-list-search">
        <div className="relative">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            className="lite-form-input w-full pl-6 text-xs"
            placeholder="搜索技能..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : filtered.length === 0 ? (
          <EmptyBlock title="暂无技能" detail="市场中无 SKILL 类型条目" />
        ) : (
          filtered.map((item) => (
            <div
              key={item.id}
              role="button"
              tabIndex={0}
              className={`lite-list-item ${selectedId === item.id ? "selected" : ""}`}
              onClick={() => onSelect(item)}
              onKeyDown={(e) => e.key === "Enter" && onSelect(item)}
            >
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
                  {item.title}
                </div>
                <div className="text-xs text-gray-400 mt-0.5 truncate">
                  {item.category || "未分类"}
                </div>
              </div>
              <StatusBadge enabled={item.enabled} />
            </div>
          ))
        )}
      </div>
    </>
  );
}

function SkillDetailColumn({ item, onSaved }: { item: MarketCatalogItem | null; onSaved: (item: MarketCatalogItem) => void }) {
  const { status, errorMessage, save } = useSaveState();
  const [enabled, setEnabled] = useState(item?.enabled ?? true);
  const [featured, setFeatured] = useState(item?.featured ?? false);

  useEffect(() => {
    if (item) {
      setEnabled(item.enabled);
      setFeatured(item.featured);
    }
  }, [item]);

  if (!item) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        从左侧选择技能条目
      </div>
    );
  }

  const handleSave = () => {
    save(async () => {
      const updated = await updateMarketCatalogItem(item.id, { enabled, featured });
      onSaved(updated);
    });
  };

  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-100">{item.title}</h2>
        <StatusBadge enabled={item.enabled} />
      </div>
      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        <div className="lite-form-group">
          <label className="lite-form-label">摘要</label>
          <div className="text-sm text-gray-600 dark:text-gray-300">{item.summary || "—"}</div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="lite-form-group">
            <label className="lite-form-label">分类</label>
            <div className="text-sm text-gray-600 dark:text-gray-300">{item.category || "—"}</div>
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">排序</label>
            <div className="text-sm text-gray-600 dark:text-gray-300">{item.sortOrder}</div>
          </div>
        </div>
        <div className="flex gap-4">
          <label className="lite-toggle-row cursor-pointer select-none">
            <span className="lite-toggle">
              <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
              <span className="lite-toggle-track" />
              <span className="lite-toggle-thumb" />
            </span>
            <span className="lite-form-label">上架</span>
          </label>
          <label className="lite-toggle-row cursor-pointer select-none">
            <span className="lite-toggle">
              <input type="checkbox" checked={featured} onChange={(e) => setFeatured(e.target.checked)} />
              <span className="lite-toggle-track" />
              <span className="lite-toggle-thumb" />
            </span>
            <span className="lite-form-label">精选</span>
          </label>
        </div>
        <div className="lite-info-note">
          技能库（SKILL）负责工具调用，属于市场资产治理。知识库、智能体编辑请从对应模块入口操作。
        </div>
      </div>
      <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
    </div>
  );
}

// ─── MCP ───────────────────────────────────────────
function blankMcpServer(): McpServerPayload {
  return {
    name: "",
    description: "",
    endpointUrl: "",
    transportType: "HTTP",
    enabled: true,
  };
}

function McpListColumn({
  servers,
  loading,
  error,
  selectedId,
  onSelect,
  onNew,
}: {
  servers: McpServer[];
  loading: boolean;
  error: string | null;
  selectedId: number | null;
  onSelect: (s: McpServer) => void;
  onNew: () => void;
}) {
  return (
    <>
      <div className="lite-list-search flex gap-2 items-center">
        <span className="text-xs text-gray-500 flex-1">MCP 服务 ({servers.length})</span>
        <button type="button" className="lite-btn lite-btn-primary px-2 py-1.5" onClick={onNew}>
          <Plus size={14} />
        </button>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : servers.length === 0 ? (
          <EmptyBlock title="暂无 MCP 服务" detail="点击 + 添加服务" />
        ) : (
          servers.map((s) => (
            <div
              key={s.id}
              role="button"
              tabIndex={0}
              className={`lite-list-item ${selectedId === s.id ? "selected" : ""}`}
              onClick={() => onSelect(s)}
              onKeyDown={(e) => e.key === "Enter" && onSelect(s)}
            >
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
                  {s.name}
                </div>
                <div className="text-xs text-gray-400 mt-0.5 truncate">{s.endpointUrl}</div>
              </div>
              <StatusBadge enabled={s.enabled} />
            </div>
          ))
        )}
      </div>
    </>
  );
}

function McpDetailColumn({
  server,
  isNew,
  onSaved,
  onDeleted,
}: {
  server: McpServer | null;
  isNew: boolean;
  onSaved: (s: McpServer) => void;
  onDeleted: (id: number) => void;
}) {
  const [form, setForm] = useState<McpServerPayload>(blankMcpServer());
  const { status, errorMessage, save } = useSaveState();
  const [testResult, setTestResult] = useState<McpConnectionResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (isNew) { setForm(blankMcpServer()); setTestResult(null); return; }
    if (!server) return;
    setForm({
      name: server.name,
      description: server.description,
      endpointUrl: server.endpointUrl,
      transportType: server.transportType,
      enabled: server.enabled,
    });
    setTestResult(null);
  }, [server, isNew]);

  const set = <K extends keyof McpServerPayload>(key: K, value: McpServerPayload[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleSave = () => {
    save(async () => {
      if (isNew) {
        const result = await createMcpServer(form);
        // Re-fetch to get full server object
        const servers = await listMcpServers();
        const created = servers.find((s) => s.id === (result as { id: number }).id);
        if (created) onSaved(created);
      } else {
        await updateMcpServer(server!.id, form);
        const servers = await listMcpServers();
        const updated = servers.find((s) => s.id === server!.id);
        if (updated) onSaved(updated);
      }
    });
  };

  const handleDelete = () => {
    if (!server) return;
    if (!confirm(`确定删除 MCP 服务「${server.name}」？`)) return;
    save(async () => {
      await deleteMcpServer(server.id);
      onDeleted(server.id);
    });
  };

  const handleTest = async () => {
    if (!server) return;
    setTesting(true);
    try {
      const result = await testMcpServer(server.id);
      setTestResult(result);
    } catch {
      setTestResult({ success: false, error: "测试请求失败" });
    } finally {
      setTesting(false);
    }
  };

  const handleRefresh = async () => {
    if (!server) return;
    setRefreshing(true);
    try {
      await refreshMcpServer(server.id);
    } catch {
      // ignore
    } finally {
      setRefreshing(false);
    }
  };

  if (!server && !isNew) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        从左侧选择 MCP 服务或点击 + 新建
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-100">
          {isNew ? "新建 MCP 服务" : server?.name}
        </h2>
        {!isNew && server && (
          <div className="flex gap-2">
            <button
              type="button"
              className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
              onClick={handleTest}
              disabled={testing}
            >
              <Wifi size={13} />
              {testing ? "测试中..." : "测试"}
            </button>
            <button
              type="button"
              className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
              onClick={handleRefresh}
              disabled={refreshing}
            >
              <RefreshCw size={13} />
              刷新
            </button>
          </div>
        )}
      </div>
      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        {testResult ? (
          <div className={`${testResult.success ? "lite-info-note" : "lite-warning-note"}`}>
            {testResult.success
              ? `连接成功，发现 ${testResult.toolCount ?? 0} 个工具`
              : `连接失败：${testResult.error ?? "未知错误"}`}
          </div>
        ) : null}
        <div className="lite-form-group">
          <label className="lite-form-label">名称 *</label>
          <input className="lite-form-input" value={form.name} onChange={(e) => set("name", e.target.value)} />
        </div>
        <div className="lite-form-group">
          <label className="lite-form-label">描述</label>
          <input className="lite-form-input" value={form.description} onChange={(e) => set("description", e.target.value)} />
        </div>
        <div className="lite-form-group">
          <label className="lite-form-label">端点地址 *</label>
          <input
            className="lite-form-input font-mono text-xs"
            value={form.endpointUrl}
            onChange={(e) => set("endpointUrl", e.target.value)}
            placeholder="https://mcp.example.com/api"
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="lite-form-group">
            <label className="lite-form-label">传输方式</label>
            <select className="lite-form-select" value={form.transportType ?? "HTTP"} onChange={(e) => set("transportType", e.target.value)}>
              <option value="HTTP">HTTP</option>
              <option value="WEBSOCKET">WebSocket</option>
              <option value="SSE">SSE</option>
            </select>
          </div>
          <label className="lite-toggle-row cursor-pointer select-none mt-5">
            <span className="lite-toggle">
              <input type="checkbox" checked={form.enabled} onChange={(e) => set("enabled", e.target.checked)} />
              <span className="lite-toggle-track" />
              <span className="lite-toggle-thumb" />
            </span>
            <span className="lite-form-label">启用</span>
          </label>
        </div>
        <AdvancedSection label="认证 Token">
          <div className="lite-form-group mt-2">
            <label className="lite-form-label">Auth Token（写入即更新，留空不变）</label>
            <input
              type="password"
              className="lite-form-input"
              value={form.authToken ?? ""}
              onChange={(e) => set("authToken", e.target.value)}
              placeholder="bearer token..."
            />
          </div>
        </AdvancedSection>
      </div>
      <SaveBar
        status={status}
        errorMessage={errorMessage}
        onSave={handleSave}
        onDelete={isNew ? undefined : handleDelete}
      />
    </div>
  );
}

// ─── Main ─────────────────────────────────────────
export function MarketWorkbench() {
  const [tab, setTab] = useState<MarketTab>("skill");

  // Skill state
  const [skills, setSkills] = useState<MarketCatalogItem[]>([]);
  const [skillsLoading, setSkillsLoading] = useState(true);
  const [skillsError, setSkillsError] = useState<string | null>(null);
  const [selectedSkill, setSelectedSkill] = useState<MarketCatalogItem | null>(null);
  const [skillSearch, setSkillSearch] = useState("");

  // MCP state
  const [servers, setServers] = useState<McpServer[]>([]);
  const [mcpLoading, setMcpLoading] = useState(true);
  const [mcpError, setMcpError] = useState<string | null>(null);
  const [selectedServer, setSelectedServer] = useState<McpServer | null>(null);
  const [isNewMcp, setIsNewMcp] = useState(false);

  const loadSkills = useCallback(() => {
    setSkillsLoading(true);
    listMarketCatalog({ assetType: "SKILL" })
      .then((res) => setSkills(res?.items ?? []))
      .catch((err) => setSkillsError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setSkillsLoading(false));
  }, []);

  const loadServers = useCallback(() => {
    setMcpLoading(true);
    listMcpServers()
      .then(setServers)
      .catch((err) => setMcpError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setMcpLoading(false));
  }, []);

  useEffect(() => { loadSkills(); loadServers(); }, []);

  const handleSkillSaved = (item: MarketCatalogItem) => {
    setSkills((prev) => prev.map((s) => (s.id === item.id ? item : s)));
    setSelectedSkill(item);
  };

  const handleMcpSaved = (s: McpServer) => {
    setServers((prev) => {
      const idx = prev.findIndex((x) => x.id === s.id);
      if (idx >= 0) { const next = [...prev]; next[idx] = s; return next; }
      return [s, ...prev];
    });
    setSelectedServer(s);
    setIsNewMcp(false);
  };

  const handleMcpDeleted = (id: number) => {
    setServers((prev) => prev.filter((s) => s.id !== id));
    setSelectedServer(null);
    setIsNewMcp(false);
  };

  return (
    <div className="lite-three-col">
      {/* Left: tab + list */}
      <div className="lite-col-list">
        <div className="lite-tab-bar flex-shrink-0">
          <button type="button" className={`lite-tab ${tab === "skill" ? "active" : ""}`} onClick={() => setTab("skill")}>
            SKILL
          </button>
          <button type="button" className={`lite-tab ${tab === "mcp" ? "active" : ""}`} onClick={() => setTab("mcp")}>
            MCP
          </button>
        </div>
        {tab === "skill" ? (
          <SkillListColumn
            skills={skills}
            loading={skillsLoading}
            error={skillsError}
            search={skillSearch}
            onSearchChange={setSkillSearch}
            selectedId={selectedSkill?.id ?? null}
            onSelect={setSelectedSkill}
          />
        ) : (
          <McpListColumn
            servers={servers}
            loading={mcpLoading}
            error={mcpError}
            selectedId={selectedServer?.id ?? null}
            onSelect={(s) => { setSelectedServer(s); setIsNewMcp(false); }}
            onNew={() => { setSelectedServer(null); setIsNewMcp(true); }}
          />
        )}
      </div>

      {/* Right: detail */}
      <div className="lite-col-detail">
        {tab === "skill" ? (
          <SkillDetailColumn item={selectedSkill} onSaved={handleSkillSaved} />
        ) : (
          <McpDetailColumn
            server={selectedServer}
            isNew={isNewMcp}
            onSaved={handleMcpSaved}
            onDeleted={handleMcpDeleted}
          />
        )}
      </div>
    </div>
  );
}
