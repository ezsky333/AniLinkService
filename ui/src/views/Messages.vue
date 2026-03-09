<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const messages = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const filterType = ref('')
const selectedMessage = ref(null)
const error = ref('')

const messageTypes = [
  { label: '全部', value: '', color: 'grey' },
  { label: '剧集更新', value: 'episode_update', color: 'primary' },
  { label: '系统通知', value: 'system', color: 'grey-darken-1' }
]

const typeLabel = (type) => {
  return messageTypes.find(t => t.value === type)?.label || type
}

const typeChipColor = (type) => {
  return messageTypes.find(t => t.value === type)?.color || 'grey'
}

const fetchMessages = async () => {
  loading.value = true
  error.value = ''
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value
    }
    
    let url = '/api/messages'
    if (filterType.value) {
      url = `/api/messages/type/${filterType.value}`
    }
    
    const response = await axios.get(url, { params })
    if (response.data.code === 200) {
      messages.value = response.data.data.content || []
      totalElements.value = response.data.data.totalElements || 0
    } else {
      error.value = response.data.msg || '加载失败'
    }
  } catch (err) {
    console.error('Failed to fetch messages:', err)
    error.value = '加载消息列表失败'
  } finally {
    loading.value = false
  }
}

const markAsRead = async (messageId) => {
  try {
    const response = await axios.put(`/api/messages/${messageId}/read`)
    if (response.data.code === 200) {
      await fetchMessages()
      selectedMessage.value = null
    }
  } catch (err) {
    console.error('Failed to mark message as read:', err)
  }
}

const markAllAsRead = async () => {
  try {
    await axios.put('/api/messages/mark-all-read')
    await fetchMessages()
  } catch (err) {
    console.error('Failed to mark all messages as read:', err)
  }
}

const deleteMessage = async (messageId) => {
  if (!confirm('确定要删除这条消息吗？')) {
    return
  }
  
  try {
    const response = await axios.delete(`/api/messages/${messageId}`)
    if (response.data.code === 200) {
      await fetchMessages()
      selectedMessage.value = null
    }
  } catch (err) {
    console.error('Failed to delete message:', err)
    alert('删除失败')
  }
}

const goToAnime = (animeId) => {
  if (animeId) {
    router.push(`/anime/${animeId}`)
  }
}

const goToHome = () => {
  router.push('/')
}

const handleFilterChange = () => {
  currentPage.value = 1
  fetchMessages()
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchMessages()
}

const selectMessage = (message) => {
  selectedMessage.value = message
  if (!message.isRead) {
    markAsRead(message.id)
  }
}

