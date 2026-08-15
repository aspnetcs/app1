import { useCallback, useEffect, useState } from "react";
import { Database, LayoutList } from "lucide-react";
import {
  getKnowledgeAdminConfig,
  saveKnowledgeAdminConfig,
  listKnowledgeBases,
  listKnowledgeIngestJobs,
  type KnowledgeAdminConfig,
  type KnowledgeBaseSummary,
  type KnowledgeIngestJob,
} from "../../../api/knowledge";
import { ThreeColumn } from "../../components/ThreeColumn";
import { StatusBadge } from "../../components/StatusBadge";
import { SaveBar } from "../../components/SaveBar";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

type KnowledgeView = "bases" | "jobs";

function KnowledgeListColumn({
  bases,
  jobs,
  loading,
  error,
  view,
  onViewChange,
  selectedBaseId,
  onSelectBase,
}: {
  bases: KnowledgeBaseSummary[];
  jobs: KnowledgeIngestJob[];
  loading: boolean;
  error: string | null;
  view: KnowledgeView;
  onViewChange: (v: KnowledgeView) => void;
  selectedBaseId: string | null;
  onSelectBase: (kb: KnowledgeBaseSummary) => void;
}) {
  return (
    <>
      <div className="lite-tab-bar flex-shrink-0">
        <button
          type="button"
          className={`lite-tab ${view === "bases" ? "active" : ""}`}
          onClick={() => onViewChange("bases")}
        >
          库列表
        </button>
        <button
          type="button"
          className={`lite-tab ${view === "jobs" ? "active" : ""}`}
          onClick={() => onViewChange("jobs")}
        >
          导入任务
        </button>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : view === "bases" ? (
          bases.length === 0 ? (
            <EmptyBlock title="暂无知识库" />
          ) : (
            bases.map((kb) => (
              <div
                key={kb.id}
                role="button"
                tabIndex={0}
                className={`lite-list-item ${selectedBaseId === kb.id ? "selected" : ""}`}
                onClick={() => onSelectBase(kb)}
                onKeyDown={(e) => e.key === "Enter" && onSelectBase(kb)}
              >
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
                    {kb.name}
                  </div>
                  <div className="text-xs text-gray-400 mt-0.5">
                    {kb.documentCount} 文档 · {kb.chunkCount} 块
                  </div>
                </div>
                <span
                  className={`lite-badge ${kb.status === "ACTIVE" || kb.status === "READY" ? "lite-badge-active" : "lite-badge-inactive"}`}
                >
                  {kb.status}
                </span>
              </div>
            ))
          )
        ) : jobs.length === 0 ? (
          <EmptyBlock title="暂无导入任务" />
        ) : (
          jobs.map((job) => (
            <div key={job.id} className="lite-list-item flex-col items-start gap-1">
              <div className="text-xs font-medium text-gray-700 dark:text-gray-200 truncate w-full">
                {job.baseName || job.baseId || job.id}
              </div>
              <div className="flex items-center gap-2">
                <span className={`lite-badge ${job.status === "COMPLETED" ? "lite-badge-active" : "lite-badge-inactive"}`}>
                  {job.status}
                </span>
                {job.phase ? (
                  <span className="text-xs text-gray-400">{job.phase}</span>
                ) : null}
              </div>
              {job.errorMessage ? (
                <div className="text-xs text-red-500 truncate w-full">{job.errorMessage}</div>
              ) : null}
              <div className="text-xs text-gray-400">
                {job.processedDocuments ?? 0}/{job.totalDocuments ?? "?"} 文档
              </div>
            </div>
          ))
        )}
      </div>
    </>
  );
}

