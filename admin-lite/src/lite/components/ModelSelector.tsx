import { useModels } from "../hooks/useModels";

type ModelSelectorProps = {
  value: string;
  onChange: (modelId: string) => void;
  placeholder?: string;
  disabled?: boolean;
  allowEmpty?: boolean;
  emptyLabel?: string;
};

export function ModelSelector({
  value,
  onChange,
  placeholder = "选择模型",
  disabled = false,
  allowEmpty = true,
  emptyLabel = "（空值，使用系统默认）",
}: ModelSelectorProps) {
  const { models, loading, error } = useModels();

  if (error) {
    return (
      <div className="flex gap-2 items-center">
        <input
          className="lite-form-input flex-1"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="模型 ID（无法加载列表，请手动输入）"
          disabled={disabled}
        />
      </div>
    );
  }

  return (
    <select
      className="lite-form-select w-full"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled || loading}
    >
      {allowEmpty ? (
        <option value="">{loading ? "加载中..." : emptyLabel}</option>
      ) : (
        <option value="" disabled>
          {loading ? "加载中..." : placeholder}
        </option>
      )}
      {models.map((m) => (
        <option key={m.id} value={m.id}>
          {m.name}{m.provider ? ` — ${m.provider}` : ""}
        </option>
      ))}
    </select>
  );
}

type EffectiveModelNoteProps = {
  modelId: string;
  fallbackDescription: string;
};

export function EffectiveModelNote({ modelId, fallbackDescription }: EffectiveModelNoteProps) {
  if (modelId) {
    return (
      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
        当前生效模型：<code className="bg-gray-100 dark:bg-gray-800 px-1 rounded text-xs">{modelId}</code>
      </p>
    );
  }
  return (
    <p className="text-xs text-amber-600 dark:text-amber-400 mt-1">
      未配置，{fallbackDescription}
    </p>
  );
}