const formatTime = (dateString) => {
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

const formatDateTime = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const totalPages = computed(() => {
  return Math.ceil(totalElements.value / pageSize.value)
})

const unreadCount = computed(() => {
  return messages.value.filter(m => !m.isRead).length
})

onMounted(() => {
  fetchMessages()
})
</script>

<template>
  <div class="messages-page">
    <v-container>
      <v-card class="elevation-2">
        <v-card-title class="text-h5 bg-grey-lighten-4 d-flex align-center justify-space-between">
          <div class="d-flex align-center">
            <i class="fa-solid fa-bell mr-3" style="color: #3498db;"></i>
            消息中心
            <v-badge
              v-if="unreadCount > 0"
              :content="unreadCount"
              color="error"
              class="ml-3"
            />
          </div>
          <v-btn
            v-if="unreadCount > 0"
            color="primary"
            size="small"
            @click="markAllAsRead"
          >
            <i class="fa-solid fa-check-double mr-2"></i>
            全部已读
          </v-btn>
        </v-card-title>

        <v-card-text class="pa-6">
          <!-- 错误提示 -->
          <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
            {{ error }}
          </v-alert>

          <!-- 类型筛选 -->
          <div class="mb-6 d-flex flex-wrap gap-2">
            <v-btn
              v-for="type in messageTypes"
              :key="type.value"
              :variant="filterType === type.value ? 'flat' : 'tonal'"
              :color="filterType === type.value ? type.color || 'grey' : 'grey'"
              size="small"
              rounded="pill"
              @click="filterType = type.value; handleFilterChange()"
            >
              {{ type.label }}
            </v-btn>
          </div>

          <!-- 加载状态 -->
          <v-skeleton-loader
            v-if="loading"
            type="list-item-avatar-three-line@3"
            class="mb-3"
          />

          <!-- 消息列表 -->
          <div v-else-if="messages.length > 0">
            <v-card
              v-for="message in messages"
              :key="message.id"
              class="mb-3 message-card"
              :class="{ 'unread-message': !message.isRead }"
              elevation="1"
              hover
              @click="selectMessage(message)"
            >
              <div class="pa-4">
                <div class="d-flex align-start">
                  <!-- 未读指示器 -->
                  <div class="mr-3 mt-1">
                    <v-avatar
                      :color="message.isRead ? 'grey-lighten-2' : 'primary'"
                      size="8"
                    />
                  </div>

                  <!-- 消息内容 -->
                  <div class="flex-grow-1">
                    <div class="d-flex align-center mb-2">
                      <v-chip
                        :color="typeChipColor(message.type)"
                        size="x-small"
                        variant="tonal"
                        class="mr-2"
                      >
                        {{ typeLabel(message.type) }}
                      </v-chip>
                      <h3 class="message-title">{{ message.title }}</h3>
                    </div>

                    <p class="message-content text-grey-darken-1 mb-2">
                      {{ message.content }}
                    </p>

                    <div class="d-flex align-center gap-3">
                      <v-chip
                        v-if="message.animeTitle"
                        size="x-small"
                        variant="outlined"
                        color="primary"
                        @click.stop="goToAnime(message.animeId)"
                      >
                        <i class="fa-solid fa-tv mr-1"></i>
                        {{ message.animeTitle }}
                      </v-chip>
                      <span class="text-caption text-grey">
                        {{ formatTime(message.createdAt) }}
                      </span>
                    </div>
                  </div>

                  <!-- 操作按钮 -->
                  <div class="ml-3">
                    <v-btn
                      icon
                      size="small"
                      variant="text"
                      @click.stop="deleteMessage(message.id)"
                    >
                      <i class="fa-solid fa-trash"></i>
                    </v-btn>
                  </div>
                </div>
              </div>
            </v-card>
          </div>

          <!-- 空状态 -->
          <v-alert v-else type="info" variant="tonal" class="text-center">
            <div class="text-h6 mb-2">暂无消息</div>
            <div>当有新的番剧更新或系统通知时，会在这里显示</div>
          </v-alert>

          <!-- 分页 -->
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

    <!-- 消息详情对话框 -->
    <v-dialog v-model="selectedMessage" max-width="600">
      <v-card v-if="selectedMessage">
        <v-card-title class="bg-grey-lighten-4">
          <div class="d-flex align-center">
            <v-chip
              :color="typeChipColor(selectedMessage.type)"
              size="small"
              variant="tonal"
              class="mr-3"
            >
              {{ typeLabel(selectedMessage.type) }}
            </v-chip>
            {{ selectedMessage.title }}
          </div>
        </v-card-title>

        <v-card-text class="pa-6">
          <p class="message-detail-content mb-4">
            {{ selectedMessage.content }}
          </p>

          <v-divider class="mb-4" />

          <div v-if="selectedMessage.animeTitle" class="mb-4">
            <div class="text-caption text-grey mb-2">相关番剧</div>
            <v-chip
              color="primary"
              variant="tonal"
              @click="goToAnime(selectedMessage.animeId); selectedMessage = null"
            >
              <i class="fa-solid fa-tv mr-2"></i>
              {{ selectedMessage.animeTitle }}
            </v-chip>
          </div>

          <div class="text-caption text-grey">
            <div class="mb-1">
              <i class="fa-solid fa-clock mr-2"></i>
              发送时间: {{ formatDateTime(selectedMessage.createdAt) }}
            </div>
            <div v-if="selectedMessage.isRead">
              <i class="fa-solid fa-envelope-open mr-2"></i>
              阅读时间: {{ formatDateTime(selectedMessage.readAt) }}
            </div>
          </div>
        </v-card-text>

        <v-card-actions>
          <v-spacer />
          <v-btn
            color="error"
            variant="text"
            @click="deleteMessage(selectedMessage.id)"
          >
            <i class="fa-solid fa-trash mr-2"></i>
            删除
          </v-btn>
          <v-btn
            color="primary"
            variant="text"
            @click="selectedMessage = null"
          >
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.messages-page {
  padding: 24px 0;
}

.message-card {
  transition: all 0.3s ease;
  cursor: pointer;
}

.message-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}

.unread-message {
  border-left: 4px solid #4b7bec;
  background-color: #f0f7ff;
}

.message-title {
  font-size: 1rem;
  font-weight: 600;
  color: #2c3e50;
  line-height: 1.4;
}

.message-content {
  font-size: 0.875rem;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.message-detail-content {
  font-size: 0.95rem;
  line-height: 1.8;
  color: #555;
  white-space: pre-wrap;
}

.gap-2 {
  gap: 8px;
}

.gap-3 {
  gap: 12px;
}
</style>

