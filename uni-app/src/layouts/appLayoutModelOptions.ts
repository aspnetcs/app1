export interface LayoutModelSource {
  id: string
  name: string
  avatar?: string | null
  supportsImageParsing?: boolean
  supportsImageParsingSource?: 'manual' | 'inferred' | 'unknown'
}

export interface LayoutModelOption {
  id: string
  name: string
  provider?: string
  avatarPath?: string
  supportsImageParsing?: boolean
  supportsImageParsingSource?: 'manual' | 'inferred' | 'unknown'
}

const PROVIDER_NAMES: Record<string, string> = {
  openai: 'OpenAI',
  claude: 'Anthropic',
  gemini: 'Google',
  deepseek: 'DeepSeek',
  qwen: 'Qwen',
  meta: 'Meta',
  mistral: 'Mistral',
  grok: 'xAI',
  doubao: 'ByteDance',
  kimi: 'Moonshot',
  zhipu: 'Zhipu',
  yi: 'Yi',
  hunyuan: 'Tencent',
  ernie: 'Baidu',
}

const AVATAR_PATH_MAP: Record<string, string> = {
  openai: '/static/images/models/openai.png',
  claude: '/static/images/models/claude.png',
  gemini: '/static/images/models/gemini.png',
  deepseek: '/static/images/models/deepseek.png',
  qwen: '/static/images/models/qwen.png',
}

const DEFAULT_AVATAR = '/static/images/models/default.png'

const UPPER_WORDS = new Set(['gpt', 'ai', 'llm', 'api', 'vl', 'moe', 'hd', 'sd', 'xl'])

function fixModelName(name: string): string {
  return name.replace(/\b\w+/g, (word) =>
    UPPER_WORDS.has(word.toLowerCase()) ? word.toUpperCase() : word,
  )
}

export function buildAppLayoutModelOptions(models: LayoutModelSource[]): LayoutModelOption[] {
  return models.map((model) => {
    const avatarKey = model.avatar || ''
    return {
      id: model.id,
      name: model.name,
      provider: PROVIDER_NAMES[avatarKey] || avatarKey || undefined,
      avatarPath: AVATAR_PATH_MAP[avatarKey] || DEFAULT_AVATAR,
      supportsImageParsing: model.supportsImageParsing,
      supportsImageParsingSource: model.supportsImageParsingSource,
    }
  })
}

