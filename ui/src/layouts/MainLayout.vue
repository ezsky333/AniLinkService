<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <header class="nav-header">
      <div class="nav-container">
        <!-- Logo 和站点名称 -->
        <div class="nav-logo">
          <router-link to="/" class="logo-link">
            <i class="mdi mdi-magic-staff"></i>
            <span class="site-name">{{ siteConfig?.siteName || 'AniLink' }}</span>
          </router-link>
        </div>

        <!-- 中间导航菜单 -->
        <nav class="nav-menu">
          <router-link to="/" class="nav-item" exact-active-class="active">首页</router-link>
          <router-link to="/search" class="nav-item" active-class="active">发现</router-link>
          <a href="#" class="nav-item">库</a>
        </nav>

        <!-- 搜索框和用户菜单 -->
        <div class="nav-right">
          <!-- 搜索框 -->
          <div class="search-box">
            <i class="mdi mdi-magnify"></i>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索动画..."
              @keyup.enter="handleSearch"
            />
          </div>

          <!-- 用户菜单 -->
          <div class="user-menu-wrapper">
            <button class="user-btn" @click="userMenuOpen = !userMenuOpen">
              <i class="mdi mdi-account"></i>
            </button>
            <div v-if="userMenuOpen" class="user-dropdown">
              <a v-if="!isLoggedIn" href="#" @click.prevent="showLoginDialog = true" class="dropdown-item">
                <i class="mdi mdi-login"></i>
                <span>登录</span>
              </a>

              <template v-else>
                <div class="dropdown-header">
                  <span class="username">{{ currentUser }}</span>
                </div>
                <a href="#" @click.prevent="goToProfile" class="dropdown-item">
                  <i class="mdi mdi-account-circle"></i>
                  <span>个人中心</span>
                </a>
                <div class="dropdown-divider"></div>
              </template>

              <a v-if="isAdmin" href="#" @click.prevent="goToAdmin" class="dropdown-item">
                <i class="mdi mdi-cog"></i>
                <span>后台管理</span>
              </a>

              <a v-if="isLoggedIn" href="#" @click.prevent="handleLogout" class="dropdown-item logout">
                <i class="mdi mdi-logout"></i>
                <span>登出</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="main-content">
      <div class="content-wrapper">
        <router-view />
      </div>
    </main>

    <!-- 登录对话框 -->
    <div v-if="showLoginDialog" class="login-modal-overlay" @click="showLoginDialog = false">
      <div class="login-modal" @click.stop>
        <div class="login-header">
          <h2>用户登录</h2>
          <button class="close-btn" @click="showLoginDialog = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>

        <div class="login-body">
          <input
            v-model="loginForm.username"
            type="text"
            placeholder="用户名"
            class="login-input"
            @keyup.enter="handleLogin"
          />
          <input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            class="login-input"
            @keyup.enter="handleLogin"
          />
        </div>

        <div class="login-footer">
          <button class="btn-cancel" @click="showLoginDialog = false">取消</button>
          <button class="btn-login" @click="handleLogin" :disabled="loginLoading">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const API_BASE = '/api'
const DEFAULT_SITE_NAME = 'AniLink'
const router = useRouter()
const searchQuery = ref('')
const showLoginDialog = ref(false)
const loginLoading = ref(false)
const userMenuOpen = ref(false)
const siteConfig = ref(null)
const userInfo = ref(null)

const loginForm = ref({
  username: '',
  password: ''
})

// 配置axios请求拦截器，自动添加token
axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      const userData = res.data.data
      localStorage.setItem('userInfo', JSON.stringify(userData))
      userInfo.value = userData
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    // 如果获取失败，清除登录状态
    handleLogout()
  }
}

// 检查登录状态
const checkLoginStatus = () => {
  const token = localStorage.getItem('token')
  const user = localStorage.getItem('userInfo')
  if (token) {
    if (user) {
      try {
        userInfo.value = JSON.parse(user)
      } catch (e) {
        console.error('解析用户信息失败:', e)
      }
    }
    // 获取最新的用户信息
    fetchUserInfo()
  }
}

