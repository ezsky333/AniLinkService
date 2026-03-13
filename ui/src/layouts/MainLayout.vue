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

          <router-link v-if="isRemoteAccessVisible" to="/remote-access" class="nav-item" active-class="active">远程访问</router-link>
          <router-link v-if="isLoggedIn" to="/profile" class="nav-item" active-class="active">个人中心</router-link>
        </nav>

        <!-- 搜索框和用户菜单 -->
        <div class="nav-right">
          <!-- 消息按钮和下拉 -->
          <div v-if="isLoggedIn" class="message-wrapper">
            <button @click="handleMessageBtnClick" class="message-btn" :title="`消息${unreadCount > 0 ? ' (' + unreadCount + ')' : ''}`">
              <i class="mdi mdi-bell"></i>
              <span v-if="unreadCount > 0" class="unread-dot"></span>
            </button>
            
            <!-- 消息下拉窗 -->
            <div v-if="messageMenuOpen" class="message-dropdown" @click.stop>
              <div class="message-dropdown-header">
                <span>消息通知</span>
                <div class="message-header-actions">
                  <span v-if="unreadCount > 0" class="unread-count-badge">{{ unreadCount }}</span>
                  <button
                    v-if="unreadCount > 0"
                    class="mark-all-read-btn"
                    :disabled="markingAllRead"
                    @click="handleMarkAllAsRead"
                  >
                    {{ markingAllRead ? '处理中...' : '一键已读' }}
                  </button>
                </div>
              </div>
              
              <div class="message-list">
                <div v-if="loadingMessages" class="message-loading">
                  <i class="mdi mdi-loading mdi-spin"></i>
                  <span>加载中...</span>
                </div>
                
                <template v-else-if="recentMessages.length > 0">
                  <div
                    v-for="msg in recentMessages"
                    :key="msg.id"
                    class="message-item"
                    :class="{ 'unread': !msg.isRead }"
                    @click="handleMessageClick(msg)"
                  >
                    <div class="message-item-indicator">
                      <span v-if="!msg.isRead" class="unread-indicator"></span>
                    </div>
                    <div class="message-item-content">
                      <div class="message-item-title">{{ msg.title }}</div>
                      <div class="message-item-text">{{ msg.content }}</div>
                      <div class="message-item-time">{{ formatMessageTime(msg.createdAt) }}</div>
                    </div>
                  </div>
                </template>
                
                <div v-else class="message-empty">
                  <i class="mdi mdi-bell-off-outline"></i>
                  <span>暂无消息</span>
                </div>
              </div>
              
              <div class="message-dropdown-footer">
                <button @click="messageMenuOpen = false; goToMessages()" class="view-all-messages-btn">
                  查看全部消息
                </button>
              </div>
            </div>
          </div>

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
              <a v-if="!isLoggedIn" href="#" @click.prevent="showLoginDialog = true; userMenuOpen = false" class="dropdown-item">
                <i class="mdi mdi-login"></i>
                <span>登录</span>
              </a>
              <a
                v-if="!isLoggedIn && isRegisterOpen"
                href="#"
                @click.prevent="openRegisterDialog(); userMenuOpen = false"
                class="dropdown-item"
              >
                <i class="mdi mdi-account-plus"></i>
                <span>注册</span>
              </a>

              <template v-if="isLoggedIn">
                <div class="dropdown-header">
                  <span class="username">{{ currentUser }}</span>
                </div>
                <a href="#" @click.prevent="goToProfile" class="dropdown-item">
                  <i class="mdi mdi-account-circle"></i>
                  <span>个人中心</span>
                </a>
                <a href="#" @click.prevent="goToFollows" class="dropdown-item">
                  <i class="mdi mdi-bookmark-multiple"></i>
                  <span>我的追番</span>
                </a>
                <div class="dropdown-divider"></div>
                <a v-if="isAdmin" href="#" @click.prevent="goToAdmin" class="dropdown-item">
                  <i class="mdi mdi-cog"></i>
                  <span>后台管理</span>
                </a>
                <a href="#" @click.prevent="handleLogout" class="dropdown-item logout">
                  <i class="mdi mdi-logout"></i>
                  <span>登出</span>
                </a>
              </template>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容区域 -->
    <main class="main-content">
      <div class="content-wrapper">
        <router-view />
        <p v-if="siteConfig?.siteDescription" class="site-description-note">
          {{ siteConfig.siteDescription }}
        </p>
      </div>
    </main>

    <!-- 登录对话框 -->
    <div v-if="showLoginDialog" class="login-modal-overlay" @click.self="showLoginDialog = false">
      <div class="login-modal" @click.stop>
        <div class="login-header">
          <h2>用户登录</h2>
          <button class="close-btn" @click="showLoginDialog = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>

        <div class="login-body">
          <input
            v-model="loginForm.account"
            type="text"
            placeholder="用户名或邮箱"
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
          <button v-if="isRegisterOpen" class="btn-cancel" @click="openRegisterDialog">去注册</button>
          <button class="btn-cancel" @click="showLoginDialog = false">取消</button>
          <button class="btn-login" @click="handleLogin" :disabled="loginLoading">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 注册对话框 -->
    <div v-if="showRegisterDialog" class="login-modal-overlay" @click.self="showRegisterDialog = false">
      <div class="login-modal" @click.stop>
        <div class="login-header">
          <h2>用户注册</h2>
          <button class="close-btn" @click="showRegisterDialog = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>

        <div class="login-body">
          <input v-model="registerForm.username" type="text" placeholder="用户名" class="login-input" />
          <input v-model="registerForm.email" type="email" placeholder="邮箱" class="login-input" />
          <input v-model="registerForm.password" type="password" placeholder="密码" class="login-input" />
          <input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            class="login-input"
          />
          <div class="captcha-row">
            <input
              v-model="registerForm.captchaCode"
              type="text"
              placeholder="图形验证码"
              class="login-input"
            />
            <img
              v-if="captchaImage"
              :src="captchaImageSrc"
              class="captcha-image"
              alt="captcha"
              @click="refreshCaptcha"
            />
          </div>
          <div class="captcha-row">
            <input v-model="registerForm.emailCode" type="text" placeholder="邮箱验证码" class="login-input" />
            <button class="btn-cancel send-code-btn" :disabled="sendCodeDisabled" @click="sendEmailCode">
              {{ sendCodeText }}
            </button>
          </div>
        </div>

        <div class="login-footer">
          <button class="btn-cancel" @click="openLoginDialog">去登录</button>
          <button class="btn-cancel" @click="showRegisterDialog = false">取消</button>
          <button class="btn-login" @click="handleRegister" :disabled="registerLoading">
            {{ registerLoading ? '注册中...' : '注册' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { showAppMessage } from '../utils/ui-feedback'

const API_BASE = '/api'
const DEFAULT_SITE_NAME = 'AniLink'
const router = useRouter()
const searchQuery = ref('')
const showLoginDialog = ref(false)
const showRegisterDialog = ref(false)
const loginLoading = ref(false)
const registerLoading = ref(false)
const sendCodeLoading = ref(false)
const sendCodeCountdown = ref(0)
const userMenuOpen = ref(false)
const siteConfig = ref(null)
const userInfo = ref(null)
const captchaId = ref('')
const captchaImage = ref('')

const loginForm = ref({
  account: '',
  password: ''
})

const registerForm = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
  emailCode: ''
})

