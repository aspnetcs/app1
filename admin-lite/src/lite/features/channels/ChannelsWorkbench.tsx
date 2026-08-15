import { useCallback, useEffect, useMemo, useState } from "react";
import { Download, Globe2, KeyRound, Plus, Search, Wifi } from "lucide-react";
import {
  createChannel,
  deleteChannel,
  fetchUpstreamModels,
  listChannels,
  testChannel,
  updateChannel,
  type ChannelItem,
  type ChannelTestResult,
} from "../../../api/channels";
import { EmptyBlock } from "../../../components/EmptyBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { SaveBar } from "../../components/SaveBar";
import { StatusBadge } from "../../components/StatusBadge";
import { ThreeColumn } from "../../components/ThreeColumn";
import { AdvancedSection } from "../../components/AdvancedSection";
import { useSaveState } from "../../hooks/useSaveState";
import {
  buildChannelPayload,
  buildChannelUrlPreview,
  buildFetchModelsPayload,
  blankChannelProviderForm,
  CHANNEL_TYPE_OPTIONS,
  getChannelTypeLabel,
  parseModelItems,
  summarizeProviderAddress,
  toChannelProviderForm,
  withNextChannelType,
  type ChannelProviderForm,
} from "./channelProviderShared";

function ChannelListColumn({
  channels,
  loading,
  error,
  selectedId,
  search,
  onSearchChange,
  onSelect,
  onNew,
}: {
  channels: ChannelItem[];
  loading: boolean;
  error: string | null;
  selectedId: string | null;
  search: string;
  onSearchChange: (value: string) => void;
  onSelect: (channel: ChannelItem) => void;
  onNew: () => void;
}) {
  const filteredChannels = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return channels;
    }

    return channels.filter((channel) => {
      const haystack =
        `${channel.name ?? ""} ${channel.type ?? ""} ${channel.baseUrl ?? ""} ${channel.models ?? ""}`.toLowerCase();
      return haystack.includes(keyword);
    });
  }, [channels, search]);

  return (
    <>
      <div className="lite-list-search flex items-center gap-2">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            className="lite-form-input w-full pl-6 text-xs"
            placeholder="搜索服务商..."
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
          <LoadingBlock label="正在加载渠道服务商..." />
        ) : error ? (
          <ErrorBlock title="渠道加载失败" detail={error} />
        ) : filteredChannels.length === 0 ? (
          <EmptyBlock title="暂无服务商渠道" detail="点击右上角新增一个服务商。" />
        ) : (
          filteredChannels.map((channel) => {
            const modelCount = parseModelItems(String(channel.models ?? "")).length;

            return (
              <div
                key={channel.id}
                role="button"
                tabIndex={0}
                className={`lite-list-item ${selectedId === channel.id ? "selected" : ""}`}
                onClick={() => onSelect(channel)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    onSelect(channel);
                  }
                }}
              >
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium text-gray-800 dark:text-gray-100">
                    {channel.name}
                  </div>
                  <div className="mt-0.5 truncate text-xs text-gray-400">
                    {getChannelTypeLabel(channel.type)} · {modelCount} 个模型
                  </div>
                  <div className="mt-1 truncate text-xs text-gray-400">
                    {summarizeProviderAddress(channel.baseUrl)}
                  </div>
                </div>
                <StatusBadge enabled={channel.enabled} activeLabel="已启用" inactiveLabel="已停用" />
              </div>
            );
          })
        )}
      </div>
    </>
  );
}

