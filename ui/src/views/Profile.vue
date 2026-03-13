<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { showAppMessage } from '../utils/ui-feedback'

const router = useRouter()
const route = useRoute()
const API_BASE = '/api'

const activeTab = ref(['history', 'follows', 'binding'].includes(route.query.tab) ? route.query.tab : 'history')

const historyList = ref([])
const historyLoading = ref(false)
const historyError = ref('')
const historyPage = ref(1)
const historyPageSize = ref(12)
const historyTotal = ref(0)

const followList = ref([])
const followLoading = ref(false)
const followError = ref('')
const followPage = ref(1)
const followPageSize = ref(20)
const followTotal = ref(0)
const followStatus = ref('watching')
const followKeyword = ref('')

const bangumiLoading = ref(false)
const bangumiBinding = ref(false)
const bangumiUnbinding = ref(false)
const bangumiTokenInput = ref('')
const bangumiStatus = ref({
  bound: false,
  tokenValid: false,
  tokenExpired: false,
  bangumiUserId: null,
  bangumiUsername: '',
  bangumiNickname: '',
  profile: null,
  statusMessage: '未绑定 Bangumi 账号'
})

const tabs = [
  { value: 'history', label: '观看历史', icon: 'mdi mdi-history' },
  { value: 'follows', label: '我的追番', icon: 'mdi mdi-bookmark-multiple' },
  { value: 'binding', label: '账号绑定', icon: 'mdi mdi-link-variant' }
]

const followStatuses = [
  { label: '追番中', value: 'watching', color: 'primary' },
  { label: '已完成', value: 'success', color: 'success' },
  { label: '已放弃', value: 'dropped', color: 'error' }
]

const syncTabQuery = (tab) => {
  const query = { ...route.query }
  if (tab === 'history') {
    delete query.tab
  } else {
    query.tab = tab
  }
  router.replace({ query })
}

watch(() => route.query.tab, (tab) => {
  activeTab.value = ['history', 'follows', 'binding'].includes(tab) ? tab : 'history'
})

watch(activeTab, async (tab) => {
  syncTabQuery(tab)
  if (tab === 'history') {
    await fetchPlayHistory()
  }
  if (tab === 'follows') {
    await fetchFollowList()
  }
  if (tab === 'binding') {
    await fetchBangumiStatus()
  }
})

const fetchCurrentUserInfo = async () => {
  try {
    const res = await axios.post(`${API_BASE}/auth/currentUser`)
    if (res.data?.code === 200 && res.data?.data) {
      localStorage.setItem('userInfo', JSON.stringify(res.data.data))
    }
  } catch (err) {
    console.error('刷新当前用户信息失败:', err)
  }
}

const fetchPlayHistory = async () => {
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await axios.get('/api/play-history', {
      params: {
        page: historyPage.value,
        pageSize: historyPageSize.value,
      },
    })
    if (response.data?.code === 200) {
      historyList.value = response.data.data?.content || []
      historyTotal.value = Number(response.data.data?.totalElements || 0)
    } else {
      historyError.value = response.data?.msg || '加载播放历史失败'
    }
  } catch (err) {
    console.error('加载播放历史失败:', err)
    historyError.value = '加载播放历史失败'
  } finally {
    historyLoading.value = false
  }
}

const goToPlayer = (item) => {
  if (!item?.videoId) return
  router.push({
    name: 'Player',
    params: { videoId: String(item.videoId) },
    query: {
      animeId: String(item.animeId || ''),
    },
  })
}

const deleteHistoryItem = async (id) => {
  if (!id || !confirm('确定删除这条播放历史吗？')) return
  try {
    const response = await axios.delete(`/api/play-history/${id}`)
    if (response.data?.code === 200) {
      if (historyList.value.length === 1 && historyPage.value > 1) {
        historyPage.value -= 1
      }
      await fetchPlayHistory()
    } else {
      showAppMessage(response.data?.msg || '删除失败', 'error')
    }
  } catch (err) {
    console.error('删除播放历史失败:', err)
    showAppMessage('删除播放历史失败', 'error')
  }
}

