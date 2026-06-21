import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-km-confluence contract', () => {
  it('exposes a valid tool-level manifest', () => {
    expect(def.id).toBe('km-confluence')
    expect(def.requires).toEqual(['km'])
    expect(def.routes).toBeUndefined()
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })
  it('merges i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:km:confluence:space')).toBe('Space')
  })
  it('throws for an unknown feature', () => {
    expect(() => def.feature('nope')).toThrow(/no feature "nope"/)
  })
  it('renderFeatures returns the space link when url + space are set', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      parameters: { 'service:km:confluence:url': 'https://wiki.example.org', 'service:km:confluence:space': 'DIG' },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].props.href).toBe('https://wiki.example.org/display/DIG')
    expect(vnodes[0].props.target).toBe('_blank')
  })
  it('renderFeatures returns [] without url or space', () => {
    def.install()
    expect(def.feature('renderFeatures', { parameters: { 'service:km:confluence:url': 'x' } })).toEqual([])
    expect(def.feature('renderFeatures', {})).toEqual([])
  })
  it('renderDetailsKey returns the space chip when present', () => {
    def.install()
    expect(def.feature('renderDetailsKey', { parameters: { 'service:km:confluence:space': 'DIG' } })).toBeTruthy()
    expect(def.feature('renderDetailsKey', { parameters: {} })).toBeNull()
  })
})
