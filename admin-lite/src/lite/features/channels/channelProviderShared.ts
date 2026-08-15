import type { ChannelItem, ChannelPayload, FetchModelsPayload } from "../../../api/channels";

type ChannelTypeOption = {
  value: string;
  label: string;
};

type ChannelUrlPreview = {
  modelsUrl: string;
  chatUrl: string | null;
  notes: string[];
};

export type ChannelProviderForm = {
  name: string;
  type: string;
  baseUrl: string;
  apiKey: string;
  models: string;
  testModel: string;
  priority: number;
  weight: number;
  maxConcurrent: number;
  enabled: boolean;
  existingKeyCount: number;
};

export const CHANNEL_TYPE_OPTIONS: ChannelTypeOption[] = [
  { value: "OPENAI", label: "OpenAI" },
  { value: "AZURE", label: "Azure OpenAI" },
  { value: "ANTHROPIC", label: "Anthropic" },
  { value: "GEMINI", label: "Google Gemini" },
  { value: "DEEPSEEK", label: "DeepSeek" },
  { value: "OLLAMA", label: "Ollama" },
  { value: "CUSTOM", label: "自定义兼容服务" },
];

export const CHANNEL_DEFAULT_URLS: Record<string, string> = {
  OPENAI: "https://api.openai.com/v1",
  AZURE: "https://{resource}.openai.azure.com/openai",
  ANTHROPIC: "https://api.anthropic.com/v1",
  GEMINI: "https://generativelanguage.googleapis.com/v1beta",
  DEEPSEEK: "https://api.deepseek.com/v1",
  OLLAMA: "http://localhost:11434/v1",
  CUSTOM: "",
};

export function normalizeChannelType(type?: string) {
  return (type ?? "OPENAI").trim().toUpperCase();
}

export function getChannelTypeLabel(type?: string) {
  const normalizedType = normalizeChannelType(type);
  return CHANNEL_TYPE_OPTIONS.find((option) => option.value === normalizedType)?.label ?? normalizedType;
}

export function blankChannelProviderForm(): ChannelProviderForm {
  return {
    name: "",
    type: "OPENAI",
    baseUrl: CHANNEL_DEFAULT_URLS.OPENAI,
    apiKey: "",
    models: "",
    testModel: "",
    priority: 1,
    weight: 1,
    maxConcurrent: 5,
    enabled: true,
    existingKeyCount: 0,
  };
}

export function toChannelProviderForm(channel: ChannelItem): ChannelProviderForm {
  return {
    name: String(channel.name ?? ""),
    type: normalizeChannelType(channel.type),
    baseUrl: String(channel.baseUrl ?? ""),
    apiKey: "",
    models: normalizeModelText(String(channel.models ?? "")),
    testModel: String(channel.testModel ?? ""),
    priority: Number(channel.priority ?? 1),
    weight: Number(channel.weight ?? 1),
    maxConcurrent: Number(channel.maxConcurrent ?? 5),
    enabled: channel.enabled !== false,
    existingKeyCount: Number(channel.keyCount ?? 0),
  };
}

export function withNextChannelType(current: ChannelProviderForm, nextType: string): ChannelProviderForm {
  const normalizedCurrent = normalizeChannelType(current.type);
  const normalizedNext = normalizeChannelType(nextType);
  const currentDefaultUrl = CHANNEL_DEFAULT_URLS[normalizedCurrent] ?? "";

  return {
    ...current,
    type: normalizedNext,
    baseUrl: current.baseUrl === currentDefaultUrl ? (CHANNEL_DEFAULT_URLS[normalizedNext] ?? "") : current.baseUrl,
  };
}

export function buildChannelPayload(form: ChannelProviderForm): ChannelPayload {
  const payload: ChannelPayload = {
    name: form.name.trim(),
    type: normalizeChannelType(form.type),
    base_url: form.baseUrl.trim(),
    models: normalizeModelText(form.models) || null,
    test_model: form.testModel.trim() || null,
    priority: Number.isFinite(form.priority) ? form.priority : 1,
    weight: Number.isFinite(form.weight) ? form.weight : 1,
    max_concurrent: Number.isFinite(form.maxConcurrent) ? form.maxConcurrent : 5,
    enabled: form.enabled,
  };

  const apiKey = form.apiKey.trim();
  if (apiKey) {
    payload.api_key = apiKey;
  }

  return payload;
}

