import { useEffect, useState } from "react";
import { FlaskConical } from "lucide-react";
import {
  getResearchAdminConfig,
  saveResearchAdminConfig,
  type ResearchAdminConfig,
} from "../../../api/researchAdmin";
import { ModelSelector, EffectiveModelNote } from "../../components/ModelSelector";
import { SaveBar } from "../../components/SaveBar";
import { AdvancedSection } from "../../components/AdvancedSection";
import { useSaveState } from "../../hooks/useSaveState";
import { LoadingBlock } from "../../../components/LoadingBlock";
import { ErrorBlock } from "../../../components/ErrorBlock";

export function ResearchAdminPage() {
  const [config, setConfig] = useState<ResearchAdminConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const { status, errorMessage, save } = useSaveState();

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setLoadError(null);

    getResearchAdminConfig()
      .then((next) => {
        if (!mounted) return;
        setConfig(next);
      })
      .catch((err) => {
        if (!mounted) return;
        setLoadError(err instanceof Error ? err.message : "加载科研配置失败");
      })
      .finally(() => {
        if (!mounted) return;
        setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const setTop = <K extends keyof ResearchAdminConfig>(key: K, value: ResearchAdminConfig[K]) =>
    setConfig((prev) => (prev ? { ...prev, [key]: value } : prev));

  const setLlm = <K extends keyof ResearchAdminConfig["llm"]>(
    key: K,
    value: ResearchAdminConfig["llm"][K],
  ) =>
    setConfig((prev) =>
      prev ? { ...prev, llm: { ...prev.llm, [key]: value } } : prev,
    );

  const setLiterature = <K extends keyof ResearchAdminConfig["literature"]>(
    key: K,
    value: ResearchAdminConfig["literature"][K],
  ) =>
    setConfig((prev) =>
      prev ? { ...prev, literature: { ...prev.literature, [key]: value } } : prev,
    );

  const setPaper = <K extends keyof ResearchAdminConfig["paper"]>(
    key: K,
    value: ResearchAdminConfig["paper"][K],
  ) =>
    setConfig((prev) =>
      prev ? { ...prev, paper: { ...prev.paper, [key]: value } } : prev,
    );

  const setExperiment = <K extends keyof ResearchAdminConfig["experiment"]>(
    key: K,
    value: ResearchAdminConfig["experiment"][K],
  ) =>
    setConfig((prev) =>
      prev ? { ...prev, experiment: { ...prev.experiment, [key]: value } } : prev,
    );

  const handleSave = () => {
    if (!config) return;

    save(async () => {
      const updated = await saveResearchAdminConfig(config);
      setConfig(updated);
    });
  };

  if (loading) {
    return <LoadingBlock label="正在加载科研配置..." />;
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
          <FlaskConical size={15} />
          科研助理配置
        </span>
      </div>

      <div className="flex-1 overflow-y-auto p-6 flex flex-col gap-5 max-w-xl">
        <label className="lite-toggle-row cursor-pointer select-none">
          <span className="lite-toggle">
            <input
              type="checkbox"
              checked={config.enabled}
              onChange={(event) => setTop("enabled", event.target.checked)}
            />
            <span className="lite-toggle-track" />
            <span className="lite-toggle-thumb" />
          </span>
          <span className="lite-form-label">启用科研助理</span>
        </label>

        <div className="flex flex-col gap-3">
          <div className="text-xs font-semibold text-gray-400 uppercase tracking-wide">LLM 配置</div>
          <div className="lite-form-group">
            <label className="lite-form-label">研究模型</label>
            <ModelSelector
              value={config.llm.model}
              onChange={(value) => setLlm("model", value)}
              emptyLabel="留空时回退到可路由模型"
            />
            <EffectiveModelNote
              modelId={config.llm.model}
              fallbackDescription="将回退到当前可路由渠道的默认模型"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="lite-form-group">
              <label className="lite-form-label">最大 Token 数</label>
              <input
                type="number"
                min="256"
                className="lite-form-input"
                value={config.llm.maxTokens}
                onChange={(event) => setLlm("maxTokens", parseInt(event.target.value, 10) || 0)}
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">Temperature</label>
              <input
                type="number"
                step="0.1"
                min="0"
                max="2"
                className="lite-form-input"
                value={config.llm.temperature}
                onChange={(event) => setLlm("temperature", parseFloat(event.target.value) || 0)}
              />
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="lite-form-group">
            <label className="lite-form-label">最大并发研究数</label>
            <input
              type="number"
              min="1"
              className="lite-form-input"
              value={config.maxConcurrentPipelines}
              onChange={(event) =>
                setTop("maxConcurrentPipelines", parseInt(event.target.value, 10) || 1)
              }
            />
          </div>
          <div className="lite-form-group">
            <label className="lite-form-label">阶段超时（分钟）</label>
            <input
              type="number"
              min="1"
              className="lite-form-input"
              value={config.stageTimeoutMinutes}
              onChange={(event) =>
                setTop("stageTimeoutMinutes", parseInt(event.target.value, 10) || 1)
              }
            />
          </div>
        </div>

        <AdvancedSection label="文献搜索">
          <div className="flex flex-col gap-3 mt-2">
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={config.literature.enabled}
                  onChange={(event) => setLiterature("enabled", event.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用文献搜索</span>
            </label>
            <div className="lite-form-group">
              <label className="lite-form-label">数据源（逗号分隔）</label>
              <input
                className="lite-form-input"
                value={config.literature.sources}
                onChange={(event) => setLiterature("sources", event.target.value)}
                placeholder="arxiv,semantic_scholar"
              />
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">每源最大结果数</label>
              <input
                type="number"
                min="1"
                className="lite-form-input"
                value={config.literature.maxResultsPerSource}
                onChange={(event) =>
                  setLiterature("maxResultsPerSource", parseInt(event.target.value, 10) || 1)
                }
              />
            </div>
          </div>
        </AdvancedSection>

        <AdvancedSection label="论文生成">
          <div className="flex flex-col gap-3 mt-2">
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={config.paper.enabled}
                  onChange={(event) => setPaper("enabled", event.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用论文生成</span>
            </label>
            <div className="grid grid-cols-2 gap-3">
              <div className="lite-form-group">
                <label className="lite-form-label">最大迭代次数</label>
                <input
                  type="number"
                  min="1"
                  className="lite-form-input"
                  value={config.paper.maxIterations}
                  onChange={(event) =>
                    setPaper("maxIterations", parseInt(event.target.value, 10) || 1)
                  }
                />
              </div>
              <div className="lite-form-group">
                <label className="lite-form-label">质量阈值</label>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  max="1"
                  className="lite-form-input"
                  value={config.paper.qualityThreshold}
                  onChange={(event) =>
                    setPaper("qualityThreshold", parseFloat(event.target.value) || 0)
                  }
                />
              </div>
            </div>
          </div>
        </AdvancedSection>

        <AdvancedSection label="实验模式">
          <div className="flex flex-col gap-3 mt-2">
            <label className="lite-toggle-row cursor-pointer select-none">
              <span className="lite-toggle">
                <input
                  type="checkbox"
                  checked={config.experiment.enabled}
                  onChange={(event) => setExperiment("enabled", event.target.checked)}
                />
                <span className="lite-toggle-track" />
                <span className="lite-toggle-thumb" />
              </span>
              <span className="lite-form-label">启用实验模式</span>
            </label>
            <div className="lite-form-group">
              <label className="lite-form-label">执行模式</label>
              <select
                className="lite-form-select"
                value={config.experiment.mode}
                onChange={(event) => setExperiment("mode", event.target.value)}
              >
                <option value="fast">fast（快速）</option>
                <option value="balanced">balanced（均衡）</option>
                <option value="thorough">thorough（深入）</option>
              </select>
            </div>
            <div className="lite-form-group">
              <label className="lite-form-label">时间预算（秒）</label>
              <input
                type="number"
                min="10"
                className="lite-form-input"
                value={config.experiment.timeBudgetSec}
                onChange={(event) =>
                  setExperiment("timeBudgetSec", parseInt(event.target.value, 10) || 10)
                }
              />
            </div>
          </div>
        </AdvancedSection>

        <div className="lite-info-note">
          研究模型留空时，将回退到当前可路由渠道的默认模型。建议优先选择具备长上下文和稳定推理能力的模型。
        </div>
      </div>

      <div className="border-t border-gray-200 dark:border-gray-700">
        <SaveBar status={status} errorMessage={errorMessage} onSave={handleSave} />
      </div>
    </div>
  );
}
