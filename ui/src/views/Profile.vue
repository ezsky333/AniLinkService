<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const historyList = ref([])
const loading = ref(false)
const error = ref('')
const currentPage = ref(1)
const pageSize = ref(12)
const totalElements = ref(0)

const fetchPlayHistory = async () => {
  loading.value = true
  error.value = ''

  try {
    const response = await axios.get('/api/play-history', {
      params: {
        page: currentPage.value,
        pageSize: pageSize.value,
      },
    })

    if (response.data?.code === 200) {
      historyList.value = response.data.data?.content || []
      totalElements.value = Number(response.data.data?.totalElements || 0)
    } else {
      error.value = response.data?.msg || '加载播放历史失败'
    }
  } catch (err) {
    console.error('加载播放历史失败:', err)
    error.value = '加载播放历史失败'
  } finally {
    loading.value = false
  }
}

const goToPlayer = (item) => {
  if (!item?.videoId) {
    return
  }
  router.push({
    name: 'Player',
    params: { videoId: String(item.videoId) },
    query: {
      animeId: String(item.animeId || ''),
    },
  })
}

const deleteHistoryItem = async (id) => {
  if (!id) {
    return
  }
  if (!confirm('确定删除这条播放历史吗？')) {
    return
  }

  try {
    const response = await axios.delete(`/api/play-history/${id}`)
    if (response.data?.code === 200) {
      if (historyList.value.length === 1 && currentPage.value > 1) {
        currentPage.value -= 1
      }
      await fetchPlayHistory()
    } else {
      alert(response.data?.msg || '删除失败')
    }
  } catch (err) {
    console.error('删除播放历史失败:', err)
    alert('删除播放历史失败')
  }
}

const clearHistory = async () => {
  if (!confirm('确定清空所有播放历史吗？该操作不可恢复。')) {
    return
  }

  try {
    const response = await axios.delete('/api/play-history/clear')
    if (response.data?.code === 200) {
      currentPage.value = 1
      await fetchPlayHistory()
    } else {
      alert(response.data?.msg || '清空失败')
    }
  } catch (err) {
    console.error('清空播放历史失败:', err)
    alert('清空播放历史失败')
  }
}

const handlePageChange = async (page) => {
  currentPage.value = page
  await fetchPlayHistory()
}

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize.value)))

const formatDateTime = (value) => {
  if (!value) {
    return '--'
  }
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const getProgressText = (item) => {
  const progress = Number(item?.progressSeconds || 0)
  const duration = Number(item?.durationSeconds || 0)
  const percent = Number(item?.progressPercentage || 0)
  if (!duration) {
    return `${progress}s`
  }
  return `${progress}s / ${duration}s (${Math.min(100, Math.max(0, percent))}%)`
}

onMounted(() => {
  fetchPlayHistory()
})
</script>

<template>
  <div class="history-page">
    <v-container>
      <v-card class="elevation-2">
        <v-card-title class="text-h5 bg-grey-lighten-4 d-flex align-center justify-space-between">
          <div class="d-flex align-center">
            <i class="fa-solid fa-clock-rotate-left mr-3" style="color: #c45d2b;"></i>
            观看历史
          </div>
          <v-btn
            variant="tonal"
            color="error"
            :disabled="loading || totalElements === 0"
            @click="clearHistory"
          >
            清空历史
          </v-btn>
        </v-card-title>

        <v-card-text class="pa-6">
          <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
            {{ error }}
          </v-alert>

          <v-skeleton-loader
            v-if="loading"
            type="list-item-avatar-three-line@4"
          />

          <div v-else-if="historyList.length > 0" class="history-list">
            <v-card
              v-for="item in historyList"
              :key="item.id"
              class="history-card"
              elevation="1"
            >
              <div class="history-main">
                <div class="history-content">
                  <h3 class="history-title">{{ item.animeTitle || `番剧 #${item.animeId}` }}</h3>
                  <p class="history-subtitle">最近播放: {{ item.videoName || `视频 #${item.videoId || '-'}` }}</p>
                  <p class="history-meta">播放进度: {{ getProgressText(item) }}</p>
                  <p class="history-meta">最近播放时间: {{ formatDateTime(item.lastPlayTime) }}</p>
                </div>

                <div class="history-actions">
                  <v-btn
                    color="primary"
                    :disabled="!item.videoId"
                    @click="goToPlayer(item)"
                  >
                    继续播放
                  </v-btn>
                  <v-btn
                    variant="outlined"
                    color="error"
                    @click="deleteHistoryItem(item.id)"
                  >
                    删除
                  </v-btn>
                </div>
              </div>
            </v-card>
          </div>

          <v-alert v-else type="info" variant="tonal" class="text-center">
            暂无播放历史
          </v-alert>

          <div v-if="totalPages > 1" class="d-flex justify-center mt-6">
            <v-pagination
              v-model="currentPage"
              :length="totalPages"
              @update:model-value="handlePageChange"
            />
          </div>
        </v-card-text>
      </v-card>
    </v-container>
  </div>
</template>

<style scoped>
.history-page {
  padding: 24px 0;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-card {
  border-radius: 12px;
}

.history-main {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
}

.history-content {
  min-width: 0;
}

.history-title {
  margin: 0 0 6px;
  font-size: 1.05rem;
  color: #2f2b28;
}

.history-subtitle,
.history-meta {
  margin: 0;
  color: #666;
  font-size: 0.92rem;
  line-height: 1.5;
}

.history-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 760px) {
  .history-main {
    flex-direction: column;
    align-items: stretch;
  }

  .history-actions {
    width: 100%;
  }

  .history-actions :deep(.v-btn) {
    flex: 1;
  }
}
</style>
