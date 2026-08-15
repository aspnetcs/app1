import { describe, expect, it } from 'vitest'
import { inferResearchFeatureStatusFromError } from './researchFeatureState'

describe('inferResearchFeatureStatusFromError', () => {
  it('treats backend disabled messages as disabled state', () => {
    expect(inferResearchFeatureStatusFromError({ message: 'research assistant is disabled' })).toBe('disabled')
    expect(inferResearchFeatureStatusFromError({ message: 'Research Assistant is not enabled' })).toBe('disabled')
    expect(inferResearchFeatureStatusFromError({ message: 'Literature search is not enabled' })).toBe('disabled')
    expect(inferResearchFeatureStatusFromError({ data: { message: 'research assistant is disabled' } })).toBe('disabled')
  })

  it('keeps unrelated failures as unknown', () => {
    expect(inferResearchFeatureStatusFromError(new Error('network failed'))).toBe('unknown')
  })
})
