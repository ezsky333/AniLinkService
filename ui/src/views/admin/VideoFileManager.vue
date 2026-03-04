<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const API_BASE = '/api'

const mediaFiles = ref([])
const loading = ref(false)
const detailDialog = ref(false)
const editDialog = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const selectedLibraryId = ref(null)
const mediaLibraries = ref([])
const queueStatus = ref(null)
const reprocessingLibraryId = ref(null)

const selectedFile = ref(null)
const editingFile = ref({
  episodeId: '',
  animeId: null,
  animeTitle: '',
  episodeTitle: ''
})

const headers = [
  { title: '文件名', key: 'fileName', width: '30%' },
  { title: '大小', key: 'size', width: '10%' },
  { title: '状态', key: 'metadataFetched', width: '10%' },
  { title: '视频编码', key: 'videoCodec', width: '12%' },
  { title: '分辨率', key: 'resolution', width: '12%' },
  { title: '操作', key: 'actions', width: '26%', sortable: false }
]

// 获取媒体库列表
const fetchLibraries = async () => {
  try {
    const res = await axios.get(`${API_BASE}/media-library`)
    if (res.data?.code === 200) {
      mediaLibraries.value = res.data.data || []
    }
  } catch (error) {
    console.error('获取媒体库失败:', error)
  }
}

// 获取媒体文件列表
const fetchMediaFiles = async () => {
  loading.value = true
  try {
    const params = {}
    if (selectedLibraryId.value) {
      params.libraryId = selectedLibraryId.value
    }

    const res = await axios.get(`${API_BASE}/media-files`, { params })
    if (res.data?.code === 200 && res.data?.data) {
      mediaFiles.value = res.data.data.content || []
    }
  } catch (error) {
    console.error('获取媒体文件失败:', error)
    errorMessage.value = '获取文件列表失败'
  } finally {
    loading.value = false
  }
}

// 获取队列状态
const fetchQueueStatus = async () => {
  try {
    const res = await axios.get(`${API_BASE}/media-files/queue/status`)
    if (res.data?.code === 200) {
      queueStatus.value = res.data.data
    }
  } catch (error) {
    console.error('获取队列状态失败:', error)
  }
}

// 查看文件详情
const viewFileDetail = (file) => {
  selectedFile.value = { ...file }
  detailDialog.value = true
}

// 编辑文件信息
const editFile = (file) => {
  selectedFile.value = file
  editingFile.value = {
    episodeId: file.episodeId || '',
    animeId: file.animeId || null,
    animeTitle: file.animeTitle || '',
    episodeTitle: file.episodeTitle || ''
  }
  editDialog.value = true
}

// 保存编辑
const saveEdit = async () => {
  if (!selectedFile.value?.id) return

  loading.value = true
  try {
    const res = await axios.put(`${API_BASE}/media-files/${selectedFile.value.id}`, editingFile.value)
    if (res.data?.code === 200) {
      successMessage.value = '更新成功'
      editDialog.value = false
      await fetchMediaFiles()
      setTimeout(() => { successMessage.value = '' }, 3000)
    }
  } catch (error) {
    errorMessage.value = '更新失败: ' + (error.response?.data?.msg || error.message)
  } finally {
    loading.value = false
  }
}

// 删除文件
const deleteFile = async (fileId, deletePhysicalFile = false) => {
  if (!confirm(`确定要删除此文件${deletePhysicalFile ? '及其物理文件' : ''}吗？`)) {
    return
  }

  loading.value = true
  try {
    const res = await axios.delete(`${API_BASE}/media-files/${fileId}`, {
      params: { deleteFile: deletePhysicalFile }
    })
    if (res.data?.code === 200) {
      successMessage.value = '删除成功'
      await fetchMediaFiles()
      setTimeout(() => { successMessage.value = '' }, 3000)
    }
  } catch (error) {
    errorMessage.value = '删除失败: ' + (error.response?.data?.msg || error.message)
  } finally {
    loading.value = false
  }
}

