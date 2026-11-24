import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/layout.css'

// 旧版本浏览足迹保存在 localStorage，现已改为服务端按账号存储，启动时清理历史残留
try { localStorage.removeItem('star_footprints') } catch (e) { /* ignore */ }

const app = createApp(App)

// 注册全部图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')