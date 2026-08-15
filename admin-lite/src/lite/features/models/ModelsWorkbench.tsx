import { useCallback, useEffect, useState } from "react";
import { FileText, Image, Search } from "lucide-react";
import {
  listModels,
  updateModel,
  type ModelItem,
} from "../../../api/models";
import { ThreeColumn } from "../../components/ThreeColumn";
import { StatusBadge } from "../../components/StatusBadge";
import { SaveBar } from "../../components/SaveBar";
import { AdvancedSection } from "../../components/AdvancedSection";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

type CapabilityFilter = "all" | "vision" | "text" | "manual";
type ImageOverrideValue = "auto" | "enabled" | "disabled";

function getImageOverrideValue(value: boolean | null | undefined): ImageOverrideValue {
  if (value === true) return "enabled";
  if (value === false) return "disabled";
  return "auto";
}

function parseImageOverrideValue(value: ImageOverrideValue): boolean | null {
  if (value === "enabled") return true;
  if (value === "disabled") return false;
  return null;
}

function getCapabilityLabel(model: Pick<ModelItem, "supportsImageParsing">) {
  return model.supportsImageParsing ? "视觉多模态" : "纯文本";
}

function getCapabilityHint(model: Pick<ModelItem, "supportsImageParsing" | "supportsImageParsingSource">) {
  if (model.supportsImageParsing) {
    return model.supportsImageParsingSource === "manual"
      ? "管理员手动标记支持图片输入"
      : "按模型名称推断支持图片输入";
  }
  return model.supportsImageParsingSource === "manual"
    ? "管理员手动标记不支持图片输入"
    : "图片输入会被阻止，文档仍可先转文本";
}

function getSourceLabel(source: ModelItem["supportsImageParsingSource"]) {
  if (source === "manual") return "手动";
  if (source === "inferred") return "推断";
  return "未知";
}

function getEffectiveImageParsing(form: Partial<ModelItem>) {
  return form.imageParsingOverride ?? Boolean(form.supportsImageParsing);
}

function getEffectiveImageSource(form: Partial<ModelItem>): ModelItem["supportsImageParsingSource"] {
  return form.imageParsingOverride == null ? form.supportsImageParsingSource : "manual";
}

function CapabilityBadge({ model }: { model: ModelItem }) {
  const active = Boolean(model.supportsImageParsing);
  return (
    <span
      className={`lite-badge ${active ? "lite-badge-active" : "lite-badge-inactive"}`}
      title={getCapabilityHint(model)}
    >
      {getCapabilityLabel(model)}
    </span>
  );
}

