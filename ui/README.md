# plugin-km-confluence — Vue UI

Tool plugin (`service:km:confluence`), the Confluence implementation of the
`km` service. Compiled to `webjars/km-confluence/vue/`.

Ships i18n parameter labels + `renderFeatures` (space link
`url/display/<space>`) and `renderDetailsKey` (space chip).
`requires: ['km']`. The legacy activity avatar and the sidebar
`renderGlobal` space-links list (a cross-domain fetch) are deferred.

```bash
npm install && npm run build && npm run lint && npm test
```
