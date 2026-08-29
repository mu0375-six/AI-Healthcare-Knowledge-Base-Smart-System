import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { provideGlobalConfig } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import { initTheme } from './utils/theme'
import { vReveal } from './directives/reveal'
import './styles/index.css'

initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
provideGlobalConfig({ locale: zhCn }, app, true)
app.directive('reveal', vReveal)
app.mount('#app')
