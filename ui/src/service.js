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
import { renderServiceLink, renderDetailsChip, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:km:confluence:url'
const PARAM_SPACE = 'service:km:confluence:space'

function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const space = params?.[PARAM_SPACE]
  if (!url || !space) return []
  const { t } = useI18nStore()
  return [renderServiceLink({ icon: 'mdi-home', href: `${url.replace(/\/$/, '')}/display/${encodeURIComponent(space)}`, title: t('service:km:confluence:space') })]
}

function renderDetailsKey(subscription) {
  const space = subscription?.parameters?.[PARAM_SPACE]
  if (!space) return null
  const { t } = useI18nStore()
  return renderDetailsChip({ icon: 'mdi-book-open-page-variant', text: space, title: t('service:km:confluence:space') })
}

export default { renderFeatures, renderDetailsKey }