function ChannelDetailColumn({
  channel,
  isNew,
  onSaved,
  onDeleted,
}: {
  channel: ChannelItem | null;
  isNew: boolean;
  onSaved: (channel: ChannelItem) => void;
  onDeleted: (id: string) => void;
}) {
  const [form, setForm] = useState<ChannelProviderForm>(blankChannelProviderForm());
  const { status, errorMessage, save } = useSaveState();
  const [formError, setFormError] = useState<string | null>(null);
  const [fetchStateMessage, setFetchStateMessage] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<ChannelTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [fetchingModels, setFetchingModels] = useState(false);

  useEffect(() => {
    if (isNew) {
      setForm(blankChannelProviderForm());
      setFormError(null);
      setFetchStateMessage(null);
      setTestResult(null);
      return;
    }

    if (!channel) {
      return;
    }

    setForm(toChannelProviderForm(channel));
    setFormError(null);
    setFetchStateMessage(null);
    setTestResult(null);
  }, [channel, isNew]);

  const modelItems = useMemo(() => parseModelItems(form.models), [form.models]);
  const urlPreview = useMemo(
    () =>
      buildChannelUrlPreview({
        type: form.type,
        baseUrl: form.baseUrl,
        testModel: form.testModel,
      }),
    [form.baseUrl, form.testModel, form.type]
  );

  const setField = <K extends keyof ChannelProviderForm>(key: K, value: ChannelProviderForm[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleSave = () => {
    if (!form.name.trim()) {
      setFormError("请填写服务商名称。");
      return;
    }
    if (!form.baseUrl.trim()) {
      setFormError("请填写 API 地址。");
      return;
    }
    if (isNew && !form.apiKey.trim()) {
      setFormError("新建服务商时必须填写 API 密钥。");
      return;
    }

    setFormError(null);
    save(async () => {
      const payload = buildChannelPayload(form);
      const result = isNew
        ? ((await createChannel(payload)) as ChannelItem)
        : ((await updateChannel(channel!.id, payload)) as ChannelItem);

      onSaved(result);
    });
  };

  const handleDelete = () => {
    if (!channel) {
      return;
    }
    if (!window.confirm(`确认删除服务商“${channel.name}”吗？`)) {
      return;
    }

    setFormError(null);
    save(async () => {
      await deleteChannel(channel.id);
      onDeleted(channel.id);
    });
  };

  const handleTest = async () => {
    if (!channel) {
      return;
    }

    setTesting(true);
    setTestResult(null);
    try {
      setTestResult(await testChannel(channel.id));
    } catch (error) {
      setTestResult({
        success: false,
        message: error instanceof Error ? error.message : "测试请求失败",
      });
    } finally {
      setTesting(false);
    }
  };

  const handleFetchModels = async () => {
    setFetchingModels(true);
    setFetchStateMessage(null);
    setFormError(null);
    try {
      const payload = buildFetchModelsPayload({
        type: form.type,
        baseUrl: form.baseUrl,
        apiKey: form.apiKey,
        channelId: channel?.id ?? null,
      });

      if (!payload) {
        throw new Error("请先填写 API 地址；新建服务商时还需要提供 API 密钥。");
      }

      const result = await fetchUpstreamModels(payload);
      const modelsText = result.models.join("\n");
      setForm((current) => ({
        ...current,
        models: modelsText,
        testModel: current.testModel || result.models[0] || "",
      }));
      setFetchStateMessage(`已从上游拉取 ${result.count} 个模型。`);
    } catch (error) {
      setFetchStateMessage(null);
      setFormError(error instanceof Error ? error.message : "获取模型列表失败");
    } finally {
      setFetchingModels(false);
    }
  };

  if (!channel && !isNew) {
    return (
      <div className="flex flex-1 items-center justify-center text-sm text-gray-400">
        从左侧选择一个服务商，或点击加号新建。
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="lite-section-header">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-100">
              {isNew ? "新建服务商" : form.name || channel?.name || "未命名服务商"}
            </h2>
            <StatusBadge enabled={form.enabled} activeLabel="已启用" inactiveLabel="已停用" />
          </div>
          <p className="mt-1 text-xs text-gray-400">
            {getChannelTypeLabel(form.type)} ·{" "}
            {isNew
              ? "保存后即可测试连接。"
              : form.existingKeyCount > 0
                ? `已配置 ${form.existingKeyCount} 个密钥。`
                : "当前还没有已保存的密钥。"}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
            onClick={handleFetchModels}
            disabled={fetchingModels || !form.baseUrl.trim()}
          >
            <Download size={13} />
            {fetchingModels ? "拉取中..." : "从上游获取模型"}
          </button>
          {!isNew && channel ? (
            <button
              type="button"
              className="lite-btn lite-btn-secondary flex items-center gap-1 text-xs"
              onClick={handleTest}
              disabled={testing}
            >
              <Wifi size={13} />
              {testing ? "测试中..." : "测试连接"}
            </button>
          ) : null}
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-4 overflow-y-auto p-4">
        {formError ? <div className="lite-warning-note">{formError}</div> : null}
        {fetchStateMessage ? <div className="lite-info-note">{fetchStateMessage}</div> : null}
        {testResult ? (
          <div className={testResult.success ? "lite-info-note" : "lite-warning-note"}>
            {testResult.success
              ? `连接测试成功${testResult.latencyMs != null ? `，耗时 ${testResult.latencyMs}ms` : ""}。`
              : `连接测试失败：${testResult.message ?? "未知错误"}`}
          </div>
        ) : null}

        <section className="lite-provider-card">
          <div className="lite-provider-section-head">
            <div>
              <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-100">服务商信息</h3>
              <p className="mt-1 text-xs text-gray-400">服务商名称、类型和启用状态在这里维护。</p>
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <label className="lite-form-group">
              <span className="lite-form-label">服务商名称</span>
              <input
                className="lite-form-input"
                value={form.name}
                onChange={(event) => setField("name", event.target.value)}
                placeholder="例如：OpenAI 主渠道"
              />
            </label>
            <label className="lite-form-group">
              <span className="lite-form-label">服务商类型</span>
              <select
                className="lite-form-select"
                value={form.type}
                onChange={(event) =>
                  setForm((current) => withNextChannelType(current, event.target.value))
                }
              >
                {CHANNEL_TYPE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="lite-toggle-row cursor-pointer select-none self-end md:mt-6">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => setField("enabled", event.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用此服务商</span>
            </label>
          </div>
        </section>

        <div className="lite-provider-grid">
          <section className="lite-provider-card">
            <div className="lite-provider-card-title">
              <KeyRound size={15} />
              <span>API 密钥</span>
            </div>
            <label className="lite-form-group">
              <span className="lite-form-label">
                {isNew ? "新建密钥" : "更新密钥（留空则保持不变）"}
              </span>
              <input
                type="password"
                className="lite-form-input"
                value={form.apiKey}
                onChange={(event) => setField("apiKey", event.target.value)}
                placeholder={
                  isNew
                    ? "sk-..."
                    : form.existingKeyCount > 0
                      ? `已配置 ${form.existingKeyCount} 个密钥，留空则不覆盖`
                      : "当前未配置密钥"
                }
              />
            </label>
            <p className="text-xs text-gray-400">
              支持多个密钥，使用 <code>|</code> 分隔。编辑已有服务商时，只有填写新值才会覆盖原密钥。
            </p>
          </section>

          <section className="lite-provider-card">
            <div className="lite-provider-card-title">
              <Globe2 size={15} />
              <span>API 地址</span>
            </div>
            <label className="lite-form-group">
              <span className="lite-form-label">服务地址</span>
              <input
                className="lite-form-input font-mono text-xs"
                value={form.baseUrl}
                onChange={(event) => setField("baseUrl", event.target.value)}
                placeholder="https://api.openai.com/v1"
              />
            </label>
            {urlPreview ? (
              <div className="lite-provider-preview-stack">
                <div className="lite-provider-preview">
                  <strong>模型列表：</strong>
                  <span>{urlPreview.modelsUrl}</span>
                </div>
                {urlPreview.chatUrl ? (
                  <div className="lite-provider-preview">
                    <strong>聊天接口：</strong>
                    <span>{urlPreview.chatUrl}</span>
                  </div>
                ) : null}
                {urlPreview.notes.map((note) => (
                  <div key={note} className="text-xs text-gray-400">
                    {note}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-gray-400">填写 API 地址后，这里会展示模型和对话接口预览。</p>
            )}
          </section>
        </div>

        <section className="lite-provider-card">
          <div className="lite-provider-section-head">
            <div>
              <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-100">服务商模型</h3>
              <p className="mt-1 text-xs text-gray-400">
                下方展示这个服务商当前可用的模型列表，支持手动维护和上游拉取。
              </p>
            </div>
            <div className="text-xs text-gray-400">当前共 {modelItems.length} 个模型</div>
          </div>

          <div className="lite-provider-model-cloud">
            {modelItems.length > 0 ? (
              modelItems.map((model) => (
                <span key={model} className="lite-provider-model-chip">
                  {model}
                </span>
              ))
            ) : (
              <div className="lite-provider-empty">还没有模型，点击“从上游获取模型”或手动录入。</div>
            )}
          </div>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
            <label className="lite-form-group">
              <span className="lite-form-label">模型列表</span>
              <textarea
                className="lite-form-textarea"
                value={form.models}
                onChange={(event) => setField("models", event.target.value)}
                placeholder={"每行一个模型，或使用逗号分隔\n例如：\ngpt-4o\ngpt-4o-mini"}
              />
            </label>

            <div className="flex flex-col gap-4">
              <label className="lite-form-group">
                <span className="lite-form-label">测试模型</span>
                <input
                  className="lite-form-input"
                  value={form.testModel}
                  onChange={(event) => setField("testModel", event.target.value)}
                  placeholder={modelItems[0] ?? "例如：gpt-4o-mini"}
                />
              </label>
              <div className="lite-provider-preview">
                <strong>提示：</strong>
                <span>测试模型建议填写上面列表里的一个模型 ID，便于连接测试和后续联调。</span>
              </div>
            </div>
          </div>
        </section>

        <AdvancedSection label="高级调度参数">
          <div className="mt-2 grid gap-3 md:grid-cols-3">
            <label className="lite-form-group">
              <span className="lite-form-label">优先级</span>
              <input
                type="number"
                min="0"
                className="lite-form-input"
                value={form.priority}
                onChange={(event) => setField("priority", Number(event.target.value) || 0)}
              />
            </label>
            <label className="lite-form-group">
              <span className="lite-form-label">权重</span>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={form.weight}
                onChange={(event) => setField("weight", Number(event.target.value) || 1)}
              />
            </label>
            <label className="lite-form-group">
              <span className="lite-form-label">最大并发</span>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={form.maxConcurrent}
                onChange={(event) => setField("maxConcurrent", Number(event.target.value) || 1)}
              />
            </label>
          </div>
        </AdvancedSection>
      </div>

      <SaveBar
        status={status}
        errorMessage={errorMessage}
        onSave={handleSave}
        onDelete={isNew ? undefined : handleDelete}
        saveLabel={isNew ? "创建服务商" : "保存服务商"}
        deleteLabel="删除服务商"
      />
    </div>
  );
}

export function ChannelsWorkbench() {
  const [channels, setChannels] = useState<ChannelItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [selectedChannel, setSelectedChannel] = useState<ChannelItem | null>(null);
  const [isNew, setIsNew] = useState(false);

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    listChannels({ size: 200 })
      .then((response) => setChannels(response.items))
      .catch((loadError) => {
        setError(loadError instanceof Error ? loadError.message : "渠道加载失败");
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const handleSaved = (channel: ChannelItem) => {
    setChannels((current) => {
      const index = current.findIndex((item) => item.id === channel.id);
      if (index >= 0) {
        const next = [...current];
        next[index] = channel;
        return next;
      }
      return [channel, ...current];
    });
    setSelectedChannel(channel);
    setIsNew(false);
  };

  const handleDeleted = (id: string) => {
    setChannels((current) => current.filter((item) => item.id !== id));
    setSelectedChannel(null);
    setIsNew(false);
  };

  return (
    <ThreeColumn
      list={
        <ChannelListColumn
          channels={channels}
          loading={loading}
          error={error}
          selectedId={selectedChannel?.id ?? null}
          search={search}
          onSearchChange={setSearch}
          onSelect={(channel) => {
            setSelectedChannel(channel);
            setIsNew(false);
          }}
          onNew={() => {
            setSelectedChannel(null);
            setIsNew(true);
          }}
        />
      }
      detail={
        <ChannelDetailColumn
          channel={selectedChannel}
          isNew={isNew}
          onSaved={handleSaved}
          onDeleted={handleDeleted}
        />
      }
    />
  );
}
