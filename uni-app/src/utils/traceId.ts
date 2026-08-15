function normalizeKey(key: string): string {
  return key.trim().toLowerCase().replace(/[_-]+/g, '')
}

function firstNonBlankString(value: unknown): string | null {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed ? trimmed : null
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      if (typeof item !== 'string') continue
      const trimmed = item.trim()
      if (trimmed) return trimmed
    }
  }
  return null
}

export function readTraceIdFromHeaders(header: Record<string, unknown> | null | undefined): string | null {
  if (!header || typeof header !== 'object') return null

  // Common uni.request header shapes vary by runtime; try direct keys first.
  const direct =
    (header as Record<string, unknown>)['x-trace-id'] ??
    (header as Record<string, unknown>)['X-Trace-Id'] ??
    (header as Record<string, unknown>)['x-traceid'] ??
    (header as Record<string, unknown>)['X-TraceId'] ??
    (header as Record<string, unknown>)['x_trace_id'] ??
    (header as Record<string, unknown>)['X_Trace_Id']
  const directValue = firstNonBlankString(direct)
  if (directValue) return directValue

  // Fallback: case-insensitive scan with normalization. (MP-WEIXIN may lowercase keys)
  for (const [k, v] of Object.entries(header)) {
    if (normalizeKey(String(k)) !== 'xtraceid') continue
    const value = firstNonBlankString(v)
    if (value) return value
  }
  return null
}

export function readTraceIdFromPayload(payload: unknown): string | null {
  if (!payload || typeof payload !== 'object') return null
  const record = payload as Record<string, unknown>
  const direct =
    record.traceId ??
    record.traceID ??
    record.trace_id ??
    record['x-trace-id'] ??
    record['x_trace_id']

  const directValue = firstNonBlankString(direct)
  if (directValue) return directValue

  // Fallback: support unexpected casing/format from WS frames (e.g. trace_id / TRACEID).
  for (const [k, v] of Object.entries(record)) {
    const nk = normalizeKey(String(k))
    if (nk !== 'traceid' && nk !== 'xtraceid') continue
    const value = firstNonBlankString(v)
    if (value) return value
  }

  return null
}
