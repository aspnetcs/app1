import { useCallback, useEffect, useMemo, useState } from "react";
import { Plus, RefreshCw, Search, Wifi } from "lucide-react";
import {
  createMcpServer,
  deleteMcpServer,
  listMcpServers,
  refreshMcpServer,
  testMcpServer,
  updateMcpServer,
  type McpConnectionResult,
  type McpServer,
  type McpServerPayload,
} from "../../../api/mcp";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { SaveBar } from "../../components/SaveBar";
import { StatusBadge } from "../../components/StatusBadge";
import { ThreeColumn } from "../../components/ThreeColumn";
import { useSaveState } from "../../hooks/useSaveState";

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
  search,
  onSearchChange,
  onSelect,
  onNew,
}: {
  servers: McpServer[];
  loading: boolean;
  error: string | null;
  selectedId: number | null;
  search: string;
  onSearchChange: (value: string) => void;
  onSelect: (server: McpServer) => void;
  onNew: () => void;
}) {
  const filteredServers = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return servers;
    }

    return servers.filter((server) => {
      const haystack = `${server.name} ${server.description} ${server.endpointUrl}`.toLowerCase();
      return haystack.includes(keyword);
    });
  }, [search, servers]);

  return (
    <>
      <div className="lite-list-search flex items-center gap-2">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            className="lite-form-input w-full pl-6 text-xs"
            placeholder="搜索 MCP 服务..."
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>
        <button type="button" className="lite-btn lite-btn-primary px-2 py-1.5" onClick={onNew}>
          <Plus size={14} />
        </button>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock label="正在加载 MCP 服务..." />
        ) : error ? (
          <ErrorBlock title="MCP 加载失败" detail={error} />
        ) : filteredServers.length === 0 ? (
          <EmptyBlock title="暂无 MCP 服务" detail="点击右上角新增一个 MCP 服务。" />
        ) : (
          filteredServers.map((server) => (
            <div
              key={server.id}
              role="button"
              tabIndex={0}
              className={`lite-list-item ${selectedId === server.id ? "selected" : ""}`}
              onClick={() => onSelect(server)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  onSelect(server);
                }
              }}
            >
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium text-gray-800 dark:text-gray-100">
                  {server.name}
                </div>
                <div className="mt-0.5 truncate text-xs text-gray-400">
                  {server.endpointUrl || "未配置地址"}
                </div>
              </div>
              <StatusBadge enabled={server.enabled} />
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
  onSaved: (server: McpServer) => void;
  onDeleted: (id: number) => void;
}) {
  const [form, setForm] = useState<McpServerPayload>(blankMcpServer());
  const { status, errorMessage, save } = useSaveState();
  const [formError, setFormError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<McpConnectionResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    if (isNew) {
      setForm(blankMcpServer());
      setFormError(null);
      setTestResult(null);
      return;
    }

    if (!server) {
      return;
    }

    setForm({
      name: server.name,
      description: server.description,
      endpointUrl: server.endpointUrl,
      transportType: server.transportType,
      enabled: server.enabled,
    });
    setFormError(null);
    setTestResult(null);
  }, [isNew, server]);

  const setField = <K extends keyof McpServerPayload>(key: K, value: McpServerPayload[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleSave = () => {
    if (!form.name.trim()) {
      setFormError("请填写 MCP 服务名称。");
      return;
    }
    if (!form.endpointUrl.trim()) {
      setFormError("请填写 MCP 服务地址。");
      return;
    }

    setFormError(null);
    save(async () => {
      if (isNew) {
        const created = await createMcpServer(form);
        const servers = await listMcpServers();
        const latest = servers.find((item) => item.id === created.id);
        if (!latest) {
          throw new Error("新建后的 MCP 服务未能重新加载。");
        }
        onSaved(latest);
        return;
      }

      if (!server) {
        throw new Error("当前没有可更新的 MCP 服务。");
      }

      await updateMcpServer(server.id, form);
      const servers = await listMcpServers();
      const latest = servers.find((item) => item.id === server.id);
      if (!latest) {
        throw new Error("更新后的 MCP 服务未能重新加载。");
      }
      onSaved(latest);
    });
  };

  const handleDelete = () => {
    if (!server) {
      return;
    }
    if (!window.confirm(`确认删除 MCP 服务“${server.name}”吗？`)) {
      return;
    }

    setFormError(null);
    save(async () => {
      await deleteMcpServer(server.id);
      onDeleted(server.id);
    });
  };

  const handleTest = async () => {
    if (!server) {
      return;
    }

    setTesting(true);
    setTestResult(null);
    try {
      setTestResult(await testMcpServer(server.id));
    } catch (error) {
      setTestResult({
        success: false,
        error: error instanceof Error ? error.message : "测试请求失败",
      });
    } finally {
      setTesting(false);
    }
  };

  const handleRefresh = async () => {
    if (!server) {
      return;
    }

    setRefreshing(true);
    try {
      await refreshMcpServer(server.id);
    } finally {
      setRefreshing(false);
    }
  };

  if (!server && !isNew) {
    return (
      <div className="flex flex-1 items-center justify-center text-sm text-gray-400">
        从左侧选择一个 MCP 服务，或点击加号新建。
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="lite-section-header">
        <div>
          <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-100">
            {isNew ? "新建 MCP 服务" : form.name || server?.name || "未命名 MCP 服务"}
          </h2>
          <p className="mt-1 text-xs text-gray-400">
            {isNew ? "保存后即可测试连接和刷新工具。" : form.endpointUrl || "未配置 MCP 地址"}
          </p>
        </div>
        <div className="flex gap-2">
          {!isNew && server ? (
            <>
              <button
                type="button"
                className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
                onClick={handleTest}
                disabled={testing}
              >
                <Wifi size={13} />
                {testing ? "测试中..." : "测试连接"}
              </button>
              <button
                type="button"
                className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
                onClick={handleRefresh}
                disabled={refreshing}
              >
                <RefreshCw size={13} />
                {refreshing ? "刷新中..." : "刷新工具"}
              </button>
            </>
          ) : null}
          <StatusBadge enabled={form.enabled} activeLabel="已启用" inactiveLabel="已停用" />
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-4 overflow-y-auto p-4">
        {formError ? <div className="lite-warning-note">{formError}</div> : null}
        {testResult ? (
          <div className={testResult.success ? "lite-info-note" : "lite-warning-note"}>
            {testResult.success
              ? `连接成功，当前发现 ${testResult.toolCount ?? 0} 个工具。`
              : `连接失败：${testResult.error ?? "未知错误"}`}
          </div>
        ) : null}

        <div className="lite-provider-grid">
          <section className="lite-provider-card">
            <div className="lite-provider-card-title">基础信息</div>
            <div className="grid gap-3 md:grid-cols-2">
              <label className="lite-form-group">
                <span className="lite-form-label">名称</span>
                <input
                  className="lite-form-input"
                  value={form.name}
                  onChange={(event) => setField("name", event.target.value)}
                  placeholder="例如：本地工具集"
                />
              </label>
              <label className="lite-form-group">
                <span className="lite-form-label">传输方式</span>
                <select
                  className="lite-form-select"
                  value={form.transportType ?? "HTTP"}
                  onChange={(event) => setField("transportType", event.target.value)}
                >
                  <option value="HTTP">HTTP</option>
                  <option value="WEBSOCKET">WebSocket</option>
                  <option value="SSE">SSE</option>
                </select>
              </label>
            </div>
            <label className="lite-form-group">
              <span className="lite-form-label">服务地址</span>
              <input
                className="lite-form-input font-mono text-xs"
                value={form.endpointUrl}
                onChange={(event) => setField("endpointUrl", event.target.value)}
                placeholder="https://mcp.example.com/api"
              />
            </label>
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => setField("enabled", event.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用 MCP 服务</span>
            </label>
          </section>

          <section className="lite-provider-card">
            <div className="lite-provider-card-title">说明与认证</div>
            <label className="lite-form-group">
              <span className="lite-form-label">描述</span>
              <textarea
                className="lite-form-textarea"
                value={form.description}
                onChange={(event) => setField("description", event.target.value)}
                placeholder="补充这个 MCP 服务的用途和工具范围。"
              />
            </label>
            <label className="lite-form-group">
              <span className="lite-form-label">Auth Token</span>
              <input
                type="password"
                className="lite-form-input"
                value={form.authToken ?? ""}
                onChange={(event) => setField("authToken", event.target.value)}
                placeholder={isNew ? "Bearer Token" : "留空则保持现有 Token"}
              />
            </label>
            <p className="text-xs text-gray-400">
              保存时如果填写了 Token，会直接写入最新值；编辑已有服务时留空不会覆盖现有 Token。
            </p>
          </section>
        </div>
      </div>

      <SaveBar
        status={status}
        errorMessage={errorMessage}
        onSave={handleSave}
        onDelete={isNew ? undefined : handleDelete}
        saveLabel={isNew ? "创建 MCP 服务" : "保存 MCP 服务"}
      />
    </div>
  );
}

export function McpWorkbench() {
  const [servers, setServers] = useState<McpServer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [selectedServer, setSelectedServer] = useState<McpServer | null>(null);
  const [isNew, setIsNew] = useState(false);

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    listMcpServers()
      .then((items) => setServers(items))
      .catch((loadError) => {
        setError(loadError instanceof Error ? loadError.message : "MCP 服务加载失败");
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const handleSaved = (server: McpServer) => {
    setServers((current) => {
      const index = current.findIndex((item) => item.id === server.id);
      if (index >= 0) {
        const next = [...current];
        next[index] = server;
        return next;
      }
      return [server, ...current];
    });
    setSelectedServer(server);
    setIsNew(false);
  };

  const handleDeleted = (id: number) => {
    setServers((current) => current.filter((item) => item.id !== id));
    setSelectedServer(null);
    setIsNew(false);
  };

  return (
    <ThreeColumn
      list={
        <McpListColumn
          servers={servers}
          loading={loading}
          error={error}
          selectedId={selectedServer?.id ?? null}
          search={search}
          onSearchChange={setSearch}
          onSelect={(server) => {
            setSelectedServer(server);
            setIsNew(false);
          }}
          onNew={() => {
            setSelectedServer(null);
            setIsNew(true);
          }}
        />
      }
      detail={
        <McpDetailColumn
          server={selectedServer}
          isNew={isNew}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
        />
      }
    />
  );
}
