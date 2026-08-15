import { useEffect, useState } from "react";
import { Languages } from "lucide-react";
import { getFeatureConfig, updateFeatureConfig } from "../../../api/featureConfigs";
import { ModelSelector, EffectiveModelNote } from "../../components/ModelSelector";
import { SaveBar } from "../../components/SaveBar";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

type TranslationConfig = {
  enabled: boolean;
  defaultModel: string;
  defaultTargetLanguage: string;
  maxInputChars: number;
};

const DEFAULT_TRANSLATION_CONFIG: TranslationConfig = {
  enabled: true,
  defaultModel: "",
  defaultTargetLanguage: "English",
  maxInputChars: 4000,
};

function normalizeTranslationConfig(raw: Record<string, unknown> | null | undefined): TranslationConfig {
  return {
    enabled: typeof raw?.enabled === "boolean" ? raw.enabled : DEFAULT_TRANSLATION_CONFIG.enabled,
    defaultModel: typeof raw?.defaultModel === "string" ? raw.defaultModel : DEFAULT_TRANSLATION_CONFIG.defaultModel,
    defaultTargetLanguage:
      typeof raw?.defaultTargetLanguage === "string"
        ? raw.defaultTargetLanguage
        : DEFAULT_TRANSLATION_CONFIG.defaultTargetLanguage,
    maxInputChars:
      typeof raw?.maxInputChars === "number" && Number.isFinite(raw.maxInputChars)
        ? raw.maxInputChars
        : DEFAULT_TRANSLATION_CONFIG.maxInputChars,
  };
}

export function TranslationPage() {
  const [config, setConfig] = useState<TranslationConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setLoadError(null);

    getFeatureConfig("translation")
      .then((next) => {
        if (!mounted) return;
        setConfig(normalizeTranslationConfig(next));
      })
      .catch((err) => {
        if (!mounted) return;
        setLoadError(err instanceof Error ? err.message : "加载翻译配置失败");
      })
      .finally(() => {
        if (!mounted) return;
        setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const setField = <K extends keyof TranslationConfig>(key: K, value: TranslationConfig[K]) => {
    setConfig((prev) => (prev ? { ...prev, [key]: value } : prev));
  };

  const handleSave = () => {
    if (!config) return;
    save(async () => {
      const next = await updateFeatureConfig("translation", config as unknown as Record<string, unknown>);
      setConfig(normalizeTranslationConfig(next));
    });
  };

  if (loading) {
    return <LoadingBlock label="正在加载翻译配置..." />;
  }

  if (loadError) {
    return <ErrorBlock title="加载失败" detail={loadError} />;
  }

  if (!config) {
    return null;
  }

  return (
    <div className="flex flex-col h-full">
      <div className="lite-section-header">
        <span className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-200">
          <Languages size={15} />
          翻译配置
        </span>
      </div>

      <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-5 max-w-xl">
        <label className="lite-toggle-row cursor-pointer select-none">
          <span className="lite-toggle">
            <input
              type="checkbox"
              checked={config.enabled}
              onChange={(event) => setField("enabled", event.target.checked)}
            />
            <span className="lite-toggle-track" />
            <span className="lite-toggle-thumb" />
          </span>
          <span className="lite-form-label">启用翻译能力</span>
        </label>

        <div className="lite-form-group">
          <label className="lite-form-label">默认模型</label>
          <ModelSelector
            value={config.defaultModel}
            onChange={(value) => setField("defaultModel", value)}
            emptyLabel="留空时回退到当前可路由模型"
          />
          <EffectiveModelNote
            modelId={config.defaultModel}
            fallbackDescription="将回退到当前可路由渠道的默认模型"
          />
        </div>

        <div className="lite-form-group">
          <label className="lite-form-label">默认目标语言</label>
          <input
            className="lite-form-input"
            value={config.defaultTargetLanguage}
            onChange={(event) => setField("defaultTargetLanguage", event.target.value)}
            placeholder="例如 English"
          />
        </div>

        <div className="lite-form-group">
          <label className="lite-form-label">最大输入字符数</label>
          <input
            type="number"
            min="100"
            className="lite-form-input"
            value={config.maxInputChars}
            onChange={(event) => setField("maxInputChars", parseInt(event.target.value, 10) || 100)}
          />
        </div>

        <div className="lite-info-note">
          admin-lite 是独立前端，但仍然复用现有管理员后端接口，这里的配置会直接影响用户端翻译页的默认行为。
        </div>
      </div>

      <div className="border-t border-gray-200 dark:border-gray-700">
        <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
      </div>
    </div>
  );
}
