import { describe, expect, it } from 'vitest'
import { readTraceIdFromHeaders, readTraceIdFromPayload } from './traceId'

describe('traceId', () => {
  it('reads X-Trace-Id from headers (string, lower-case key)', () => {
    expect(readTraceIdFromHeaders({ 'x-trace-id': 't_123' })).toBe('t_123')
  })

  it('reads X-Trace-Id from headers (string, mixed-case key)', () => {
    expect(readTraceIdFromHeaders({ 'X-Trace-Id': 't_abc' })).toBe('t_abc')
  })

  it('reads X-Trace-Id from headers (array value)', () => {
    expect(readTraceIdFromHeaders({ 'x-trace-id': [' ', 't_arr'] })).toBe('t_arr')
  })

  it('reads X-Trace-Id from headers via case-insensitive scan', () => {
    expect(readTraceIdFromHeaders({ 'X-TRACE-ID': 't_scan' })).toBe('t_scan')
  })

  it('reads X-Trace-Id from headers with underscore key', () => {
    expect(readTraceIdFromHeaders({ x_trace_id: 't_us' })).toBe('t_us')
  })

  it('returns null when trace header missing/blank', () => {
    expect(readTraceIdFromHeaders({})).toBeNull()
    expect(readTraceIdFromHeaders({ 'x-trace-id': '   ' })).toBeNull()
  })

  it('reads traceId from streaming payload (traceId/trace_id/x-trace-id)', () => {
    expect(readTraceIdFromPayload({ traceId: 't_1' })).toBe('t_1')
    expect(readTraceIdFromPayload({ traceID: 't_1b' })).toBe('t_1b')
    expect(readTraceIdFromPayload({ trace_id: 't_2' })).toBe('t_2')
    expect(readTraceIdFromPayload({ 'x-trace-id': 't_3' })).toBe('t_3')
  })

  it('reads traceId from payload via normalization scan', () => {
    expect(readTraceIdFromPayload({ 'TRACE_ID': 't_scan' })).toBe('t_scan')
    expect(readTraceIdFromPayload({ 'X_TRACE_ID': 't_scan2' })).toBe('t_scan2')
  })

  it('returns null when payload is not an object', () => {
    expect(readTraceIdFromPayload(null)).toBeNull()
    expect(readTraceIdFromPayload('x')).toBeNull()
  })
})