// 批量重新获取元数据
const reprocessMetadata = async (libraryId) => {
  if (!libraryId) {
    errorMessage.value = '请先选择媒体库'
    return
  }

  if (!confirm('确定要重新获取此媒体库中所有文件的元数据吗？')) {
    return
  }

  reprocessingLibraryId.value = libraryId
  try {
    const res = await axios.post(`${API_BASE}/media-files/reprocess-metadata/${libraryId}`)
    if (res.data?.code === 200) {
      successMessage.value = '已提交重新获取任务'
      // 5秒后刷新队列状态
      setTimeout(() => {
        fetchQueueStatus()
      }, 5000)
    }
  } catch (error) {
    errorMessage.value = '提交任务失败: ' + (error.response?.data?.msg || error.message)
  } finally {
    reprocessingLibraryId.value = null
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

// 格式化时长
const formatDuration = (ms) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  if (hours > 0) {
    return `${hours}h ${minutes}m ${secs}s`
  }
  return `${minutes}m ${secs}s`
}

// 获取分辨率
const getResolution = (file) => {
  if (file.width && file.height) {
    return `${file.width}x${file.height}`
  }
  return '-'
}

// 库选择变化
const handleLibraryChange = () => {
  fetchMediaFiles()
}

onMounted(() => {
  fetchLibraries()
  fetchMediaFiles()
  fetchQueueStatus()
})
</script>

<template>
  <div>
    <!-- 警告和成功提示 -->
    <v-alert v-if="errorMessage" type="error" class="mb-4" closable @update:model-value="v => { if (!v) errorMessage = '' }">
      {{ errorMessage }}
    </v-alert>
    <v-alert v-if="successMessage" type="success" class="mb-4" closable @update:model-value="v => { if (!v) successMessage = '' }">
      {{ successMessage }}
    </v-alert>

    <!-- 操作工具栏 -->
    <v-card class="mb-4">
      <v-card-text class="pa-4">
        <div class="d-flex gap-2 align-center flex-wrap">
          <v-select
            v-model="selectedLibraryId"
            :items="mediaLibraries"
            item-title="name"
            item-value="id"
            label="选择媒体库"
            variant="outlined"
            density="compact"
            style="max-width: 300px"
            @update:model-value="handleLibraryChange"
            clearable
          />
          <v-btn
            v-if="selectedLibraryId"
            color="warning"
            variant="elevated"
            size="small"
            :loading="reprocessingLibraryId === selectedLibraryId"
            @click="reprocessMetadata(selectedLibraryId)"
          >
            <v-icon start>mdi-refresh</v-icon>
            重新获取元数据
          </v-btn>
          <v-spacer />

          <!-- 队列状态 -->
          <v-chip
            v-if="queueStatus"
            prepend-icon="mdi-queue"
            variant="outlined"
            color="info"
          >
            待处理: {{ queueStatus.pendingTasks }} | 活跃: {{ queueStatus.activeThreads }}/{{ queueStatus.maxPoolSize }}
          </v-chip>
        </div>
      </v-card-text>
    </v-card>

    <!-- 文件列表 -->
    <v-card>
      <v-data-table
        :headers="headers"
        :items="mediaFiles"
        :loading="loading"
        density="compact"
        class="elevation-0"
      >
  
        <template v-slot:item.size="{ item }">
          <span class="text-caption">{{ formatFileSize(item.size) }}</span>
        </template>

        <template v-slot:item.metadataFetched="{ item }">
          <v-chip
            :color="item.metadataFetched ? 'success' : 'warning'"
            size="small"
            label
          >
            {{ item.metadataFetched ? '已解析' : '待解析' }}
          </v-chip>
        </template>

        <template v-slot:item.videoCodec="{ item }">
          <span class="text-caption">{{ item.videoCodec || '-' }}</span>
        </template>

        <template v-slot:item.resolution="{ item }">
          <span class="text-caption">{{ getResolution(item) }}</span>
        </template>

        <template v-slot:item.actions="{ item }">
          <div class="d-flex gap-1">
            <v-btn
              icon="mdi-eye"
              variant="text"
              size="x-small"
              color="info"
              @click="viewFileDetail(item)"
            />
            <v-btn
              icon="mdi-pencil"
              variant="text"
              size="x-small"
              color="primary"
              @click="editFile(item)"
            />
            <v-menu>
              <template v-slot:activator="{ props }">
                <v-btn
                  icon="mdi-delete"
                  variant="text"
                  size="x-small"
                  color="error"
                  v-bind="props"
                />
              </template>
              <v-list>
                <v-list-item @click="deleteFile(item.id, false)">
                  <v-list-item-title>删除记录</v-list-item-title>
                </v-list-item>
                <v-list-item @click="deleteFile(item.id, true)">
                  <v-list-item-title>删除记录及文件</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </div>
        </template>

        <template v-slot:no-data>
          <div class="text-center py-8">
            <v-icon size="64" color="grey-lighten-1">mdi-file-video-outline</v-icon>
            <p class="text-body-1 mt-4 text-grey">暂无视频文件</p>
          </div>
        </template>
      </v-data-table>
    </v-card>

    <!-- 文件详情对话框 -->
    <v-dialog v-model="detailDialog" max-width="900">
      <v-card v-if="selectedFile">
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">文件详情</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="detailDialog = false" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-row>
            <v-col cols="12" md="6">
              <v-list density="compact">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件名</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.fileName }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件路径</v-list-item-title>
                  <v-list-item-subtitle class="text-caption" style="word-break: break-all;">{{ selectedFile.filePath }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">文件大小</v-list-item-title>
                  <v-list-item-subtitle>{{ formatFileSize(selectedFile.size) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">时长</v-list-item-title>
                  <v-list-item-subtitle>{{ formatDuration(selectedFile.duration) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">MD5 哈希</v-list-item-title>
                  <v-list-item-subtitle class="font-monospace text-caption">{{ selectedFile.hash || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
            <v-col cols="12" md="6">
              <v-list density="compact">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">分辨率</v-list-item-title>
                  <v-list-item-subtitle>{{ getResolution(selectedFile) }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">帧率</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.fps ? selectedFile.fps.toFixed(2) + ' fps' : '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">视频编码</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.videoCodec || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">音频编码</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.audioCodec || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">容器格式</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.containerFormat || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">HDR 类型</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.hdrType || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">色彩空间</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.colorSpace || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
          </v-row>

          <v-divider class="my-4" />

          <v-row>
            <v-col cols="12">
              <span class="text-subtitle-2 font-weight-medium">动漫信息</span>
              <v-list density="compact" class="mt-2">
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">番名</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.animeTitle || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">话数</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.episodeTitle || '-' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title class="text-caption text-grey">弹幕库ID</v-list-item-title>
                  <v-list-item-subtitle>{{ selectedFile.episodeId || '-' }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-col>
          </v-row>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn color="primary" variant="elevated" @click="editFile(selectedFile)">
            编辑信息
          </v-btn>
          <v-btn color="grey" variant="text" @click="detailDialog = false">
            关闭
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- 编辑对话框 -->
    <v-dialog v-model="editDialog" max-width="600">
      <v-card v-if="selectedFile">
        <v-toolbar color="primary" flat>
          <v-toolbar-title class="text-white">编辑文件信息</v-toolbar-title>
          <v-spacer />
          <v-btn icon="mdi-close" color="white" @click="editDialog = false" />
        </v-toolbar>

        <v-card-text class="pa-6">
          <v-form>
            <v-text-field
              v-model="editingFile.animeTitle"
              label="番名"
              variant="outlined"
              color="primary"
              class="mb-4"
            />

            <v-text-field
              v-model="editingFile.episodeTitle"
              label="话数 / 标题"
              variant="outlined"
              color="primary"
              class="mb-4"
            />

            <v-text-field
              v-model.number="editingFile.animeId"
              label="番号"
              type="number"
              variant="outlined"
              color="primary"
              class="mb-4"
            />

            <v-text-field
              v-model="editingFile.episodeId"
              label="弹幕库 ID"
              variant="outlined"
              color="primary"
            />
          </v-form>
        </v-card-text>

        <v-divider />

        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn color="grey" variant="text" @click="editDialog = false">
            取消
          </v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            :loading="loading"
            @click="saveEdit"
          >
            保存
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
:deep(.v-data-table) {
  background: transparent;
}

.font-monospace {
  font-family: 'Courier New', monospace;
}
</style>
