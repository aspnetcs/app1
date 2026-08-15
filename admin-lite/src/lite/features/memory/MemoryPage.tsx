import { useEffect, useState } from "react";
import { Brain } from "lucide-react";
import {
  getMemoryAdminConfig,
  saveMemoryAdminConfig,
  getMemoryAdminStats,
  getMemoryAuditPage,
  type MemoryAdminConfig,
  type MemoryAdminStats,
  type MemoryAuditEntry,
} from "../../../api/memory";
import { ModelSelector, EffectiveModelNote } from "../../components/ModelSelector";
import { SaveBar } from "../../components/SaveBar";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

type ConfigSection = "basic" | "stats" | "audit";

const CONFIG_SECTIONS: { key: ConfigSection; label: string }[] = [
  { key: "basic", label: "基础配置" },
  { key: "stats", label: "统计概览" },
  { key: "audit", label: "最近审计" },
];

function ConfigNavColumn({
  active,
  onChange,
}: {
  active: ConfigSection;
  onChange: (v: ConfigSection) => void;
}) {
  return (
    <div className="lite-col-list-inner">
      {CONFIG_SECTIONS.map((s) => (
        <div
          key={s.key}
          role="button"
          tabIndex={0}
          className={`lite-list-item ${active === s.key ? "selected" : ""}`}
          onClick={() => onChange(s.key)}
          onKeyDown={(e) => e.key === "Enter" && onChange(s.key)}
        >
          <span className="text-sm text-gray-700 dark:text-gray-200">{s.label}</span>
        </div>
      ))}
    </div>
  );
}

export function MemoryPage() {
  const [section, setSection] = useState<ConfigSection>("basic");
  const [config, setConfig] = useState<MemoryAdminConfig | null>(null);
  const [stats, setStats] = useState<MemoryAdminStats | null>(null);
  const [audits, setAudits] = useState<MemoryAuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    setLoading(true);
    Promise.all([getMemoryAdminConfig(), getMemoryAdminStats(), getMemoryAuditPage(0, 10)])
      .then(([cfg, st, auditPage]) => {
        setConfig(cfg);
        setStats(st);
        setAudits(auditPage.items);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setLoading(false));
  }, []);

  const handleSave = () => {
    if (!config) return;
    save(async () => {
      const saved = await saveMemoryAdminConfig(config);
      setConfig(saved);
    });
  };

  const setField = <K extends keyof MemoryAdminConfig>(key: K, value: MemoryAdminConfig[K]) => {
    setConfig((prev) => (prev ? { ...prev, [key]: value } : prev));
  };

  return (
    <div className="lite-three-col">
      <div className="lite-col-list">
        <div className="lite-section-header flex-shrink-0">
          <span className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-200">
            <Brain size={15} /> 记忆库
          </span>
        </div>
        <ConfigNavColumn active={section} onChange={setSection} />
      </div>

      <div className="lite-col-detail flex flex-col h-full">
        {loading ? (
          <LoadingBlock />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : section === "basic" && config ? (
          <>
            <div className="lite-section-header flex-shrink-0">
              <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-100">基础配置</h2>
            </div>
            <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
              <label className="lite-toggle-row cursor-pointer select-none">
                <span className="lite-toggle">
                  <input
                    type="checkbox"
                    checked={config.enabled}
                    onChange={(e) => setField("enabled", e.target.checked)}
                  />
                  <span className="lite-toggle-track" />
                  <span className="lite-toggle-thumb" />
                </span>
                <span className="lite-form-label">启用记忆库</span>
              </label>
              <label className="lite-toggle-row cursor-pointer select-none">
                <span className="lite-toggle">
                  <input
                    type="checkbox"
                    checked={config.requireConsent}
                    onChange={(e) => setField("requireConsent", e.target.checked)}
                  />
                  <span className="lite-toggle-track" />
                  <span className="lite-toggle-thumb" />
                </span>
                <span className="lite-form-label">需要用户授权 (requireConsent)</span>
              </label>
              <div className="lite-form-group">
                <label className="lite-form-label">记忆保留天数 (retentionDays)</label>
                <input
                  type="number"
                  min="1"
                  className="lite-form-input"
                  value={config.retentionDays}
                  onChange={(e) => setField("retentionDays", parseInt(e.target.value, 10))}
                />
              </div>
              <div className="lite-form-group">
                <label className="lite-form-label">每用户最大条目数</label>
                <input
                  type="number"
                  min="1"
                  className="lite-form-input"
                  value={config.maxEntriesPerUser}
                  onChange={(e) => setField("maxEntriesPerUser", parseInt(e.target.value, 10))}
                />
              </div>
              <div className="lite-form-group">
                <label className="lite-form-label">每条目最大字符数</label>
                <input
                  type="number"
                  min="1"
                  className="lite-form-input"
                  value={config.maxCharsPerEntry}
                  onChange={(e) => setField("maxCharsPerEntry", parseInt(e.target.value, 10))}
                />
              </div>
              <div className="lite-form-group">
                <label className="lite-form-label">摘要模型 (summaryModel)</label>
                <ModelSelector
                  value={config.summaryModel}
                  onChange={(v) => setField("summaryModel", v)}
                  emptyLabel="（空值，使用默认路由模型）"
                />
                <EffectiveModelNote
                  modelId={config.summaryModel}
                  fallbackDescription="使用当前可路由渠道的默认模型"
                />
              </div>
            </div>
            <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
          </>
        ) : section === "stats" && stats ? (
          <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
            <div className="lite-section-header">
              <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-100">统计概览</h2>
            </div>
            <div className="grid grid-cols-2 gap-4">
              {[
                { label: "总用户数", value: stats.totalUsers },
                { label: "总记忆条目", value: stats.totalEntries },
                { label: "待审核", value: stats.pendingReviews },
                { label: "平均条目/用户", value: stats.averageEntriesPerUser.toFixed(1) },
              ].map((item) => (
                <div key={item.label} className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
                  <div className="text-xs text-gray-400 mb-1">{item.label}</div>
                  <div className="text-xl font-semibold text-gray-800 dark:text-gray-100">{item.value}</div>
                </div>
              ))}
            </div>
          </div>
        ) : section === "audit" ? (
          <div className="flex-1 overflow-y-auto p-4">
            <div className="text-sm font-semibold text-gray-700 dark:text-gray-200 mb-3">最近审计（前10条）</div>
            {audits.length === 0 ? (
              <div className="text-sm text-gray-400">暂无审计记录</div>
            ) : (
              <div className="flex flex-col gap-2">
                {audits.map((a) => (
                  <div key={a.id} className="p-3 rounded border border-gray-200 dark:border-gray-700 text-sm">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-gray-700 dark:text-gray-200">{a.action}</span>
                      <span className={`lite-badge ${a.status === "APPROVED" ? "lite-badge-active" : "lite-badge-inactive"}`}>{a.status}</span>
                    </div>
                    <div className="text-xs text-gray-400 truncate">{a.summary}</div>
                    <div className="text-xs text-gray-400 mt-1">{a.createdAt ?? "—"}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}