const unreadCount = ref(0)

// 消息下拉
const messageMenuOpen = ref(false)
const recentMessages = ref([])
const loadingMessages = ref(false)
const markingAllRead = ref(false)

let countdownTimer = null

// 获取未读消息数
const fetchUnreadCount = async () => {
  if (!isLoggedIn.value) {
    unreadCount.value = 0
    return
  }
  
  try {
    const res = await axios.get(`${API_BASE}/messages/unread-count`)
    // 后端 success 统一返回 code=200，兼容历史 code=0 的情况
    if (res.data?.code === 200 || res.data?.code === 0) {
      unreadCount.value = Number(res.data.data?.unreadCount || 0)
      return
    }
    unreadCount.value = 0
  } catch (error) {
    console.error('获取未读消息数失败:', error)
    unreadCount.value = 0
  }
}

// 获取最近消息
const fetchRecentMessages = async () => {
  if (!isLoggedIn.value) {
    recentMessages.value = []
    return
  }
  
  loadingMessages.value = true
  try {
    const res = await axios.get(`${API_BASE}/messages`, {
      params: {
        page: 1,
        pageSize: 5
      }
    })
    if (res.data?.code === 200) {
      recentMessages.value = res.data.data.content || []
    }
  } catch (error) {
    console.error('获取最近消息失败:', error)
  } finally {
    loadingMessages.value = false
  }
}

