/*
 * Plugin "km-confluence" — Confluence implementation of plugin-km.
 *
 * Tool-level plugin (`service:km:confluence`). Augments the parent
 * `plugin-km` via i18n parameter labels + row features (space link,
 * space chip) merged in through plugin-km's `subPluginIdFor` delegation.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
}

export default {
  id: 'km-confluence',
  label: 'Confluence',
  requires: ['km'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "km-confluence" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-confluence', color: 'blue-darken-3' },
}

export { service }