const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token') && !!userInfo.value
})

const currentUser = computed(() => {
  return userInfo.value?.username || ''
})

const isAdmin = computed(() => {
  if (!userInfo.value || !userInfo.value.roleCodeList) return false
  return userInfo.value.roleCodeList.includes('super-admin')
})

const syncDocumentTitle = () => {
  if (typeof document === 'undefined') {
    return
  }
  document.title = siteConfig.value?.siteName || DEFAULT_SITE_NAME
}

watch(
  () => siteConfig.value?.siteName,
  () => {
    syncDocumentTitle()
  },
  { immediate: true }
)

onMounted(() => {
  loadSiteConfig()
  checkLoginStatus()
})

const loadSiteConfig = () => {
  try {
    const stored = localStorage.getItem('siteConfig')
    if (stored) {
      siteConfig.value = JSON.parse(stored)
    }
  } catch (e) {
    console.error('解析本地配置失败:', e)
  }
}

const handleSearch = () => {
  const keyword = searchQuery.value.trim()
  router.push({
    path: '/search',
    query: keyword ? { q: keyword, page: '1' } : { page: '1' }
  })
}

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    alert('请输入用户名和密码')
    return
  }

  loginLoading.value = true
  try {
    const res = await axios.post(`${API_BASE}/auth/login`, {
      username: loginForm.value.username,
      password: loginForm.value.password
    })

    if (res.data?.code === 200 && res.data?.data) {
      const { tokenValue } = res.data.data
      localStorage.setItem('token', tokenValue)
      
      if (loginForm.value.rememberMe) {
        localStorage.setItem('rememberMe', 'true')
      }

      // 登录成功后获取用户信息
      await fetchUserInfo()

      showLoginDialog.value = false
      loginForm.value = { username: '', password: '', rememberMe: false }
    } else {
      alert(res.data?.msg || '登录失败')
    }
  } catch (error) {
    alert(error.response?.data?.msg || '登录失败，请重试')
  } finally {
    loginLoading.value = false
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('rememberMe')
  userInfo.value = null
  userMenuOpen.value = false
  router.push('/')
}

const goToProfile = () => {
  userMenuOpen.value = false
  router.push('/profile')
}

const goToAdmin = () => {
  userMenuOpen.value = false
  router.push('/admin')
}
</script>

<style scoped>
:root {
  --primary-dark: #2e241e;
  --primary-light: #f9f5f0;
  --accent-brown: #b99a7e;
  --accent-red: #c45d2b;
  --accent-teal: #1e7b6b;
  --text-main: #2e2a26;
  --text-secondary: #6b5f55;
  --bg-cream: #faf8f5;
  --bg-beige: #f4eee7;
  --border-light: #e5d8cc;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  height: 100%;
  width: 100%;
  overflow: hidden;
  overflow-y: hidden;
}

#app {
  height: 100%;
  overflow: hidden;
}

.main-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(135deg, #faf8f5 0%, #f4eee7 50%, #ede8df 100%);
  overflow: hidden;
}

/* ===== Navigation Header ===== */
.nav-header {
  position: relative;
  height: 70px;
  background: #fdfbf9;
  border-bottom: 1px solid var(--border-light);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  z-index: 1000;
  flex-shrink: 0;
}

.nav-container {
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  max-width: 100%;
}

/* Logo */
.nav-logo {
  flex-shrink: 0;
}

.logo-link {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--primary-dark);
  font-weight: 700;
  font-size: 1.15rem;
  transition: 0.2s;
}

.logo-link:hover {
  color: var(--accent-red);
  transform: scale(1.05);
}

.logo-link i {
  font-size: 1.35rem;
  color: var(--accent-red);
}

.site-name {
  letter-spacing: 0.5px;
}