const handleMarkAllAsRead = async () => {
  if (markingAllRead.value || unreadCount.value <= 0) {
    return
  }

  markingAllRead.value = true
  try {
    const res = await axios.put(`${API_BASE}/messages/mark-all-read`)
    if (res.data?.code === 200 || res.data?.code === 0) {
      await fetchUnreadCount()
      await fetchRecentMessages()
      showAppMessage('已全部标记为已读', 'success')
      return
    }
    showAppMessage(res.data?.msg || '一键已读失败', 'error')
  } catch (error) {
    console.error('全部标记已读失败:', error)
    showAppMessage('一键已读失败，请稍后重试', 'error')
  } finally {
    markingAllRead.value = false
  }
}

// 格式化时间
const formatMessageTime = (dateString) => {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN')
}

// 处理消息点击
const handleMessageClick = async (message) => {
  messageMenuOpen.value = false
  
  // 标记为已读
  if (!message.isRead) {
    try {
      await axios.put(`${API_BASE}/messages/${message.id}/read`)
      await fetchUnreadCount()
      await fetchRecentMessages()
    } catch (error) {
      console.error('标记消息已读失败:', error)
    }
  }
  
  // 如果是剧集更新消息且有视频ID，跳转到播放页
  if (message.type === 'episode_update' && message.videoId) {
    const routeData = router.resolve({
      name: 'Player',
      params: { videoId: String(message.videoId) },
      query: {
        animeId: String(message.animeId),
        episodeId: String(message.episodeId || '')
      }
    })
    window.open(routeData.href, '_blank')
  } else if (message.animeId) {
    // 否则如果有animeId，跳转到动画详情页
    router.push(`/anime/${message.animeId}`)
  }
}

// 判断是否是移动端
const isMobileDevice = () => {
  return window.innerWidth <= 768
}

// 处理消息按钮点击
const handleMessageBtnClick = () => {
  if (isMobileDevice()) {
    // 移动端直接跳转到消息列表
    goToMessages()
  } else {
    // PC端打开下拉菜单
    toggleMessageMenu()
  }
}

// 切换消息菜单
const toggleMessageMenu = () => {
  messageMenuOpen.value = !messageMenuOpen.value
  if (messageMenuOpen.value) {
    fetchRecentMessages()
  }
}

// 定期获取未读消息数
let unreadCountTimer = null
const startUnreadCountPolling = () => {
  if (!isLoggedIn.value) return
  
  if (!unreadCountTimer) {
    fetchUnreadCount()
    unreadCountTimer = setInterval(fetchUnreadCount, 180000) // 每3分钟检查一次
  }
}

const stopUnreadCountPolling = () => {
  if (unreadCountTimer) {
    clearInterval(unreadCountTimer)
    unreadCountTimer = null
  }
}

// 获取当前用户信息
const fetchUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      const userData = res.data.data
      localStorage.setItem('userInfo', JSON.stringify(userData))
      userInfo.value = userData
      startUnreadCountPolling()
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

const isRegisterOpen = computed(() => {
  return !!siteConfig.value?.authRegisterEnabled
})