const clearHistory = async () => {
  if (!confirm('确定清空所有播放历史吗？该操作不可恢复。')) return
  try {
    const response = await axios.delete('/api/play-history/clear')
    if (response.data?.code === 200) {
      historyPage.value = 1
      await fetchPlayHistory()
    } else {
      showAppMessage(response.data?.msg || '清空失败', 'error')
    }
  } catch (err) {
    console.error('清空播放历史失败:', err)
    showAppMessage('清空播放历史失败', 'error')
  }
}

const fetchFollowList = async () => {
  followLoading.value = true
  followError.value = ''
  try {
    const params = {
      page: followPage.value,
      pageSize: followPageSize.value
    }

    const url = followStatus.value ? `/api/follows/status/${followStatus.value}` : '/api/follows'
    const response = await axios.get(url, { params })

    if (response.data?.code === 200) {
      if (Array.isArray(response.data.data)) {
        const keyword = followKeyword.value.trim().toLowerCase()
        followList.value = response.data.data.filter((item) => {
          return !keyword || String(item.animeTitle || '').toLowerCase().includes(keyword)
        })
        followTotal.value = followList.value.length
      } else {
        const rawList = response.data.data?.content || []
        const keyword = followKeyword.value.trim().toLowerCase()
        followList.value = rawList.filter((item) => {
          return !keyword || String(item.animeTitle || '').toLowerCase().includes(keyword)
        })
        followTotal.value = response.data.data?.totalElements || 0
      }
    } else {
      followError.value = response.data?.msg || '加载追番列表失败'
    }
  } catch (err) {
    console.error('加载追番列表失败:', err)
    followError.value = '加载追番列表失败'
  } finally {
    followLoading.value = false
  }
}

const goToAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}

const statusLabel = (status) => {
  const map = { watching: '追番中', completed: '已完成', dropped: '已放弃' }
  return map[status] || status
}

const statusChipColor = (status) => {
  const map = { watching: 'primary', completed: 'success', dropped: 'error' }
  return map[status] || 'grey'
}

const changeFollowStatus = async (animeId, status) => {
  try {
    const response = await axios.put(`/api/follows/${animeId}/status`, null, { params: { status } })
    if (response.data?.code === 200) {
      await fetchFollowList()
      return
    }
    showAppMessage(response.data?.msg || '更新追番状态失败', 'error')
  } catch (err) {
    console.error('更新追番状态失败:', err)
    showAppMessage('更新追番状态失败', 'error')
  }
}

const unfollow = async (animeId, animeTitle) => {
  if (!confirm(`确定要取消追番《${animeTitle}》吗？`)) return
  try {
    const response = await axios.delete(`/api/follows/${animeId}`)
    if (response.data?.code === 200) {
      await fetchFollowList()
      return
    }
    showAppMessage(response.data?.msg || '取消追番失败', 'error')
  } catch (err) {
    console.error('取消追番失败:', err)
    showAppMessage('取消追番失败', 'error')
  }
}

