<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'
import DownloadTaskTable from '../../../components/admin/download/DownloadTaskTable.vue'

const API_BASE = '/api'

const tasks = ref([])
const refreshingTasks = ref(false)
const actionLoadingTaskId = ref(null)
const sseConnected = ref(false)

const bindingDialog = ref(false)
const currentBinding = ref(null)
const bindingLoading = ref(false)

let eventSource = null

const refreshTasks = async () => {
  refreshingTasks.value = true
  try {
    const res = await axios.get(`${API_BASE}/resource-search/download-tasks`)
    tasks.value = res.data?.data || []
  } catch (error) {
    console.error('刷新下载任务失败:', error)
  } finally {
    refreshingTasks.value = false
  }
}

const connectProgressStream = () => {
  const token = localStorage.getItem('token') || ''
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  eventSource = new EventSource(`${API_BASE}/resource-search/download-tasks/stream?satoken=${encodeURIComponent(token)}`)
  eventSource.addEventListener('download-progress', (event) => {
    try {
      const payload = JSON.parse(event.data || '[]')
      tasks.value = Array.isArray(payload) ? payload : []
      sseConnected.value = true
    } catch (error) {
      console.error('解析 SSE 数据失败:', error)
    }
  })
  eventSource.onerror = () => {
    sseConnected.value = false
  }
}

const cancelTask = async (task) => {
  actionLoadingTaskId.value = task.id
  try {
    const res = await axios.post(`${API_BASE}/resource-search/download-tasks/${task.id}/cancel`)
    if (res.data?.code === 200) {
      showAppMessage('任务已取消', 'success')
    } else {
      showAppMessage(res.data?.msg || '取消失败', 'error')
    }
  } catch (error) {
    console.error('取消任务失败:', error)
    showAppMessage(error.response?.data?.msg || '取消失败', 'error')
  } finally {
    actionLoadingTaskId.value = null
  }
}

const retryTask = async (task) => {
  actionLoadingTaskId.value = task.id
  try {
    const res = await axios.post(`${API_BASE}/resource-search/download-tasks/${task.id}/retry`)
    if (res.data?.code === 200) {
      showAppMessage('已创建重试任务', 'success')
    } else {
      showAppMessage(res.data?.msg || '重试失败', 'error')
    }
  } catch (error) {
    console.error('重试任务失败:', error)
    showAppMessage(error.response?.data?.msg || '重试失败', 'error')
  } finally {
    actionLoadingTaskId.value = null
  }
}

const deleteTask = async (task) => {
  actionLoadingTaskId.value = task.id
  try {
    const res = await axios.delete(`${API_BASE}/resource-search/download-tasks/${task.id}`)
    if (res.data?.code === 200) {
      showAppMessage('任务已删除', 'success')
      tasks.value = tasks.value.filter((item) => item.id !== task.id)
    } else {
      showAppMessage(res.data?.msg || '删除失败', 'error')
    }
  } catch (error) {
    console.error('删除任务失败:', error)
    showAppMessage(error.response?.data?.msg || '删除失败', 'error')
  } finally {
    actionLoadingTaskId.value = null
  }
}

const openBinding = async (taskId) => {
  bindingLoading.value = true
  bindingDialog.value = true
  currentBinding.value = null
  try {
    const res = await axios.get(`${API_BASE}/resource-search/download-tasks/${taskId}/binding`)
    currentBinding.value = res.data?.data || null
  } catch (error) {
    console.error('查询绑定状态失败:', error)
    showAppMessage(error.response?.data?.msg || '查询绑定状态失败', 'error')
  } finally {
    bindingLoading.value = false
  }
}

onMounted(async () => {
  await refreshTasks()
  connectProgressStream()
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<template>
  <div>
    <DownloadTaskTable
      title="下载任务进度"
      :tasks="tasks"
      :refreshing-tasks="refreshingTasks"
      :action-loading-task-id="actionLoadingTaskId"
      :sse-connected="sseConnected"
      :show-refresh-button="true"
      @refresh="refreshTasks"
      @cancel="cancelTask"
      @retry="retryTask"
      @delete="deleteTask"
      @binding="openBinding"
    />

    <v-dialog v-model="bindingDialog" max-width="720">
      <v-card>
        <v-card-title>
          <v-icon start color="primary">mdi-link-variant</v-icon>
          下载任务绑定状态
        </v-card-title>
        <v-card-text>
          <v-progress-linear v-if="bindingLoading" indeterminate color="primary" class="mb-4" />
          <template v-else-if="currentBinding">
            <v-list lines="two">
              <v-list-item title="任务状态" :subtitle="currentBinding.taskStatus" />
              <v-list-item title="最终路径" :subtitle="currentBinding.finalPath || '-'" />
              <v-list-item title="媒体文件ID" :subtitle="currentBinding.mediaFileId || '-'" />
              <v-list-item title="绑定结果" :subtitle="currentBinding.mediaFileExists ? '已进入媒体库数据库' : '未找到对应媒体文件记录'" />
              <v-list-item title="动漫ID" :subtitle="currentBinding.animeId || '-'" />
              <v-list-item title="动漫标题" :subtitle="currentBinding.animeTitle || '-'" />
              <v-list-item title="剧集ID" :subtitle="currentBinding.episodeId || '-'" />
              <v-list-item title="剧集标题" :subtitle="currentBinding.episodeTitle || '-'" />
              <v-list-item title="匹配状态" :subtitle="currentBinding.matchStatus || '-'" />
            </v-list>
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="bindingDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