const isRemoteAccessVisible = computed(() => {
  const enabledRaw = siteConfig.value?.remoteAccessEnabled
  const tokenRequiredRaw = siteConfig.value?.remoteAccessTokenRequired
  const requiredRoleRaw = siteConfig.value?.remoteAccessRequiredRole
  const enabled = enabledRaw === true || enabledRaw === 'true'
  const tokenRequired = tokenRequiredRaw === true || tokenRequiredRaw === 'true'

  if (!enabled) {
    return false
  }

  if (!tokenRequired) {
    return true
  }

  if (!isLoggedIn.value) {
    return false
  }

  const roleLevel = {
    user: 1,
    admin: 2,
    'super-admin': 3
  }

  const requiredRole = (requiredRoleRaw || 'user').toString().trim()
  const requiredLevel = roleLevel[requiredRole]
  const roleCodes = Array.isArray(userInfo.value?.roleCodeList) ? userInfo.value.roleCodeList : []

  if (!requiredLevel) {
    return roleCodes.includes(requiredRole)
  }

  return roleCodes.some((role) => {
    const level = roleLevel[role]
    return typeof level === 'number' && level >= requiredLevel
  })
})

const sendCodeDisabled = computed(() => {
  return sendCodeLoading.value || sendCodeCountdown.value > 0
})

const sendCodeText = computed(() => {
  if (sendCodeLoading.value) {
    return '发送中...'
  }
  if (sendCodeCountdown.value > 0) {
    return `${sendCodeCountdown.value}s`
  }
  return '发送验证码'
})

