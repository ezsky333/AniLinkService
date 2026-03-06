import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/install',
    name: 'Install',
    component: () => import('../views/Install.vue')
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/Admin.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  let installed = localStorage.getItem('installed')
  const token = localStorage.getItem('token')

  // 若状态不存在，则从接口获取，避免频繁请求 siteConfig 的同时确保状态可靠
  if (installed === null) {
    try {
      const res = await axios.get('/api/site/config')
      const isInstalled = res.data?.data?.installed === true
      installed = isInstalled ? 'true' : 'false'
      if (isInstalled) {
        localStorage.setItem('installed', 'true')
      } else {
        localStorage.removeItem('installed')
      }
    } catch (err) {
      console.error('获取安装状态失败:', err)
      // 默认认为未安装，清理 localStorage
      localStorage.removeItem('installed')
      installed = 'false'
    }
  }

  // 如果已安装，访问安装页跳转到首页
  if (installed === 'true' && to.path === '/install') {
    return next('/')
  }

  // 如果未安装，跳转到安装页
  if (installed !== 'true' && to.path !== '/install') {
    return next('/install')
  }

  // 检查需要认证的路由
  if (to.meta.requiresAuth && !token) {
    return next('/')
  }

  next()
})

export default router
