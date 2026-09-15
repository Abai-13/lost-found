import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

// ⚠️ 顺序不能换：theme.css 必须在 element-plus 样式**之后**引入。
// CSS 里同优先级的规则后写的赢，先引 theme.css 会被 Element 的默认样式盖掉。
import 'element-plus/dist/index.css'
import './styles/theme.css'

const app = createApp(App)

// 图标全量注册成全局组件，页面里直接 <el-icon><Search /></el-icon> 就能用，
// 不用每个文件单独 import。图标包体积很小，全量注册换来的便利更划算。
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.use(ElementPlus)
app.use(router)
app.mount('#app')