function KnowledgeDetailColumn({
  selected,
  config,
  onConfigSaved,
}: {
  selected: KnowledgeBaseSummary | null;
  config: KnowledgeAdminConfig | null;
  onConfigSaved: (cfg: KnowledgeAdminConfig) => void;
}) {
  const [form, setForm] = useState<KnowledgeAdminConfig | null>(null);
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    if (config) setForm({ ...config });
  }, [config]);

  const handleSave = () => {
    if (!form) return;
    save(async () => {
      const saved = await saveKnowledgeAdminConfig(form);
      onConfigSaved(saved);
    });
  };

  if (selected) {
    return (
      <div className="flex flex-col h-full">
        <div className="lite-section-header">
          <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-100 flex items-center gap-2">
            <Database size={15} />
            {selected.name}
          </h2>
          <span className={`lite-badge ${selected.status === "ACTIVE" || selected.status === "READY" ? "lite-badge-active" : "lite-badge-inactive"}`}>
            {selected.status}
          </span>
        </div>
        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="lite-form-group">
              <label className="lite-form-label">文档数</label>
              <div className="text-sm text-gray-700 dark:text-gray-200 py-2">{selected.documentCount}</div>
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">Chunk 数</label>
              <div className="text-sm text-gray-700 dark:text-gray-200 py-2">{selected.chunkCount}</div>
            </div>
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">检索模式 (retrievalMode)</label>
            <div className="text-sm text-gray-700 dark:text-gray-200 py-1.5 px-2 bg-gray-50 dark:bg-gray-800 rounded">
              {selected.retrievalMode || "（未设置）"}
            </div>
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">使用模式 (usageMode)</label>
            <div className="text-sm text-gray-700 dark:text-gray-200 py-1.5 px-2 bg-gray-50 dark:bg-gray-800 rounded">
              {selected.usageMode || "（未设置）"}
            </div>
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">AI 使用说明 (aiUsageInstruction)</label>
            <div className="text-sm text-gray-700 dark:text-gray-200 py-1.5 px-2 bg-gray-50 dark:bg-gray-800 rounded min-h-12 whitespace-pre-wrap">
              {selected.aiUsageInstruction || "（未设置）"}
            </div>
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">最近导入时间</label>
            <div className="text-sm text-gray-500 py-1">{selected.lastIngestAt ?? "—"}</div>
          </div>
          <div className="lite-info-note">
            <span className="font-medium">知识库 vs 技能库：</span>
            知识库面向语义检索和文档问答，市场技能库面向 AI 工具调用（SKILL/MCP），两者职责不同。
          </div>
        </div>
      </div>
    );
  }

  // Global config panel
  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-100 flex items-center gap-2">
          <LayoutList size={15} />
          知识库全局配置
        </h2>
      </div>
      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        {form == null ? (
          <LoadingBlock />
        ) : (
          <>
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(e) => setForm((f) => f ? { ...f, enabled: e.target.checked } : f)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用知识库功能</span>
            </label>
            <div className="lite-form-group">
              <label className="lite-form-label">每用户最大知识库数</label>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={form.maxBasesPerUser}
                onChange={(e) => setForm((f) => f ? { ...f, maxBasesPerUser: parseInt(e.target.value, 10) } : f)}
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">每文档最大 Chunk 数</label>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={form.maxChunksPerDocument}
                onChange={(e) => setForm((f) => f ? { ...f, maxChunksPerDocument: parseInt(e.target.value, 10) } : f)}
              />
            </div>
          </>
        )}
      </div>
      <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
    </div>
  );
}

export function KnowledgeWorkbench() {
  const [bases, setBases] = useState<KnowledgeBaseSummary[]>([]);
  const [jobs, setJobs] = useState<KnowledgeIngestJob[]>([]);
  const [config, setConfig] = useState<KnowledgeAdminConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<KnowledgeView>("bases");
  const [selected, setSelected] = useState<KnowledgeBaseSummary | null>(null);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      listKnowledgeBases(),
      listKnowledgeIngestJobs(),
      getKnowledgeAdminConfig(),
    ])
      .then(([basesData, jobsData, configData]) => {
        setBases(basesData);
        setJobs(jobsData);
        setConfig(configData);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setLoading(false));
  }, []);

  return (
    <ThreeColumn
      list={
        <KnowledgeListColumn
          bases={bases}
          jobs={jobs}
          loading={loading}
          error={error}
          view={view}
          onViewChange={setView}
          selectedBaseId={selected?.id ?? null}
          onSelectBase={(kb) => setSelected(kb)}
        />
      }
      detail={
        <KnowledgeDetailColumn
          selected={selected}
          config={config}
          onConfigSaved={setConfig}
        />
      }
    />
  );
}