export function parseModelItems(modelsText: string) {
  return modelsText
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function normalizeModelText(modelsText: string) {
  return parseModelItems(modelsText).join("\n");
}

function stripTrailingSlashes(input: string) {
  let result = input.trim();
  while (result.endsWith("/")) {
    result = result.slice(0, -1);
  }
  return result;
}

function joinUrl(baseUrl: string, path: string) {
  const base = stripTrailingSlashes(baseUrl);
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  return `${base}${cleanPath}`;
}

function normalizeOpenAiApiPrefix(baseUrl: string) {
  const base = stripTrailingSlashes(baseUrl);
  if (base.endsWith("/v1") || base.endsWith("/v2")) {
    return base;
  }
  return `${base}/v1`;
}

function normalizeAnthropicBaseUrl(baseUrl: string) {
  let base = stripTrailingSlashes(baseUrl);
  if (base.endsWith("/v1")) {
    base = base.slice(0, -3);
  }
  return base;
}

function normalizeGeminiBaseUrl(baseUrl: string) {
  let base = stripTrailingSlashes(baseUrl);
  if (base.endsWith("/v1beta")) {
    base = base.slice(0, -7);
  }
  return base;
}

function normalizeGeminiModelPath(model: string) {
  const trimmed = model.trim();
  if (!trimmed) {
    return "models/gemini-1.5-flash";
  }
  return trimmed.startsWith("models/") ? trimmed : `models/${trimmed}`;
}

export function buildChannelUrlPreview({
  type,
  baseUrl,
  testModel,
}: {
  type: string;
  baseUrl: string;
  testModel?: string;
}): ChannelUrlPreview | null {
  const trimmedBaseUrl = baseUrl.trim();
  if (!trimmedBaseUrl) {
    return null;
  }

  const normalizedType = normalizeChannelType(type);
  const notes: string[] = [];

  if (normalizedType === "AZURE") {
    return {
      modelsUrl: joinUrl(trimmedBaseUrl, "/models"),
      chatUrl: null,
      notes: ["Azure 需要 deployment 与 api-version，这里只展示基础地址预览。"],
    };
  }

  if (
    normalizedType === "OPENAI" ||
    normalizedType === "DEEPSEEK" ||
    normalizedType === "OLLAMA" ||
    normalizedType === "CUSTOM"
  ) {
    const prefix = normalizeOpenAiApiPrefix(trimmedBaseUrl);
    if (stripTrailingSlashes(trimmedBaseUrl) !== prefix) {
      notes.push("当前地址不含 /v1 时，预览会自动补全到 OpenAI 兼容路径。");
    }
    return {
      modelsUrl: joinUrl(prefix, "/models"),
      chatUrl: joinUrl(prefix, "/chat/completions"),
      notes,
    };
  }

  if (normalizedType === "ANTHROPIC") {
    const base = normalizeAnthropicBaseUrl(trimmedBaseUrl);
    return {
      modelsUrl: joinUrl(base, "/v1/models"),
      chatUrl: joinUrl(base, "/v1/messages"),
      notes: ["Anthropic 会自动处理 /v1 前缀，这里展示最终请求路径预览。"],
    };
  }

  if (normalizedType === "GEMINI") {
    const base = normalizeGeminiBaseUrl(trimmedBaseUrl);
    const modelPath = normalizeGeminiModelPath(testModel ?? "");
    return {
      modelsUrl: joinUrl(base, "/v1beta/models"),
      chatUrl: joinUrl(base, `/v1beta/${modelPath}:streamGenerateContent`),
      notes: ["Gemini 的对话地址依赖具体模型 ID，预览会按测试模型生成。"],
    };
  }

  return {
    modelsUrl: joinUrl(trimmedBaseUrl, "/models"),
    chatUrl: joinUrl(trimmedBaseUrl, "/chat/completions"),
    notes: ["该类型未配置专属预览规则，以上按常见 OpenAI 兼容协议展示。"],
  };
}

export function buildFetchModelsPayload({
  type,
  baseUrl,
  apiKey,
  channelId,
}: {
  type: string;
  baseUrl: string;
  apiKey: string;
  channelId?: string | null;
}): FetchModelsPayload | null {
  const normalizedType = normalizeChannelType(type);
  const trimmedBaseUrl = baseUrl.trim();
  const trimmedApiKey = apiKey.trim();

  if (!trimmedBaseUrl) {
    return null;
  }

  let modelsBaseUrl = trimmedBaseUrl;
  if (
    normalizedType === "OPENAI" ||
    normalizedType === "DEEPSEEK" ||
    normalizedType === "OLLAMA" ||
    normalizedType === "CUSTOM"
  ) {
    modelsBaseUrl = normalizeOpenAiApiPrefix(trimmedBaseUrl);
  } else if (normalizedType === "ANTHROPIC") {
    modelsBaseUrl = joinUrl(normalizeAnthropicBaseUrl(trimmedBaseUrl), "/v1");
  } else if (normalizedType === "GEMINI") {
    modelsBaseUrl = joinUrl(normalizeGeminiBaseUrl(trimmedBaseUrl), "/v1beta");
  }

  if (channelId && !trimmedApiKey) {
    return { base_url: modelsBaseUrl, channel_id: channelId };
  }

  if (trimmedApiKey) {
    return { base_url: modelsBaseUrl, api_key: trimmedApiKey };
  }

  return null;
}

export function summarizeProviderAddress(baseUrl?: string) {
  const input = (baseUrl ?? "").trim();
  if (!input) {
    return "未配置 API 地址";
  }

  try {
    const url = new URL(input);
    return `${url.host}${url.pathname === "/" ? "" : url.pathname}`;
  } catch {
    return input;
  }
}
