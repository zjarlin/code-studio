import './styles.css'
import './features/api-studio/api-studio.css'

import { createApp } from 'vue'

import App from './App.vue'
import { installAssetRecovery } from './lib/asset-recovery'

installAssetRecovery(window, () => window.location.reload())
createApp(App).mount('#app')