function ModelListColumn({
  models,
  loading,
  error,
  selectedId,
  onSelect,
  search,
  onSearchChange,
  capabilityFilter,
  onCapabilityFilterChange,
}: {
  models: ModelItem[];
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  onSelect: (m: ModelItem) => void;
  search: string;
  onSearchChange: (v: string) => void;
  capabilityFilter: CapabilityFilter;
  onCapabilityFilterChange: (v: CapabilityFilter) => void;
}) {
  const filtered = models.filter(
    (m) =>
      (!search ||
        m.name.toLowerCase().includes(search.toLowerCase()) ||
        m.id.toLowerCase().includes(search.toLowerCase()) ||
        (m.provider ?? "").toLowerCase().includes(search.toLowerCase())) &&
      (capabilityFilter === "all" ||
        (capabilityFilter === "vision" && Boolean(m.supportsImageParsing)) ||
        (capabilityFilter === "text" && !Boolean(m.supportsImageParsing)) ||
        (capabilityFilter === "manual" && m.supportsImageParsingSource === "manual"))
  );
  const counts = {
    all: models.length,
    vision: models.filter((m) => Boolean(m.supportsImageParsing)).length,
    text: models.filter((m) => !Boolean(m.supportsImageParsing)).length,
    manual: models.filter((m) => m.supportsImageParsingSource === "manual").length,
  };

  return (
    <>
      <div className="lite-list-search">
        <div className="relative">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            className="lite-form-input w-full pl-6 text-xs"
            placeholder="搜索模型..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
        <div className="lite-segment-row mt-3">
          {[
            { value: "all" as const, label: `全部 ${counts.all}` },
            { value: "vision" as const, label: `多模态 ${counts.vision}` },
            { value: "text" as const, label: `纯文本 ${counts.text}` },
            { value: "manual" as const, label: `手动 ${counts.manual}` },
          ].map((item) => (
            <button
              key={item.value}
              type="button"
              className={`lite-segment-btn ${capabilityFilter === item.value ? "active" : ""}`}
              onClick={() => onCapabilityFilterChange(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div className="lite-col-list-inner">
        {loading ? (
          <LoadingBlock />
        ) : error ? (
          <ErrorBlock title="加载失败" detail={error} />
        ) : filtered.length === 0 ? (
          <EmptyBlock title="暂无模型" />
        ) : (
          filtered.map((m) => (
            <div
              key={m.id}
              role="button"
              tabIndex={0}
              className={`lite-list-item ${selectedId === m.id ? "selected" : ""}`}
              onClick={() => onSelect(m)}
              onKeyDown={(e) => e.key === "Enter" && onSelect(m)}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 min-w-0">
                  <div className="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">
                    {m.name}
                  </div>
                  {m.supportsImageParsingSource === "manual" ? (
                    <span className="lite-model-source-dot" title="能力由管理员手动覆盖" />
                  ) : null}
                </div>
                <div className="text-xs text-gray-400 mt-0.5">{m.provider ?? "—"}</div>
                <div className="flex flex-wrap gap-1.5 mt-2">
                  <CapabilityBadge model={m} />
                  <span className="lite-badge lite-badge-neutral" title="文档会先在平台侧抽取为文本，再发送给模型">
                    文档转文本
                  </span>
                </div>
              </div>
              <StatusBadge enabled={m.enabled} />
            </div>
          ))
        )}
      </div>
    </>
  );
}

function ModelDetailColumn({
  model,
  onSaved,
}: {
  model: ModelItem | null;
  onSaved: (m: ModelItem) => void;
}) {
  const [form, setForm] = useState<Partial<ModelItem>>({});
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    if (!model) return;
    setForm({ ...model });
  }, [model]);

  const set = <K extends keyof ModelItem>(key: K, value: ModelItem[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const handleSave = () => {
    if (!model) return;
    save(async () => {
      const updated = await updateModel(model.id, {
        name: form.name,
        avatar: form.avatar,
        description: form.description,
        pinned: form.pinned,
        sortOrder: form.sortOrder,
        defaultSelected: form.defaultSelected,
        multiChatEnabled: form.multiChatEnabled,
        billingEnabled: form.billingEnabled,
        requestPriceUsd: form.requestPriceUsd,
        promptPriceUsd: form.promptPriceUsd,
        inputPriceUsdPer1M: form.inputPriceUsdPer1M,
        outputPriceUsdPer1M: form.outputPriceUsdPer1M,
        imageParsingOverride: form.imageParsingOverride ?? null,
      });
      onSaved(updated);
    });
  };

  if (!model) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        从左侧选择模型
      </div>
    );
  }

  const effectiveImageParsing = getEffectiveImageParsing(form);
  const effectiveImageSource = getEffectiveImageSource(form);

  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <div className="min-w-0">
          <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-100 truncate">{model.name}</h2>
          <div className="text-xs text-gray-400 mt-0.5 truncate">{model.id}</div>
        </div>
        <div className="flex items-center gap-2">
          <CapabilityBadge model={model} />
          <StatusBadge enabled={model.enabled} />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
        <div className="lite-capability-summary">
          <div className="lite-capability-card">
            <div className="lite-capability-icon">
              <Image size={16} />
            </div>
            <div>
              <div className="lite-capability-title">图片输入</div>
              <div className="lite-capability-value">
                {effectiveImageParsing ? "可直接识别" : "当前不支持"}
              </div>
              <div className="lite-capability-meta">来源：{getSourceLabel(effectiveImageSource)}</div>
            </div>
          </div>
          <div className="lite-capability-card">
            <div className="lite-capability-icon">
              <FileText size={16} />
            </div>
            <div>
              <div className="lite-capability-title">文档输入</div>
              <div className="lite-capability-value">平台预处理可用</div>
              <div className="lite-capability-meta">PDF / Word / Excel / PPT / Markdown / CSV 等先抽取文本</div>
            </div>
          </div>
        </div>

        <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide">元数据</div>
        <div className="lite-form-group">
          <label className="lite-form-label">名称</label>
          <input
            className="lite-form-input"
            value={String(form.name ?? "")}
            onChange={(e) => set("name", e.target.value)}
          />
        </div>
        <div className="lite-form-group">
          <label className="lite-form-label">描述</label>
          <input
            className="lite-form-input"
            value={String(form.description ?? "")}
            onChange={(e) => set("description", e.target.value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="lite-form-group">
            <label className="lite-form-label">Provider</label>
            <input
              className="lite-form-input"
              value={String(form.provider ?? "")}
              onChange={(e) => set("provider", e.target.value)}
            />
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">排序</label>
            <input
              type="number"
              className="lite-form-input"
              value={Number(form.sortOrder ?? 0)}
              onChange={(e) => set("sortOrder", parseInt(e.target.value, 10))}
            />
          </div>
        </div>

        <div className="flex flex-wrap gap-4">
          {[
            { key: "enabled" as const, label: "启用" },
            { key: "defaultSelected" as const, label: "默认选中" },
            { key: "pinned" as const, label: "置顶" },
            { key: "multiChatEnabled" as const, label: "多模型聊天" },
            { key: "billingEnabled" as const, label: "计费" },
          ].map(({ key, label }) => (
            <label key={key} className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={Boolean(form[key])}
                  onChange={(e) => set(key, e.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">{label}</span>
            </label>
          ))}
        </div>

        <AdvancedSection label="多模态能力" defaultOpen>
          <div className="flex flex-col gap-3 mt-2">
            <div className="lite-form-group">
              <label className="lite-form-label">图片解析能力</label>
              <select
                className="lite-form-select"
                value={getImageOverrideValue(form.imageParsingOverride)}
                onChange={(e) =>
                  set("imageParsingOverride", parseImageOverrideValue(e.target.value as ImageOverrideValue))
                }
              >
                <option value="auto">自动推断</option>
                <option value="enabled">强制标记为多模态</option>
                <option value="disabled">强制标记为纯文本</option>
              </select>
            </div>
            <div className="lite-info-note">
              图片上传会按这里的有效能力校验；文档上传由平台先抽取文本，所以纯文本模型也能阅读文档内容。
            </div>
            <div className="flex flex-wrap gap-2">
              <span className={`lite-badge ${effectiveImageParsing ? "lite-badge-active" : "lite-badge-inactive"}`}>
                当前：{effectiveImageParsing ? "视觉多模态" : "纯文本"}
              </span>
              <span className="lite-badge lite-badge-neutral">
                来源：{getSourceLabel(effectiveImageSource)}
              </span>
              {form.imageParsingOverride == null ? (
                <span className="lite-badge lite-badge-neutral">未手动覆盖</span>
              ) : (
                <span className="lite-badge lite-badge-active">已手动覆盖</span>
              )}
            </div>
          </div>
        </AdvancedSection>

        <AdvancedSection label="价格">
          <div className="grid grid-cols-2 gap-3 mt-2">
            <div className="lite-form-group">
              <label className="lite-form-label">输入价格（每 1M tokens，USD）</label>
              <input
                type="number"
                step="0.01"
                className="lite-form-input"
                value={form.inputPriceUsdPer1M ?? ""}
                onChange={(e) => set("inputPriceUsdPer1M", parseFloat(e.target.value) || null)}
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">输出价格（每 1M tokens，USD）</label>
              <input
                type="number"
                step="0.01"
                className="lite-form-input"
                value={form.outputPriceUsdPer1M ?? ""}
                onChange={(e) => set("outputPriceUsdPer1M", parseFloat(e.target.value) || null)}
              />
            </div>
          </div>
        </AdvancedSection>

        <div className="lite-info-note">
          模型页是「目录元数据工作台」。连通性测试、排序重排请通过旧后台「渠道与模型」模块操作。
        </div>
      </div>

      <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
    </div>
  );
}

export function ModelsWorkbench() {
  const [models, setModels] = useState<ModelItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ModelItem | null>(null);
  const [search, setSearch] = useState("");
  const [capabilityFilter, setCapabilityFilter] = useState<CapabilityFilter>("all");

  const reload = useCallback(() => {
    setLoading(true);
    listModels({ size: 500 })
      .then((res) => setModels(res.items))
      .catch((err) => setError(err instanceof Error ? err.message : "加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { reload(); }, []);

  const handleSaved = (m: ModelItem) => {
    setModels((prev) => {
      const idx = prev.findIndex((x) => x.id === m.id);
      if (idx >= 0) { const next = [...prev]; next[idx] = m; return next; }
      return prev;
    });
    setSelected(m);
  };

  return (
    <ThreeColumn
      list={
        <ModelListColumn
          models={models}
          loading={loading}
          error={error}
          selectedId={selected?.id ?? null}
          onSelect={setSelected}
          search={search}
          onSearchChange={setSearch}
          capabilityFilter={capabilityFilter}
          onCapabilityFilterChange={setCapabilityFilter}
        />
      }
      detail={<ModelDetailColumn model={selected} onSaved={handleSaved} />}
    />
  );
}
