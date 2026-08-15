// 多品牌配置入口
// 构建时通过 UNI_BRAND 环境变量选择品牌

export interface BrandConfig {
  brandId: string
  appName: string
  logo: string
  themeColor: string
  apiBaseUrl: string
  wsBaseUrl: string
  sttUrl: string
  features: {
      accountbook: boolean
      wechatLogin: boolean
      oauth: boolean
      guestAuth: boolean
      configTransfer: boolean
      functionCalling: boolean
      multiChat: boolean
      stt: boolean
      tts: boolean
      citations: boolean
      knowledgeBase: boolean
      memory: boolean
      translation: boolean
      promptOptimize: boolean
      followUp: boolean
      webRead: boolean
      mermaid: boolean
      banner: boolean
      userGroups: boolean
      pwa: boolean
      promptLibrary: boolean
      modelCatalog: boolean
      presets: boolean
      masks: boolean
      temporaryChat: boolean
      conversationPinStar: boolean
      messageVersioning: boolean
      mcp: boolean
      agentMarket: boolean
      minapps: boolean
      openWebuiHome: boolean
      researchAssistant: boolean
      multimodalUpload: boolean
    }
}

// 从环境变量读取 API 地址 (优先级最高)
const envApiBase = import.meta.env.VITE_API_BASE || ''
const envWsBase = import.meta.env.VITE_WS_BASE || ''
const envSttUrl = import.meta.env.VITE_STT_URL || ''
const envGuestAuth = import.meta.env.VITE_GUEST_AUTH || ''

// Android 模拟器 (ADB emulator) 需要使用 10.0.2.2 访问宿主机
// 真机则使用局域网 IP
const isAndroidEmulator = (): boolean => {
  // #ifdef APP-PLUS
  try {
    const runtime = uni.getSystemInfoSync()
    return runtime.platform === 'android'
  } catch (e) {
    return false
  }
  // #endif
  return false
}

const HOST_DEFAULT = '127.0.0.1:8080'
const EMULATOR_HOST_DEFAULT = '10.0.2.2:8080'
const defaultHost = isAndroidEmulator() ? EMULATOR_HOST_DEFAULT : HOST_DEFAULT

const guestAuthDefault = envGuestAuth !== 'false'

const brandConfigs: Record<string, BrandConfig> = {
  'brand-a': {
    brandId: 'brand-a',
    appName: 'AI 智能助手',
    logo: '/static/images/logo.png',
    themeColor: '#4F6BFF',
    apiBaseUrl: envApiBase || `http://${defaultHost}/api`,
    wsBaseUrl: envWsBase || `ws://${defaultHost}/ws`,
    sttUrl: envSttUrl,
    features: {
      accountbook: false,
      wechatLogin: true,
      oauth: true,
      guestAuth: guestAuthDefault,
      configTransfer: true,
      functionCalling: true,
      multiChat: true,
      stt: !!envSttUrl,
      tts: true,
      citations: true,
      knowledgeBase: true,
      memory: true,
      translation: true,
      promptOptimize: true,
      followUp: true,
      webRead: true,
      mermaid: true,
      banner: true,
      userGroups: true,
      pwa: true,
      promptLibrary: true,
      modelCatalog: true,
      presets: true,
      masks: true,
      temporaryChat: true,
      conversationPinStar: true,
      messageVersioning: true,
      mcp: true,
      agentMarket: true,
      minapps: false,
      openWebuiHome: true,
      researchAssistant: true,
      multimodalUpload: true,
    },
  },
  'brand-b': {
    brandId: 'brand-b',
    appName: 'AI 智能助手',
    logo: '/static/images/logo.png',
    themeColor: '#6366F1',
    apiBaseUrl: envApiBase || `http://${defaultHost}/api`,
    wsBaseUrl: envWsBase || `ws://${defaultHost}/ws`,
    sttUrl: '',
    features: {
      accountbook: false,
      wechatLogin: true,
      oauth: false,
      guestAuth: guestAuthDefault,
      configTransfer: false,
      functionCalling: false,
      multiChat: false,
      stt: false,
      tts: false,
      citations: false,
      knowledgeBase: false,
      memory: false,
      translation: false,
      promptOptimize: false,
      followUp: false,
      webRead: false,
      mermaid: false,
      banner: false,
      userGroups: false,
      pwa: false,
      promptLibrary: false,
      modelCatalog: false,
      presets: false,
      masks: false,
      temporaryChat: false,
      conversationPinStar: false,
      messageVersioning: false,
      mcp: false,
      agentMarket: false,
      minapps: false,
      openWebuiHome: false,
      researchAssistant: false,
      multimodalUpload: false,
    },
  },
}

const currentBrand = import.meta.env.VITE_BRAND || 'brand-a'

export const config: BrandConfig = brandConfigs[currentBrand] || brandConfigs['brand-a']

// 通用配置
export const APP_CONFIG = {
  // 模型配置
  defaultModel: 'gpt-5.4-mini',
  multimodalModel: 'gpt-5.4',

  // 验证码倒计时
  codeCountdown: 60,

  // 请求超时 (ms)
  timeout: 600000,

  // 聊天消息限制
  maxMessageLength: 20000,

  // 文件上传限制 (bytes)
  maxFileSize: 100 * 1024 * 1024, // 100MB

  // WS 心跳间隔 (ms)
  wsHeartbeatInterval: 30000,

  // WS 重连最大次数
  wsMaxReconnect: 5,

  // 游客消息条数限制；0 表示不限制
  guestMessageLimit: 0,

  // 登录成功后跳转页
  loginSuccessPage: '/pages/index/index',

  // AI 名称
  aiName: 'AI 助手',

  // 免责声明
  disclaimer: '内容由 AI 生成，仅供参考',
} as const