const fetchBangumiStatus = async () => {
  bangumiLoading.value = true
  try {
    const res = await axios.get(`${API_BASE}/bangumi/account/status`)
    if (res.data?.code === 200 && res.data?.data) {
      bangumiStatus.value = res.data.data
      await fetchCurrentUserInfo()
      return
    }
    showAppMessage(res.data?.msg || '获取 Bangumi 绑定状态失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || '获取 Bangumi 绑定状态失败', 'error')
  } finally {
    bangumiLoading.value = false
  }
}

const bindBangumiToken = async () => {
  const token = bangumiTokenInput.value.trim()
  if (!token) {
    showAppMessage('请输入 Bangumi Access Token', 'warning')
    return
  }
  bangumiBinding.value = true
  try {
    const res = await axios.post(`${API_BASE}/bangumi/account/bind`, { accessToken: token })
    if (res.data?.code === 200 && res.data?.data) {
      bangumiStatus.value = res.data.data
      bangumiTokenInput.value = ''
      await fetchCurrentUserInfo()
      showAppMessage('Bangumi 账号绑定成功', 'success')
      return
    }
    showAppMessage(res.data?.msg || 'Bangumi 绑定失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || 'Bangumi 绑定失败', 'error')
  } finally {
    bangumiBinding.value = false
  }
}

const unbindBangumiToken = async () => {
  bangumiUnbinding.value = true
  try {
    const res = await axios.delete(`${API_BASE}/bangumi/account/bind`)
    if (res.data?.code === 200) {
      bangumiStatus.value = {
        bound: false,
        tokenValid: false,
        tokenExpired: false,
        bangumiUserId: null,
        bangumiUsername: '',
        bangumiNickname: '',
        profile: null,
        statusMessage: '未绑定 Bangumi 账号'
      }
      await fetchCurrentUserInfo()
      showAppMessage('已解除 Bangumi 绑定', 'success')
      return
    }
    showAppMessage(res.data?.msg || '解除 Bangumi 绑定失败', 'error')
  } catch (err) {
    showAppMessage(err.response?.data?.msg || '解除 Bangumi 绑定失败', 'error')
  } finally {
    bangumiUnbinding.value = false
  }
}

const bangumiStatusTone = computed(() => {
  if (!bangumiStatus.value.bound) return 'info'
  if (bangumiStatus.value.tokenValid) return 'success'
  if (bangumiStatus.value.tokenExpired) return 'warning'
  return 'info'
})

const bangumiAvatar = computed(() => {
  return bangumiStatus.value.profile?.avatar?.medium
    || bangumiStatus.value.profile?.avatar?.large
    || bangumiStatus.value.profile?.avatar?.small
    || ''
})

const historyTotalPages = computed(() => Math.max(1, Math.ceil(historyTotal.value / historyPageSize.value)))
const followTotalPages = computed(() => Math.max(1, Math.ceil(followTotal.value / followPageSize.value)))

const formatDateTime = (value) => {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const formatDate = (value) => {
  if (!value) return '--'
  return new Date(value).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

const getProgressText = (item) => {
  const progress = Number(item?.progressSeconds || 0)
  const duration = Number(item?.durationSeconds || 0)
  const percent = Number(item?.progressPercentage || 0)
  if (!duration) return `${progress}s`
  return `${progress}s / ${duration}s (${Math.min(100, Math.max(0, percent))}%)`
}

onMounted(async () => {
  if (activeTab.value === 'history') {
    await fetchPlayHistory()
  } else if (activeTab.value === 'follows') {
    await fetchFollowList()
  } else {
    await fetchBangumiStatus()
  }
})
</script>

<template>
  <div class="profile-page">
    <v-container>
      <div class="profile-shell">
        <aside class="profile-sidebar">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="profile-tab-btn"
            :class="{ active: activeTab === tab.value }"
            @click="activeTab = tab.value"
          >
            <i :class="tab.icon"></i>
            <span>{{ tab.label }}</span>
          </button>
        </aside>

        <section class="profile-main">
          <v-card v-if="activeTab === 'history'" class="elevation-2">
            <v-card-title class="text-h5 bg-grey-lighten-4 d-flex align-center justify-space-between">
              <div class="d-flex align-center">
                <i class="mdi mdi-history mr-3" style="color: #c45d2b;"></i>
                观看历史
              </div>
              <v-btn variant="tonal" color="error" :disabled="historyLoading || historyTotal === 0" @click="clearHistory">
                清空历史
              </v-btn>
            </v-card-title>
            <v-card-text class="pa-6">
              <v-alert v-if="historyError" type="error" variant="tonal" class="mb-4">{{ historyError }}</v-alert>
              <v-skeleton-loader v-if="historyLoading" type="list-item-avatar-three-line@4" />
              <div v-else-if="historyList.length > 0" class="history-list">
                <v-card v-for="item in historyList" :key="item.id" class="history-card" elevation="1">
                  <div class="history-main-card">
                    <div class="history-content">
                      <h3 class="history-title">{{ item.animeTitle || `番剧 #${item.animeId}` }}</h3>
                      <p class="history-subtitle">最近播放: {{ item.videoName || `视频 #${item.videoId || '-'}` }}</p>
                      <p class="history-meta">播放进度: {{ getProgressText(item) }}</p>
                      <p class="history-meta">最近播放时间: {{ formatDateTime(item.lastPlayTime) }}</p>
                    </div>
                    <div class="history-actions">
                      <v-btn color="primary" :disabled="!item.videoId" @click="goToPlayer(item)">继续播放</v-btn>
                      <v-btn variant="outlined" color="error" @click="deleteHistoryItem(item.id)">删除</v-btn>
                    </div>
                  </div>
                </v-card>
              </div>
              <v-alert v-else type="info" variant="tonal" class="text-center">暂无播放历史</v-alert>
              <div v-if="historyTotalPages > 1" class="d-flex justify-center mt-6">
                <v-pagination v-model="historyPage" :length="historyTotalPages" @update:model-value="fetchPlayHistory" />
              </div>
            </v-card-text>
          </v-card>

          <v-card v-else-if="activeTab === 'follows'" class="elevation-2">
            <v-card-title class="text-h5 bg-grey-lighten-4 d-flex align-center">
              <i class="mdi mdi-bookmark-multiple mr-3" style="color: #e74c3c;"></i>
              我的追番
            </v-card-title>
            <v-card-text class="pa-6">
              <v-alert v-if="followError" type="error" variant="tonal" class="mb-4">{{ followError }}</v-alert>

              <div class="mb-4 d-flex flex-wrap gap-2">
                <v-btn
                  v-for="status in followStatuses"
                  :key="status.value"
                  :variant="followStatus === status.value ? 'flat' : 'tonal'"
                  :color="followStatus === status.value ? status.color : 'grey'"
                  size="small"
                  rounded="pill"
                  @click="followStatus = status.value; followPage = 1; fetchFollowList()"
                >
                  {{ status.label }}
                </v-btn>
              </div>

              <div class="d-flex gap-2 mb-6">
                <v-text-field
                  v-model="followKeyword"
                  placeholder="搜索番剧标题..."
                  density="compact"
                  variant="outlined"
                  hide-details
                  @keyup.enter="followPage = 1; fetchFollowList()"
                />
                <v-btn color="primary" @click="followPage = 1; fetchFollowList()">搜索</v-btn>
              </div>

              <v-skeleton-loader v-if="followLoading" type="list-item-avatar-three-line@3" />

              <div v-else-if="followList.length > 0" class="follow-list">
                <v-card v-for="follow in followList" :key="follow.id" class="mb-3" elevation="1">
                  <div class="follow-row">
                    <div>
                      <h3 class="follow-title" @click="goToAnime(follow.animeId)">{{ follow.animeTitle }}</h3>
                      <div class="mb-2">
                        <v-chip :color="statusChipColor(follow.status)" size="small" variant="tonal">
                          {{ statusLabel(follow.status) }}
                        </v-chip>
                      </div>
                      <div class="follow-meta">追番时间: {{ formatDate(follow.followAt) }}</div>
                      <div class="follow-meta">更新时间: {{ formatDate(follow.updatedAt) }}</div>
                    </div>
                    <div class="follow-actions">
                      <v-btn color="primary" size="small" @click="goToAnime(follow.animeId)">查看详情</v-btn>
                      <v-btn size="small" variant="outlined" color="success" @click="changeFollowStatus(follow.animeId, 'completed')">标记完结</v-btn>
                      <v-btn size="small" variant="outlined" color="error" @click="unfollow(follow.animeId, follow.animeTitle)">取消追番</v-btn>
                    </div>
                  </div>
                </v-card>
              </div>

              <v-alert v-else type="info" variant="tonal" class="text-center">暂无追番记录</v-alert>

              <div v-if="followTotalPages > 1" class="d-flex justify-center mt-6">
                <v-pagination v-model="followPage" :length="followTotalPages" @update:model-value="fetchFollowList" />
              </div>
            </v-card-text>
          </v-card>

          <v-card v-else class="elevation-2" :loading="bangumiLoading || bangumiBinding || bangumiUnbinding">
            <v-card-title class="text-h6 d-flex align-center ga-2">
              <v-icon color="deep-orange-darken-1">mdi-account-key</v-icon>
              Bangumi 账号绑定
            </v-card-title>
            <v-card-text>
              <v-alert type="info" variant="tonal" class="mb-4">
                请前往
                <a href="https://next.bgm.tv/demo/access-token" target="_blank" rel="noopener noreferrer">https://next.bgm.tv/demo/access-token</a>
                登录后获取 Access Token。请注意：该 Token 会保存到服务端，请仅在你信任的环境中进行绑定。
              </v-alert>

              <v-alert :type="bangumiStatusTone" variant="tonal" class="mb-4">
                {{ bangumiStatus.statusMessage || '未绑定 Bangumi 账号' }}
              </v-alert>

              <div v-if="bangumiStatus.bound && bangumiStatus.profile" class="bangumi-user-card mb-4">
                <img v-if="bangumiAvatar" :src="bangumiAvatar" alt="Bangumi Avatar" class="bangumi-avatar" />
                <div>
                  <div class="bangumi-user-name">{{ bangumiStatus.bangumiNickname || bangumiStatus.bangumiUsername }}</div>
                  <div class="bangumi-user-meta">@{{ bangumiStatus.bangumiUsername }} · ID {{ bangumiStatus.bangumiUserId }}</div>
                  <div class="bangumi-user-meta">{{ bangumiStatus.profile?.sign || '这个用户还没有签名。' }}</div>
                </div>
              </div>

              <v-text-field
                v-model="bangumiTokenInput"
                label="Bangumi Access Token"
                variant="outlined"
                hide-details="auto"
                placeholder="粘贴从 next.bgm.tv 获取的 Access Token"
              />

              <div class="integration-actions mt-4">
                <v-btn color="deep-orange-darken-1" :loading="bangumiBinding" :disabled="bangumiBinding" @click="bindBangumiToken">
                  绑定或更新 Token
                </v-btn>
                <v-btn
                  variant="outlined"
                  color="grey-darken-1"
                  :disabled="!bangumiStatus.bound || bangumiUnbinding"
                  :loading="bangumiUnbinding"
                  @click="unbindBangumiToken"
                >
                  解除绑定
                </v-btn>
                <v-btn variant="text" color="primary" :disabled="bangumiLoading" @click="fetchBangumiStatus">
                  刷新状态
                </v-btn>
              </div>
            </v-card-text>
          </v-card>
        </section>
      </div>
    </v-container>
  </div>
</template>

<style scoped>
.profile-page { padding: 24px 0; }
.profile-shell { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 20px; align-items: start; }
.profile-sidebar { display: grid; gap: 10px; position: sticky; top: 88px; }
.profile-tab-btn { display: flex; align-items: center; gap: 10px; width: 100%; border: 1px solid #e5d8cc; background: #fff; border-radius: 14px; padding: 14px 16px; cursor: pointer; color: #5f5148; font-weight: 600; transition: 0.2s ease; }
.profile-tab-btn:hover, .profile-tab-btn.active { border-color: #c45d2b; color: #c45d2b; background: #fff8f3; }
.history-list { display: grid; gap: 12px; }
.history-card { border-radius: 12px; }
.history-main-card { display: flex; gap: 16px; justify-content: space-between; align-items: center; padding: 16px; }
.history-title { margin: 0 0 6px; font-size: 1.05rem; color: #2f2b28; }
.history-subtitle, .history-meta { margin: 0; color: #666; font-size: 0.92rem; line-height: 1.5; }
.history-actions { display: flex; gap: 8px; flex-shrink: 0; }
.follow-list { display: grid; gap: 10px; }
.follow-row { padding: 14px; display: flex; justify-content: space-between; gap: 14px; }
.follow-title { margin: 0 0 8px; color: #2f2b28; cursor: pointer; }
.follow-meta { color: #6b5f55; font-size: 0.86rem; }
.follow-actions { display: flex; gap: 8px; align-items: flex-start; }
.integration-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.bangumi-user-card { display: flex; gap: 14px; align-items: center; padding: 14px; background: #fff8f3; border: 1px solid #f2d7c6; border-radius: 14px; }
.bangumi-avatar { width: 56px; height: 56px; border-radius: 50%; object-fit: cover; background: #f1e5db; }
.bangumi-user-name { font-size: 1rem; font-weight: 700; color: #2f2b28; }
.bangumi-user-meta { color: #6b5f55; font-size: 0.88rem; line-height: 1.5; }
@media (max-width: 960px) {
  .profile-shell { grid-template-columns: 1fr; }
  .profile-sidebar { position: static; grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .history-main-card, .follow-row { flex-direction: column; align-items: stretch; }
  .history-actions, .follow-actions, .profile-sidebar, .integration-actions { width: 100%; }
  .history-actions :deep(.v-btn), .follow-actions :deep(.v-btn), .integration-actions :deep(.v-btn) { flex: 1; }
}
</style>
