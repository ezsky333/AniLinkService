<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'
import DownloadTaskTable from '../../../components/admin/download/DownloadTaskTable.vue'

const API_BASE = '/api'

const loading = ref(false)
const searching = ref(false)
const creatingTask = ref(false)
const refreshingTasks = ref(false)
const actionLoadingTaskId = ref(null)

const keyword = ref('')
const subgroup = ref(null)
const type = ref(null)

const subgroups = ref([])
const types = ref([])
const libraries = ref([])
const selectedLibraryId = ref(null)

const hasMore = ref(false)
const resources = ref([])
const tasks = ref([])

const bindingDialog = ref(false)
const currentBinding = ref(null)
const bindingLoading = ref(false)

const sseConnected = ref(false)

let eventSource = null

const fetchSubgroups = async () => {
  try {
    const subgroupRes = await axios.get(`${API_BASE}/resource-search/subgroup`)
    subgroups.value = subgroupRes.data?.data || []
  } catch (error) {
    console.error('加载字幕组失败:', error)
  }
}

const fetchTypes = async () => {
  try {
    const typeRes = await axios.get(`${API_BASE}/resource-search/type`)
    types.value = typeRes.data?.data || []
  } catch (error) {
    console.error('加载资源类型失败:', error)
  }
}

const fetchLibraries = async () => {
  try {
    const libraryRes = await axios.get(`${API_BASE}/media-library`)
    const rawLibraries = libraryRes.data?.data || []
    libraries.value = rawLibraries.map((item) => ({
      ...item,
      id: String(item.id)
    }))

    const selected = selectedLibraryId.value != null ? String(selectedLibraryId.value) : null
    const exists = selected != null && libraries.value.some((item) => item.id === selected)
    if (!exists && libraries.value.length > 0) {
      selectedLibraryId.value = libraries.value[0].id
    }
  } catch (error) {
    console.error('加载媒体库失败:', error)
    showAppMessage(error.response?.data?.msg || '加载媒体库失败', 'error')
  }
}

const searchResources = async () => {
  if (!keyword.value.trim()) {
    showAppMessage('请输入搜索关键词', 'warning')
    return
  }
  searching.value = true
  try {
    const res = await axios.get(`${API_BASE}/resource-search/list`, {
      params: {
        keyword: keyword.value.trim(),
        subgroup: subgroup.value,
        type: type.value
      }
    })
    const payload = res.data?.data || {}
    resources.value = payload.resources || []
    hasMore.value = payload.hasMore === true
  } catch (error) {
    console.error('搜索资源失败:', error)
    showAppMessage(error.response?.data?.msg || '搜索资源失败', 'error')
  } finally {
    searching.value = false
  }
}

const createDownloadTask = async (row) => {
  if (!selectedLibraryId.value) {
    showAppMessage('请先选择媒体库', 'warning')
    return
  }

  creatingTask.value = true
  try {
    const res = await axios.post(`${API_BASE}/resource-search/download`, {
      title: row.title,
      magnet: row.magnet,
      pageUrl: row.pageUrl,
      fileSize: row.fileSize,
      publishDate: row.publishDate,
      subgroupName: row.subgroupName,
      typeName: row.typeName,
      libraryId: String(selectedLibraryId.value)
    })

    if (res.data?.code === 200) {
      showAppMessage('下载任务已创建', 'success')
      await refreshTasks()
    } else {
      showAppMessage(res.data?.msg || '创建下载任务失败', 'error')
    }
  } catch (error) {
    console.error('创建下载任务失败:', error)
    showAppMessage(error.response?.data?.msg || '创建下载任务失败', 'error')
  } finally {
    creatingTask.value = false
  }
}

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
  loading.value = true
  Promise.allSettled([fetchSubgroups(), fetchTypes(), fetchLibraries()]).finally(() => {
    loading.value = false
  })
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
    <v-card class="mb-4">
      <v-card-title>
        <v-icon start>mdi-cloud-search</v-icon>
        资源搜索与下载
      </v-card-title>
      <v-card-text>
        <v-row dense>
          <v-col cols="12" md="4">
            <v-text-field
              v-model="keyword"
              label="搜索关键词"
              prepend-inner-icon="mdi-magnify"
              variant="outlined"
              hide-details
              @keyup.enter="searchResources"
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="subgroup"
              :items="subgroups"
              item-title="name"
              item-value="id"
              label="字幕组"
              clearable
              variant="outlined"
              hide-details
            />
          </v-col>
          <v-col cols="12" md="2">
            <v-select
              v-model="type"
              :items="types"
              item-title="name"
              item-value="id"
              label="资源类型"
              clearable
              variant="outlined"
              hide-details
            />
          </v-col>
          <v-col cols="12" md="3">
            <v-select
              v-model="selectedLibraryId"
              :items="libraries"
              item-title="name"
              item-value="id"
              label="目标媒体库"
              variant="outlined"
              hide-details
            />
          </v-col>
        </v-row>

        <div class="mt-4 d-flex ga-3">
          <v-btn color="primary" :loading="searching" :disabled="loading || searching" @click="searchResources">
            <v-icon start>mdi-magnify</v-icon>
            搜索
          </v-btn>
          <v-btn variant="outlined" :loading="refreshingTasks" @click="refreshTasks">
            <v-icon start>mdi-refresh</v-icon>
            刷新任务
          </v-btn>
        </div>

        <v-alert v-if="hasMore" class="mt-4" type="warning" variant="tonal" density="comfortable">
          搜索结果过多，当前仅显示部分结果。
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card class="mb-4">
      <v-card-title>搜索结果</v-card-title>
      <v-card-text>
        <v-table density="compact" fixed-header height="340">
          <thead>
            <tr>
              <th>标题</th>
              <th>类型</th>
              <th>字幕组</th>
              <th>大小</th>
              <th>发布时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in resources" :key="`${row.magnet}-${row.title}`">
              <td class="title-cell">{{ row.title }}</td>
              <td>{{ row.typeName }}</td>
              <td>{{ row.subgroupName }}</td>
              <td>{{ row.fileSize }}</td>
              <td>{{ row.publishDate }}</td>
              <td>
                <v-btn
                  size="small"
                  color="teal-darken-1"
                  :loading="creatingTask"
                  @click="createDownloadTask(row)"
                >
                  下载
                </v-btn>
              </td>
            </tr>
            <tr v-if="resources.length === 0">
              <td colspan="6" class="text-center text-medium-emphasis py-6">暂无搜索结果</td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>

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

<style scoped>
.title-cell {
  max-width: 420px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
