import { describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => {
  return {
    http: {
      post: vi.fn(),
    },
  }
})

import { http } from './http'
import { chatCompletions } from './chat'

describe('chat api trace meta', () => {
  it('passes X-Trace-Id header when meta.traceId provided', async () => {
    const post = http.post as unknown as ReturnType<typeof vi.fn>
    post.mockResolvedValue({ code: 0, message: '', data: { requestId: 'req_1' } })

    await chatCompletions(
      { model: 'm', messages: [{ role: 'user', content: 'hi' }], stream: true },
      { traceId: 't_client' },
    )

    const call = post.mock.calls[0]
    expect(call).toBeTruthy()
    const options = call?.[2]
    expect(options?.headers?.['X-Trace-Id']).toBe('t_client')
  })

  it('invokes meta.onTraceId when response header contains X-Trace-Id', async () => {
    const post = http.post as unknown as ReturnType<typeof vi.fn>
    post.mockImplementation((_path: string, _data: unknown, options: any) => {
      options?.onResponse?.({ statusCode: 200, header: { 'x-trace-id': 't_server' } })
      return Promise.resolve({ code: 0, message: '', data: { requestId: 'req_2' } })
    })

    const onTraceId = vi.fn()
    await chatCompletions(
      { model: 'm', messages: [{ role: 'user', content: 'hi' }], stream: true },
      { traceId: 't_client', onTraceId },
    )

    expect(onTraceId).toHaveBeenCalledWith('t_server')
  })
})