const captchaImageSrc = computed(() => {
  if (!captchaImage.value) {
    return ''
  }
  if (captchaImage.value.startsWith('data:image/')) {
    return captchaImage.value
  }
  return `data:image/png;base64,${captchaImage.value}`
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

// 点击外部关闭下拉菜单
const handleClickOutside = (event) => {
  if (messageMenuOpen.value) {
    const messageWrapper = event.target.closest('.message-wrapper')
    if (!messageWrapper) {
      messageMenuOpen.value = false
    }
  }
  if (userMenuOpen.value) {
    const userMenuWrapper = event.target.closest('.user-menu-wrapper')
    if (!userMenuWrapper) {
      userMenuOpen.value = false
    }
  }
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
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  stopUnreadCountPolling()
  document.removeEventListener('click', handleClickOutside)
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
  if (!loginForm.value.account || !loginForm.value.password) {
    showAppMessage('请输入用户名/邮箱和密码', 'warning')
    return
  }

  loginLoading.value = true
  try {
    const res = await axios.post(`${API_BASE}/auth/login`, {
      account: loginForm.value.account,
      password: loginForm.value.password
    })

    if (res.data?.code === 200 && res.data?.data) {
      const { tokenValue } = res.data.data
      localStorage.setItem('token', tokenValue)
      window.location.reload()
      return
    } else {
      showAppMessage(res.data?.msg || '登录失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '登录失败，请重试', 'error')
  } finally {
    loginLoading.value = false
  }
}

const refreshCaptcha = async () => {
  try {
    const res = await axios.get(`${API_BASE}/auth/captcha`)
    if (res.data?.code === 200 && res.data?.data) {
      captchaId.value = res.data.data.captchaId
      captchaImage.value = res.data.data.imageBase64
      registerForm.value.captchaCode = ''
    }
  } catch (error) {
    console.error('获取图形验证码失败:', error)
  }
}

const openLoginDialog = () => {
  showRegisterDialog.value = false
  showLoginDialog.value = true
  userMenuOpen.value = false
}

const openRegisterDialog = async () => {
  if (!isRegisterOpen.value) {
    return
  }
  showLoginDialog.value = false
  showRegisterDialog.value = true
  userMenuOpen.value = false
  await refreshCaptcha()
}

const startSendCodeCountdown = (seconds) => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  sendCodeCountdown.value = seconds
  countdownTimer = setInterval(() => {
    if (sendCodeCountdown.value <= 1) {
      clearInterval(countdownTimer)
      countdownTimer = null
      sendCodeCountdown.value = 0
      return
    }
    sendCodeCountdown.value -= 1
  }, 1000)
}

const sendEmailCode = async () => {
  if (!registerForm.value.email) {
    showAppMessage('请输入邮箱', 'warning')
    return
  }
  if (!registerForm.value.captchaCode) {
    showAppMessage('请输入图形验证码', 'warning')
    return
  }
  if (!captchaId.value) {
    await refreshCaptcha()
    showAppMessage('图形验证码已刷新，请重新输入', 'warning')
    return
  }

  sendCodeLoading.value = true
  try {
    const res = await axios.post(`${API_BASE}/auth/send-register-email-code`, {
      email: registerForm.value.email,
      captchaId: captchaId.value,
      captchaCode: registerForm.value.captchaCode
    })

    if (res.data?.code === 200) {
      showAppMessage('验证码已发送，请检查邮箱', 'success')
      startSendCodeCountdown(60)
    } else {
      showAppMessage(res.data?.msg || '发送失败', 'error')
      await refreshCaptcha()
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '发送失败，请稍后重试', 'error')
    await refreshCaptcha()
  } finally {
    sendCodeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerForm.value.username || !registerForm.value.email || !registerForm.value.password) {
    showAppMessage('请填写完整注册信息', 'warning')
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    showAppMessage('两次密码输入不一致', 'warning')
    return
  }
  if (!registerForm.value.emailCode) {
    showAppMessage('请输入邮箱验证码', 'warning')
    return
  }

  registerLoading.value = true
  try {
    const res = await axios.post(`${API_BASE}/auth/register`, {
      username: registerForm.value.username,
      email: registerForm.value.email,
      password: registerForm.value.password,
      emailCode: registerForm.value.emailCode
    })

    if (res.data?.code === 200) {
      showAppMessage('注册成功，请登录', 'success')
      registerForm.value = {
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
        captchaCode: '',
        emailCode: ''
      }
      showRegisterDialog.value = false
      userMenuOpen.value = false
      showLoginDialog.value = true
    } else {
      showAppMessage(res.data?.msg || '注册失败', 'error')
    }
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '注册失败，请稍后重试', 'error')
  } finally {
    registerLoading.value = false
  }
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('rememberMe')
  window.location.reload()
}

const goToProfile = () => {
  userMenuOpen.value = false
  router.push('/profile')
}

const goToFollows = () => {
  userMenuOpen.value = false
  router.push({ path: '/profile', query: { tab: 'follows' } })
}

const goToMessages = () => {
  userMenuOpen.value = false
  router.push({ path: '/profile', query: { tab: 'messages' } })
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
  justify-content: flex-start;
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

/* Message Button */
.message-wrapper {
  position: relative;
}

.message-btn {
  position: relative;
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

.message-btn:hover {
  background: #faf8f5;
  color: var(--primary-dark);
  border-color: var(--accent-brown);
}

.unread-dot {
  position: absolute;
  top: -1px;
  right: -1px;
  width: 10px;
  height: 10px;
  background: #e74c3c;
  border-radius: 50%;
  border: 2px solid white;
  box-shadow: 0 0 0 1px rgba(231, 76, 60, 0.25);
}

/* Message Dropdown */
.message-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  background: white;
  border-radius: 12px;
  border: 1px solid var(--border-light);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  width: 360px;
  max-height: 500px;
  overflow: hidden;
  animation: slideDown 0.2s ease;
  z-index: 1001;
}

.message-dropdown-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-cream);
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 0.95rem;
  color: white;
  color: var(--primary-dark);
}

.unread-count-badge {
  background: #e74c3c;
  color: white;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
}

.message-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mark-all-read-btn {
  border: 1px solid var(--border-light);
  background: #fff;
  color: var(--accent-red);
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
  padding: 5px 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mark-all-read-btn:hover:not(:disabled) {
  background: var(--bg-beige);
  border-color: var(--accent-brown);
}

.mark-all-read-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.message-list {
  max-height: 380px;
  overflow-y: auto;
}

.message-loading {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-loading i {
  font-size: 1.5rem;
}

.message-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s ease;
  display: flex;
  gap: 10px;
}

.message-item:hover {
  background: var(--bg-cream);
}

.message-item.unread {
  background: rgba(196, 93, 43, 0.05);
}

.message-item.unread:hover {
  background: rgba(196, 93, 43, 0.1);
}

.message-item:last-child {
  border-bottom: none;
}

.message-item-indicator {
  flex-shrink: 0;
  padding-top: 4px;
}

.unread-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  background: #e74c3c;
  border-radius: 50%;
}

.message-item-content {
  flex: 1;
  min-width: 0;
}

.message-item-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item-text {
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-clamp: 2;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
}

.message-item-time {
  font-size: 0.75rem;
  color: var(--text-secondary);
  opacity: 0.7;
}

.message-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.message-empty i {
  font-size: 2.5rem;
  opacity: 0.5;
}

.message-dropdown-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  background: #fff;
}