/* Menu */
.nav-menu {
  display: flex;
  gap: 32px;
  flex: 1;
  justify-content: center;
}

.nav-item {
  color: var(--text-secondary);
  text-decoration: none;
  font-weight: 500;
  font-size: 0.95rem;
  transition: color 0.2s ease, background-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
  position: relative;
  padding: 8px 12px;
  margin: 0 -12px;
  border-radius: 6px;
}

.nav-item:hover {
  color: var(--accent-red);
  background: rgba(196, 93, 43, 0.14);
  box-shadow: inset 0 0 0 1px rgba(196, 93, 43, 0.2);
  transform: translateY(-1px);
}

.nav-item::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 12px;
  right: 12px;
  width: auto;
  height: 3px;
  background: var(--accent-red);
  transform: scaleX(0);
  transition: transform 0.3s ease;
  border-radius: 1px;
}

.nav-item:hover::after {
  transform: scaleX(1);
}

.nav-item.active::after {
  transform: scaleX(1);
}

.nav-item.active {
  color: var(--accent-red);
  font-weight: 600;
  background: rgba(196, 93, 43, 0.12);
  box-shadow: inset 0 0 0 1px rgba(196, 93, 43, 0.18);
}

.nav-item:focus-visible {
  outline: 2px solid rgba(196, 93, 43, 0.45);
  outline-offset: 2px;
}

/* Right Section */
.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid #e0d5ca;
  border-radius: 24px;
  padding: 8px 16px;
  transition: 0.2s;
  width: 200px;
}

.search-box:focus-within {
  border-color: var(--accent-brown);
  box-shadow: 0 0 0 3px rgba(185, 154, 126, 0.08);
}

.search-box i {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.search-box input {
  border: none;
  background: transparent;
  outline: none;
  color: var(--text-main);
  font-size: 0.9rem;
  width: 100%;
}

.search-box input::placeholder {
  color: var(--text-secondary);
}

/* User Menu */
.user-menu-wrapper {
  position: relative;
}

.user-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e0d5ca;
  color: var(--text-secondary);
  font-size: 1.1rem;
  cursor: pointer;
  transition: 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-btn:hover {
  background: #faf8f5;
  color: var(--primary-dark);
  border-color: var(--accent-brown);
}

.user-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  background: white;
  border-radius: 12px;
  border: 1px solid var(--border-light);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 180px;
  margin-top: 8px;
  overflow: hidden;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-cream);
}

.username {
  color: var(--primary-dark);
  font-weight: 600;
  font-size: 0.9rem;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  color: var(--text-main);
  text-decoration: none;
  transition: 0.2s;
  font-size: 0.9rem;
}

.dropdown-item:hover {
  background: var(--bg-cream);
  color: var(--accent-red);
}

.dropdown-item i {
  font-size: 1rem;
  width: 20px;
  text-align: center;
}

.dropdown-item.logout {
  color: #c1162b;
  border-top: 1px solid var(--border-light);
}

.dropdown-item.logout:hover {
  background: rgba(193, 22, 43, 0.05);
}

.dropdown-divider {
  height: 1px;
  background: var(--border-light);
  margin: 4px 0;
}

/* Main Content */
.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.content-wrapper {
  padding: 24px 16px;
  width: 100%;
  max-width: 100%;
}

/* Login Modal */
.login-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.login-modal {
  background: #fdfbf9;
  border-radius: 16px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  animation: slideUp 0.3s ease;
  overflow: hidden;
  border: 1px solid #e0d5ca;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #e0d5ca;
  background: #fff;
}

.login-header h2 {
  font-size: 1.1rem;
  color: var(--primary-dark);
  margin: 0;
  font-weight: 700;
}

.close-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: 0.2s;
}

.close-btn:hover {
  color: var(--primary-dark);
  transform: rotate(90deg);
}

.login-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #fdfbf9;
}

