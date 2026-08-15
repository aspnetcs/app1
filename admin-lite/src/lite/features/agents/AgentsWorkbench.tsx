import { useCallback, useEffect, useState } from "react";
import { Plus, Search } from "lucide-react";
import {
  listAgentsByFilter,
  createAgent,
  updateAgent,
  deleteAgent,
  type AgentItem,
  type AgentScope,
  type AgentUpsertPayload,
} from "../../../api/agents";
import { ThreeColumn } from "../../components/ThreeColumn";
import { StatusBadge } from "../../components/StatusBadge";
import { SaveBar } from "../../components/SaveBar";
import { AdvancedSection } from "../../components/AdvancedSection";
import { ModelSelector, EffectiveModelNote } from "../../components/ModelSelector";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

function blankAgent(): AgentUpsertPayload {
  return {
    name: "",
    modelId: "",
    systemPrompt: "",
    description: "",
    firstMessage: "",
    scope: "SYSTEM" as AgentScope,
    enabled: true,
    featured: false,
    sortOrder: 0,
    temperature: 0.7,
    topP: 1,
    maxTokens: 2048,
  };
}

function toPayload(form: AgentUpsertPayload): AgentUpsertPayload {
  return { ...form };
}

// Agent list column
function AgentListColumn({
  agents,
  loading,
  error,
  selectedId,
  onSelect,
  onNew,
  search,
  onSearchChange,
}: {
  agents: AgentItem[];
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  onSelect: (agent: AgentItem) => void;
  onNew: () => void;
  search: string;
  onSearchChange: (v: string) => void;
}) {
  return (
    <>
      <div className="lite-list-search flex gap-2 items-center">
        <div className="flex-1 relative">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            className="lite-form-input w-full pl-6 text-xs"
            placeholder="搜索智能体..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
        <button type="button" className="lite-btn lite-btn-primary px-2 py-1.5" onClick={onNew}>
          <Plus size={14} />
        </button>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock label="加载中..." />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : agents.length === 0 ? (
          <EmptyBlock title="没有智能体" detail="点击 + 新建一个" />
        ) : (
          agents.map((agent) => (
            <div
              key={agent.id}
              role="button"
              tabIndex={0}
              className={`lite-list-item ${selectedId === agent.id ? "selected" : ""}`}
              onClick={() => onSelect(agent)}
              onKeyDown={(e) => e.key === "Enter" && onSelect(agent)}
            >
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
                  {agent.name || "（未命名）"}
                </div>
                <div className="text-xs text-gray-400 truncate mt-0.5">
                  {agent.category || agent.scope || "SYSTEM"}
                </div>
              </div>
              <StatusBadge enabled={agent.enabled} />
            </div>
          ))
        )}
      </div>
    </>
  );
}

