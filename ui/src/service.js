/*
 * Service layer for plugin "km-confluence".
 *
 * Tool-level plugin (`service:km:confluence`). The parent `plugin-km`
 * delegates the subscription-row hooks to us. Mirrors `confluence.js`:
 *   - renderFeatures   → a link to the Confluence space (url/display/<space>).
 *   - renderDetailsKey → the space chip.
 *
 * The legacy `renderDetailsFeatures` (activity avatar) and `renderGlobal`
 * (sidebar space-links list, a cross-domain fetch) read live data and are
 * deferred. Kept free of Vue SFC imports for unit testing.
 */
import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:km:confluence:url'
const PARAM_SPACE = 'service:km:confluence:space'

function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const space = params?.[PARAM_SPACE]
  if (!url || !space) return []
  const { t } = useI18nStore()
  return [
    h(
      VBtn,
      {
        icon: true,
        size: 'small',
        variant: 'text',
        href: `${url.replace(/\/$/, '')}/display/${encodeURIComponent(space)}`,
        target: '_blank',
        rel: 'noopener noreferrer',
        title: t('service:km:confluence:space'),
      },
      () => h(VIcon, { size: 'small' }, () => 'mdi-home'),
    ),
  ]
}

function renderDetailsKey(subscription) {
  const space = subscription?.parameters?.[PARAM_SPACE]
  if (!space) return null
  const { t } = useI18nStore()
  return h(
    VChip,
    { size: 'small', variant: 'tonal', class: 'mr-1', title: t('service:km:confluence:space') },
    () => [h(VIcon, { start: true, size: 'small' }, () => 'mdi-book-open-page-variant'), ' ', String(space)],
  )
}

export default { renderFeatures, renderDetailsKey }