.view-all-messages-btn {
  width: 100%;
  padding: 8px;
  background: transparent;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  color: var(--accent-red);
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.view-all-messages-btn:hover {
  background: var(--bg-cream);
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
  padding: 12px 16px;
  width: 100%;
  max-width: 100%;
}

.site-description-note {
  margin: 20px 0 2px;
  font-size: 0.76rem;
  line-height: 1.45;
  color: rgba(107, 95, 85, 0.75);
  text-align: center;
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

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-row .login-input {
  flex: 1;
}

.captcha-image {
  width: 110px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #e0d5ca;
  cursor: pointer;
  background: #fff;
}

.send-code-btn {
  min-width: 110px;
  white-space: nowrap;
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
    justify-content: flex-start;
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
    width: 180px;
  }

  .message-dropdown {
    width: 340px;
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
    gap: 10px;
  }

  .nav-menu {
    justify-content: center;
    gap: 8px;
  }

  .search-box {
    width: 140px;
    font-size: 0.85rem;
    padding: 6px 10px;
    order: 1;
  }

  .search-box input::placeholder {
    font-size: 0.8rem;
  }

  .message-wrapper {
    order: 2;
  }

  .user-menu-wrapper {
    order: 3;
  }

  .content-wrapper {
    padding: 16px 12px;
  }

  .message-btn,
  .user-btn {
    width: 36px;
    height: 36px;
    font-size: 1rem;
  }

  .message-dropdown {
    width: 320px;
    right: -10px;
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
    width: 120px;
    padding: 6px 8px;
    gap: 4px;
    order: 1;
  }

  .search-box i {
    font-size: 0.85rem;
  }

  .search-box input {
    font-size: 0.8rem;
    min-width: 0;
  }

  .search-box input::placeholder {
    font-size: 0.75rem;
  }

  .message-wrapper {
    order: 2;
  }

  .user-menu-wrapper {
    order: 3;
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

  .site-description-note {
    margin-top: 16px;
    font-size: 0.72rem;
  }

  .message-btn,
  .user-btn {
    width: 32px;
    height: 32px;
    font-size: 0.9rem;
  }

  .message-dropdown {
    width: calc(100vw - 20px);
    max-width: 320px;
    right: -10px;
  }
}

/* 极小屏幕优化 */
@media (max-width: 480px) {
  .nav-container {
    padding: 6px 8px;
    gap: 6px;
  }

  .site-name {
    max-width: 100px;
    font-size: 0.8rem;
  }

  .search-box {
    width: 100px;
    padding: 5px 7px;
    order: 1;
  }

  .search-box input::placeholder {
    content: '搜索';
  }

  .message-wrapper {
    order: 2;
  }

  .user-menu-wrapper {
    order: 3;
  }

  .nav-right {
    gap: 6px;
  }

  .message-btn,
  .user-btn {
    width: 30px;
    height: 30px;
    font-size: 0.85rem;
  }

  .unread-dot {
    width: 8px;
    height: 8px;
  }

  .message-dropdown {
    width: calc(100vw - 16px);
    right: -8px;
  }
}
</style>