.login-input {
  padding: 12px 16px;
  border: 1px solid #e0d5ca;
  border-radius: 8px;
  font-size: 0.95rem;
  color: var(--text-main);
  font-family: inherit;
  transition: 0.2s;
  background: #faf8f5;
}

.login-input:focus {
  outline: none;
  border-color: var(--accent-brown);
  background: #fff;
  box-shadow: 0 0 0 3px rgba(185, 154, 126, 0.08);
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: var(--text-secondary);
  cursor: pointer;
  margin-top: 4px;
}

.remember-me input {
  cursor: pointer;
  accent-color: var(--accent-brown);
}

.login-footer {
  padding: 16px 24px;
  border-top: 1px solid #e0d5ca;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  background: #fff;
}

.btn-cancel {
  padding: 8px 20px;
  border: 1px solid #e0d5ca;
  background: #fff;
  color: var(--text-secondary);
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
}

.btn-cancel:hover {
  background: #faf8f5;
  color: var(--primary-dark);
  border-color: var(--accent-brown);
}

.btn-login {
  padding: 10px 24px;
  border: none;
  background: #d9483f;
  color: white;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
  box-shadow: 0 2px 8px rgba(217, 72, 63, 0.35);
}

.btn-login:hover:not(:disabled) {
  background: #c23b2e;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(217, 72, 63, 0.45);
}

.btn-login:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Responsive */
@media (max-width: 920px) {
  .nav-header {
    height: auto;
    min-height: 70px;
  }

  .nav-container {
    padding: 8px 16px;
    gap: 12px;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .nav-logo {
    order: 1;
  }

  .nav-right {
    order: 2;
    margin-left: auto;
    justify-content: flex-end;
    flex-shrink: 0;
    white-space: nowrap;
  }

  .nav-menu {
    order: 3;
    flex: 0 0 100%;
    width: 100%;
    justify-content: center;
    gap: 10px;
    overflow-x: auto;
    white-space: nowrap;
    -ms-overflow-style: none;
    scrollbar-width: none;
  }

  .nav-menu::-webkit-scrollbar {
    display: none;
  }

  .nav-item {
    padding: 6px 10px;
    margin: 0;
  }

  .search-box {
    width: 140px;
  }
}

@media (max-width: 768px) {
  .nav-header {
    min-height: 60px;
  }

  .nav-container {
    padding: 8px 12px;
    gap: 8px;
  }

  .nav-right {
    margin-left: auto;
    justify-content: flex-end;
  }

  .nav-menu {
    gap: 8px;
  }

  .search-box {
    width: 110px;
    font-size: 0.85rem;
    padding: 6px 10px;
  }

  .search-box input::placeholder {
    font-size: 0.8rem;
  }

  .content-wrapper {
    padding: 16px 12px;
  }

  .user-btn {
    width: 36px;
    height: 36px;
    font-size: 0.85rem;
  }
}

@media (max-width: 600px) {
  .nav-header {
    min-height: 56px;
  }

  .nav-container {
    padding: 8px 10px;
    gap: 8px;
  }

  .logo-link {
    font-size: 1rem;
    gap: 6px;
  }

  .logo-link i {
    font-size: 1.1rem;
  }

  .site-name {
    display: inline-block;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 0.86rem;
  }

  .search-box {
    display: flex;
    width: 96px;
    padding: 6px 9px;
    gap: 6px;
  }

  .search-box input {
    font-size: 0.8rem;
  }

  .search-box input::placeholder {
    font-size: 0.75rem;
  }

  .nav-menu {
    gap: 6px;
  }

  .nav-item {
    font-size: 0.85rem;
    padding: 5px 8px;
  }

  .nav-right {
    gap: 8px;
    margin-left: auto;
    justify-content: flex-end;
  }

  .content-wrapper {
    padding: 12px 10px;
  }

  .user-btn {
    width: 32px;
    height: 32px;
    font-size: 0.8rem;
  }
}
</style>
