import { describe, expect, it } from 'vitest'
import { inferFileExtension, inferFileKind, inferFileKindFromMime, inferFileKindFromName } from './fileKind'

describe('attachments fileKind', () => {
  it('infers extension', () => {
    expect(inferFileExtension('a.PDF')).toBe('pdf')
    expect(inferFileExtension('noext')).toBe('')
    expect(inferFileExtension('')).toBe('')
  })

  it('infers kind from mime', () => {
    expect(inferFileKindFromMime('image/png')).toBe('image')
    expect(inferFileKindFromMime('image/svg+xml')).toBe('image')
    expect(inferFileKindFromMime('audio/mpeg')).toBe('audio')
    expect(inferFileKindFromMime('video/mp4')).toBe('video')
    expect(inferFileKindFromMime('application/pdf')).toBe('document')
    expect(inferFileKindFromMime('application/vnd.openxmlformats-officedocument.presentationml.presentation')).toBe('document')
    expect(inferFileKindFromMime('application/json')).toBe('document')
    expect(inferFileKindFromMime('text/csv')).toBe('document')
    expect(inferFileKindFromMime('application/octet-stream')).toBe('other')
  })

  it('infers kind from name', () => {
    expect(inferFileKindFromName('x.png')).toBe('image')
    expect(inferFileKindFromName('x.mp3')).toBe('audio')
    expect(inferFileKindFromName('x.mp4')).toBe('video')
    expect(inferFileKindFromName('x.pdf')).toBe('document')
    expect(inferFileKindFromName('x.pptx')).toBe('document')
    expect(inferFileKindFromName('x.csv')).toBe('document')
    expect(inferFileKindFromName('x.markdown')).toBe('document')
    expect(inferFileKindFromName('x.html')).toBe('document')
    expect(inferFileKindFromName('x.unknown')).toBe('other')
  })

  it('prefers mime, falls back to name', () => {
    expect(inferFileKind({ mimeType: 'image/jpeg', filename: 'x.pdf' })).toBe('image')
    expect(inferFileKind({ mimeType: '', filename: 'x.pdf' })).toBe('document')
  })
})

