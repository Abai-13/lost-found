import { createRouter, createWebHistory } from 'vue-router'

// 路由表：路径 → 页面组件
// component 用 () => import(...) 懒加载，访问到才下载这个页面
const routes = [
  { path: '/', redirect: '/items' },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/items', component: () => import('../views/ItemList.vue') },
  { path: '/items/:id', component: () => import('../views/ItemDetail.vue') },
  { path: '/publish', component: () => import('../views/Publish.vue') },
  { path: '/ai', component: () => import('../views/AiChat.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：访问需要登录的页面时，先检查有没有 token
router.beforeEach((to) => {
  const needLogin = ['/publish', '/ai'].includes(to.path)
  if (needLogin && !localStorage.getItem('token')) {
    return '/login'
  }
})

export default router