// Agent detail column
function AgentDetailColumn({
  agent,
  isNew,
  onSaved,
  onDeleted,
}: {
  agent: AgentItem | null;
  isNew: boolean;
  onSaved: (agent: AgentItem) => void;
  onDeleted: (id: string) => void;
}) {
  const [form, setForm] = useState<AgentUpsertPayload>(blankAgent());
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    if (isNew) {
      setForm(blankAgent());
      return;
    }
    if (!agent) return;
    setForm({
      name: agent.name ?? "",
      modelId: agent.modelId ?? "",
      systemPrompt: agent.systemPrompt ?? "",
      description: agent.description ?? "",
      firstMessage: agent.firstMessage ?? "",
      scope: (agent.scope as AgentScope) ?? "SYSTEM",
      enabled: agent.enabled ?? true,
      featured: agent.featured ?? false,
      sortOrder: agent.sortOrder ?? 0,
      temperature: agent.temperature ?? 0.7,
      topP: agent.topP ?? 1,
      maxTokens: agent.maxTokens ?? 2048,
    });
  }, [agent, isNew]);

  const set = <K extends keyof AgentUpsertPayload>(key: K, value: AgentUpsertPayload[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleSave = () => {
    save(async () => {
      const payload = toPayload(form);
      let result: AgentItem;
      if (isNew) {
        result = await createAgent(payload);
      } else {
        result = await updateAgent(agent!.id, payload);
      }
      onSaved(result);
    });
  };

  const handleDelete = () => {
    if (!agent) return;
    if (!confirm(`确定删除智能体「${agent.name}」？`)) return;
    save(async () => {
      await deleteAgent(agent.id);
      onDeleted(agent.id);
    });
  };

  if (!agent && !isNew) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        从左侧选择智能体或点击 + 新建
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <h2 className="font-semibold text-gray-800 dark:text-gray-100 text-sm">
          {isNew ? "新建智能体" : (agent?.name || "编辑智能体")}
        </h2>
        {!isNew && <StatusBadge enabled={agent?.enabled} />}
      </div>

      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        {/* Basic info */}
        <div className="flex flex-col gap-3">
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide">基础信息</div>
          <div className="lite-form-group">
            <label className="lite-form-label">名称 *</label>
            <input
              className="lite-form-input"
              value={form.name}
              onChange={(e) => set("name", e.target.value)}
              placeholder="智能体名称"
            />
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">描述</label>
            <input
              className="lite-form-input"
              value={form.description ?? ""}
              onChange={(e) => set("description", e.target.value)}
              placeholder="简短描述"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="lite-form-group">
              <label className="lite-form-label">分类</label>
              <input
                className="lite-form-input"
                value={(form as { category?: string }).category ?? ""}
                onChange={(e) => set("category" as keyof AgentUpsertPayload, e.target.value as AgentUpsertPayload[keyof AgentUpsertPayload])}
                placeholder="例如 assistant"
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">作用域</label>
              <select
                className="lite-form-select"
                value={form.scope ?? "SYSTEM"}
                onChange={(e) => set("scope", e.target.value as AgentScope)}
              >
                <option value="SYSTEM">SYSTEM</option>
                <option value="USER">USER</option>
              </select>
            </div>
          </div>
          <div className="flex gap-4">
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={form.enabled ?? true}
                  onChange={(e) => set("enabled", e.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用</span>
            </label>
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={form.featured ?? false}
                  onChange={(e) => set("featured", e.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">精选</span>
            </label>
          </div>
        </div>

        {/* Role definition */}
        <div className="flex flex-col gap-3">
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide">角色定义</div>
          <div className="lite-form-group">
            <label className="lite-form-label">模型</label>
            <ModelSelector
              value={form.modelId}
              onChange={(v) => set("modelId", v)}
            />
            <EffectiveModelNote
              modelId={form.modelId}
              fallbackDescription="使用路由默认模型"
            />
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">系统提示词 (System Prompt)</label>
            <textarea
              className="lite-form-textarea"
              style={{ minHeight: 120 }}
              value={form.systemPrompt ?? ""}
              onChange={(e) => set("systemPrompt", e.target.value)}
              placeholder="你是一名专业的 AI 助手..."
            />
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">首条消息</label>
            <textarea
              className="lite-form-textarea"
              value={form.firstMessage ?? ""}
              onChange={(e) => set("firstMessage", e.target.value)}
              placeholder="你好！有什么我可以帮助你的吗？"
            />
          </div>
        </div>

        {/* Advanced: inference params */}
        <AdvancedSection label="推理参数">
          <div className="grid grid-cols-3 gap-3 mt-2">
            <div className="lite-form-group">
              <label className="lite-form-label">Temperature</label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="2"
                className="lite-form-input"
                value={form.temperature ?? 0.7}
                onChange={(e) => set("temperature", parseFloat(e.target.value))}
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">Top P</label>
              <input
                type="number"
                step="0.05"
                min="0"
                max="1"
                className="lite-form-input"
                value={form.topP ?? 1}
                onChange={(e) => set("topP", parseFloat(e.target.value))}
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">Max Tokens</label>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={form.maxTokens ?? 2048}
                onChange={(e) => set("maxTokens", parseInt(e.target.value, 10))}
              />
            </div>
          </div>
        </AdvancedSection>

        {/* Note on knowledge/skill associations */}
        <div className="lite-info-note">
          知识库与技能关联字段由 <code className="bg-blue-100 dark:bg-blue-900 px-1 rounded text-xs">requiredToolsJson</code> 承载，
          请在 JSON 字段中维护，或通过旧后台关联表配置。
        </div>
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

export function AgentsWorkbench() {
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AgentItem | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [search, setSearch] = useState("");

  const reload = useCallback((s = search) => {
    setLoading(true);
    setError(null);
    listAgentsByFilter({ size: 100, search: s || undefined })
      .then((res) => setAgents(res.items))
      .catch((err) => setError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setLoading(false));
  }, [search]);

  useEffect(() => { reload(); }, []);

  const handleSearchChange = (v: string) => {
    setSearch(v);
    listAgentsByFilter({ size: 100, search: v || undefined })
      .then((res) => setAgents(res.items))
      .catch(() => {});
  };

  const handleSelect = (agent: AgentItem) => {
    setSelected(agent);
    setIsNew(false);
  };

  const handleNew = () => {
    setSelected(null);
    setIsNew(true);
  };

  const handleSaved = (saved: AgentItem) => {
    setAgents((prev) => {
      const idx = prev.findIndex((a) => a.id === saved.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = saved;
        return next;
      }
      return [saved, ...prev];
    });
    setSelected(saved);
    setIsNew(false);
  };

  const handleDeleted = (id: string) => {
    setAgents((prev) => prev.filter((a) => a.id !== id));
    setSelected(null);
    setIsNew(false);
  };

  return (
    <ThreeColumn
      list={
        <AgentListColumn
          agents={agents}
          loading={loading}
          error={error}
          selectedId={selected?.id ?? null}
          onSelect={handleSelect}
          onNew={handleNew}
          search={search}
          onSearchChange={handleSearchChange}
        />
      }
      detail={
        <AgentDetailColumn
          agent={selected}
          isNew={isNew}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
        />
      }
    />
  );
}
