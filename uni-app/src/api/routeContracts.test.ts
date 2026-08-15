import { describe, expect, it } from 'vitest'
import { buildPlatformApiPath } from './platformUserRouteContract'
import { PLATFORM_ASSET_ROUTE_CONTRACT } from './platformAssetRouteContract'
import { PLATFORM_AUTH_ROUTE_CONTRACT, PLATFORM_RISK_ROUTE_CONTRACT } from './platformAuthRouteContract'
import {
  PLATFORM_CHAT_ROUTE_CONTRACT,
  buildChatCompletionsSseUrl,
  buildChatCompatUrl,
  buildConversationForkPath,
} from './platformChatRouteContract'
import { PLATFORM_MEDIA_ROUTE_CONTRACT } from './platformMediaRouteContract'
import { buildHistorySearchPath, PLATFORM_HISTORY_ROUTE_CONTRACT } from './platformHistoryRouteContract'
import {
  PLATFORM_CODETOOLS_ROUTE_CONTRACT,
  buildCodeToolsTaskArtifactsPath,
  buildCodeToolsTaskLogsPath,
  buildCodeToolsTaskPath,
} from './platformCodeToolsRouteContract'

describe('platform route contracts', () => {
  it('buildPlatformApiPath normalizes leading/trailing slashes', () => {
    expect(buildPlatformApiPath('chat/completions')).toBe('/v1/chat/completions')
    expect(buildPlatformApiPath('/chat/completions/')).toBe('/v1/chat/completions')
    expect(buildPlatformApiPath('')).toBe('/v1')
  })

  it('PLATFORM_CHAT_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_CHAT_ROUTE_CONTRACT.completions).toBe('/v1/chat/completions')
    expect(PLATFORM_CHAT_ROUTE_CONTRACT.completionsMulti).toBe('/v1/chat/completions/multi')
    expect(PLATFORM_CHAT_ROUTE_CONTRACT.completionsSse).toBe('/v1/chat/completions/sse')
  })

  it('PLATFORM_ASSET_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_ASSET_ROUTE_CONTRACT.presign).toBe('/v1/asset/presign')
    expect(PLATFORM_ASSET_ROUTE_CONTRACT.confirm).toBe('/v1/asset/confirm')
  })

  it('PLATFORM_MEDIA_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_MEDIA_ROUTE_CONTRACT.audioSpeech).toBe('/v1/audio/speech')
    expect(PLATFORM_MEDIA_ROUTE_CONTRACT.exportChatImageConfig).toBe('/v1/export/chat-image-config')
  })

  it('PLATFORM_AUTH_ROUTE_CONTRACT and PLATFORM_RISK_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_AUTH_ROUTE_CONTRACT.guest).toBe('/v1/auth/guest')
    expect(PLATFORM_AUTH_ROUTE_CONTRACT.smsLogin).toBe('/v1/auth/sms/login')
    expect(PLATFORM_AUTH_ROUTE_CONTRACT.me).toBe('/v1/auth/me')
    expect(PLATFORM_RISK_ROUTE_CONTRACT.captchaGenerate).toBe('/v1/risk/captcha/generate')
    expect(PLATFORM_RISK_ROUTE_CONTRACT.captchaVerify).toBe('/v1/risk/captcha/verify')
  })

  it('buildChatCompletionsSseUrl uses /api base and appends the route contract path', () => {
    expect(buildChatCompletionsSseUrl('http://localhost:8080/api')).toBe(
      'http://localhost:8080/api/v1/chat/completions/sse'
    )
    expect(buildChatCompletionsSseUrl('http://localhost:8080/api/')).toBe(
      'http://localhost:8080/api/v1/chat/completions/sse'
    )
  })

  it('buildChatCompatUrl strips trailing /api and targets OpenAI gateway path', () => {
    expect(buildChatCompatUrl('http://localhost:8080/api')).toBe('http://localhost:8080/v1/chat/completions')
    expect(buildChatCompatUrl('http://localhost:8080/api/')).toBe('http://localhost:8080/v1/chat/completions')
  })

  it('buildConversationForkPath encodes the conversation id', () => {
    expect(buildConversationForkPath('a b')).toBe('/v1/conversations/a%20b/fork')
  })

  it('PLATFORM_HISTORY_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_HISTORY_ROUTE_CONTRACT.topics).toBe('/v1/history/topics')
    expect(PLATFORM_HISTORY_ROUTE_CONTRACT.messages).toBe('/v1/history/messages')
    expect(PLATFORM_HISTORY_ROUTE_CONTRACT.files).toBe('/v1/history/files')
  })

  it('PLATFORM_CODETOOLS_ROUTE_CONTRACT paths are stable', () => {
    expect(PLATFORM_CODETOOLS_ROUTE_CONTRACT.tasks).toBe('/v1/code-tools/tasks')
    expect(buildCodeToolsTaskPath('a b')).toBe('/v1/code-tools/tasks/a%20b')
    expect(buildCodeToolsTaskLogsPath('a b')).toBe('/v1/code-tools/tasks/a%20b/logs')
    expect(buildCodeToolsTaskArtifactsPath('a b')).toBe('/v1/code-tools/tasks/a%20b/artifacts')
  })

  it('buildHistorySearchPath encodes keyword and topic filters', () => {
    expect(buildHistorySearchPath('messages', { keyword: 'hello world', topicId: 'a b', page: 1, size: 10 })).toBe(
      '/v1/history/messages?keyword=hello+world&page=1&size=10&topicId=a+b'
    )
  })
})
