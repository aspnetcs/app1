import { describe, expect, it, vi } from 'vitest'
import {
  playMiniProgramTtsAudio,
  resolveMiniProgramUserDataPath,
  writeMiniProgramTtsTempFile,
} from './useTts'

describe('useTts', () => {
  it('prefers the uni runtime temp directory when both runtimes expose one', () => {
    expect(
      resolveMiniProgramUserDataPath(
        { USER_DATA_PATH: '/uni-data' },
        { USER_DATA_PATH: '/wx-data' },
      ),
    ).toBe('/uni-data')
  })

  it('falls back to wx env when uni env is unavailable', () => {
    expect(
      resolveMiniProgramUserDataPath(
        undefined,
        { USER_DATA_PATH: '/wx-data' },
      ),
    ).toBe('/wx-data')
  })

  it('returns an empty string when neither runtime exposes a temp directory', () => {
    expect(resolveMiniProgramUserDataPath(undefined, undefined)).toBe('')
  })

  it('writes MP audio into wx temp storage when uni env is unavailable', async () => {
    const writeFile = vi.fn((options: { success: () => void }) => {
      options.success()
    })

    const filePath = await writeMiniProgramTtsTempFile({
      base64: 'ZmFrZS1hdWRpbw==',
      contentType: 'audio/mpeg',
      fileSystemManager: {
        writeFile,
      },
      wxEnv: { USER_DATA_PATH: '/wx-data' },
      now: () => 123,
    })

    expect(filePath).toBe('/wx-data/tts_123.mp3')
    expect(writeFile).toHaveBeenCalledWith(
      expect.objectContaining({
        filePath: '/wx-data/tts_123.mp3',
        data: 'ZmFrZS1hdWRpbw==',
        encoding: 'base64',
      }),
    )
  })

  it('throws when MP audio cannot resolve a writable temp directory', async () => {
    await expect(
      writeMiniProgramTtsTempFile({
        base64: 'ZmFrZS1hdWRpbw==',
        contentType: 'audio/wav',
        fileSystemManager: {
          writeFile: vi.fn(),
        },
      }),
    ).rejects.toThrow('missing user data path')
  })

  it('assigns the MP temp file to the audio context and starts playback', async () => {
    const writeFile = vi.fn((options: { success: () => void }) => {
      options.success()
    })
    const audioContext = {
      src: '',
      play: vi.fn(),
    }

    const filePath = await playMiniProgramTtsAudio({
      base64: 'ZmFrZS1hdWRpbw==',
      contentType: 'audio/wav',
      fileSystemManager: {
        writeFile,
      },
      audioContext,
      wxEnv: { USER_DATA_PATH: '/wx-data' },
      now: () => 456,
    })

    expect(filePath).toBe('/wx-data/tts_456.wav')
    expect(audioContext.src).toBe('/wx-data/tts_456.wav')
    expect(audioContext.play).toHaveBeenCalledTimes(1)
  })

  it('does not start playback when writing the MP temp file fails', async () => {
    const audioContext = {
      src: '',
      play: vi.fn(),
    }

    await expect(
      playMiniProgramTtsAudio({
        base64: 'ZmFrZS1hdWRpbw==',
        contentType: 'audio/aac',
        fileSystemManager: {
          writeFile: vi.fn((options: { fail: (err: unknown) => void }) => {
            options.fail(new Error('disk full'))
          }),
        },
        audioContext,
        wxEnv: { USER_DATA_PATH: '/wx-data' },
      }),
    ).rejects.toThrow('disk full')

    expect(audioContext.src).toBe('')
    expect(audioContext.play).not.toHaveBeenCalled()
  })
})
