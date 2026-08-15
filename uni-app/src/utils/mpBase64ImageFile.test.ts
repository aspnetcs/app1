import { describe, expect, it, vi } from 'vitest'
import {
  createMpBase64ImageFileManager,
  parseBase64ImageDataUrl,
  resolveMiniProgramUserDataPath,
  writeMiniProgramBase64ImageFile,
} from './mpBase64ImageFile'

describe('mpBase64ImageFile', () => {
  it('prefers wx env user data path when both runtimes expose one', () => {
    expect(
      resolveMiniProgramUserDataPath(
        { USER_DATA_PATH: '/uni-data' },
        { USER_DATA_PATH: '/wx-data' },
      ),
    ).toBe('/wx-data')
  })

  it('falls back to uni env when wx env is unavailable', () => {
    expect(
      resolveMiniProgramUserDataPath(
        { USER_DATA_PATH: '/uni-data' },
        undefined,
      ),
    ).toBe('/uni-data')
  })

  it('parses a base64 image data url', () => {
    expect(parseBase64ImageDataUrl('data:image/png;base64,AAA')).toEqual({
      mime: 'image/png',
      base64: 'AAA',
    })
  })

  it('returns null when data url is not a base64 image', () => {
    expect(parseBase64ImageDataUrl('https://example.com/a.png')).toBeNull()
    expect(parseBase64ImageDataUrl('data:text/plain;base64,AAA')).toBeNull()
    expect(parseBase64ImageDataUrl('data:image/png,AAA')).toBeNull()
  })

  it('writes base64 into the mini-program user data directory', async () => {
    const writeFile = vi.fn((options: { success: () => void }) => {
      options.success()
    })

    const filePath = await writeMiniProgramBase64ImageFile({
      base64: 'AAA',
      mime: 'image/png',
      fileSystemManager: { writeFile },
      wxEnv: { USER_DATA_PATH: '/wx-data' },
      now: () => 123,
      random: () => 'r',
      seq: 7,
      prefix: 'img',
    })

    expect(filePath).toBe('/wx-data/img_123_7_r.png')
    expect(writeFile).toHaveBeenCalledWith(
      expect.objectContaining({
        filePath: '/wx-data/img_123_7_r.png',
        data: 'AAA',
        encoding: 'base64',
      }),
    )
  })

  it('throws when missing a writable user data directory', async () => {
    await expect(
      writeMiniProgramBase64ImageFile({
        base64: 'AAA',
        mime: 'image/png',
        fileSystemManager: { writeFile: vi.fn() },
      }),
    ).rejects.toThrow('missing user data path')
  })

  it('tracks temp files and unlinks them on cleanup', async () => {
    const writeFile = vi.fn((options: { success: () => void }) => {
      options.success()
    })
    const unlink = vi.fn((options: { filePath: string; fail: () => void }) => {
      // cleanup is best-effort; calling fail/noop matches the production pattern
      options.fail()
    })

    const manager = createMpBase64ImageFileManager({
      fileSystemManager: { writeFile, unlink },
      wxEnv: { USER_DATA_PATH: '/wx-data' },
      now: () => 1,
      random: () => 'r',
    })

    const file1 = await manager.writeBase64ToTempFilePath('AAA', 'image/png', 'comfy')
    const file2 = await manager.resolveDataUrlToTempFilePath('data:image/png;base64,BBB', 'comfy')

    expect(file1).toBe('/wx-data/comfy_1_0_r.png')
    expect(file2).toBe('/wx-data/comfy_1_1_r.png')
    expect(manager.listTempFiles()).toEqual([file1, file2])

    manager.cleanupTempFiles()
    expect(unlink).toHaveBeenCalledTimes(2)
    expect(unlink).toHaveBeenCalledWith(expect.objectContaining({ filePath: file1 }))
    expect(unlink).toHaveBeenCalledWith(expect.objectContaining({ filePath: file2 }))
    expect(manager.listTempFiles()).toEqual([])
  })
})

