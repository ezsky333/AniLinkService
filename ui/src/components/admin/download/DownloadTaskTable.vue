<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: {
    type: String,
    default: '下载任务进度'
  },
  tasks: {
    type: Array,
    default: () => []
  },
  refreshingTasks: {
    type: Boolean,
    default: false
  },
  actionLoadingTaskId: {
    type: [String, Number, null],
    default: null
  },
  sseConnected: {
    type: Boolean,
    default: false
  },
  showRefreshButton: {
    type: Boolean,
    default: true
  },
  maxRenderCount: {
    type: Number,
    default: 80
  }
})

const emit = defineEmits(['refresh', 'cancel', 'retry', 'delete', 'binding'])

const formatStatus = (status) => {
  const map = {
    PENDING: '等待中',
    RUNNING: '下载中',
    MOVING: '迁移中',
    SCANNING: '扫描中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAILED: '失败'
  }
  return map[status] || status
}

const statusColor = (status) => {
  const map = {
    PENDING: 'grey',
    RUNNING: 'primary',
    MOVING: 'info',
    SCANNING: 'teal',
    COMPLETED: 'success',
    CANCELLED: 'warning',
    FAILED: 'error'
  }
  return map[status] || 'grey'
}

const canCancel = (status) => ['PENDING', 'RUNNING', 'MOVING', 'SCANNING'].includes(status)
const canRetry = (status) => ['FAILED', 'CANCELLED'].includes(status)
const canDelete = (status) => ['COMPLETED', 'FAILED', 'CANCELLED'].includes(status)

const parseSpeedToBps = (value) => {
  if (!value || typeof value !== 'string') return 0
  const normalized = value.trim().toUpperCase()
  const match = normalized.match(/([0-9]+(?:\.[0-9]+)?)\s*(B|KB|MB|GB)(?:\/S)?/)
  if (!match) return 0
  const num = Number(match[1])
  if (Number.isNaN(num)) return 0
  const unit = match[2]
  if (unit === 'B') return num
  if (unit === 'KB') return num * 1024
  if (unit === 'MB') return num * 1024 * 1024
  if (unit === 'GB') return num * 1024 * 1024 * 1024
  return 0
}

const formatSpeed = (bps) => {
  if (!bps || bps <= 0) return '0 B/s'
  if (bps < 1024) return `${Math.round(bps)} B/s`
  const kb = bps / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB/s`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB/s`
  const gb = mb / 1024
  return `${gb.toFixed(2)} GB/s`
}

const extractDownUpFromMerged = (speedText) => {
  if (!speedText || typeof speedText !== 'string') {
    return { down: '', up: '' }
  }
  const down = speedText.match(/↓\s*([0-9]+(?:\.[0-9]+)?\s*(?:B|KB|MB|GB)\/s)/i)?.[1] || ''
  const up = speedText.match(/↑\s*([0-9]+(?:\.[0-9]+)?\s*(?:B|KB|MB|GB)\/s)/i)?.[1] || ''
  return { down, up }
}

const resolveTaskSpeed = (task) => {
  const merged = extractDownUpFromMerged(task.speedText)
  const down = merged.down || task.downloadSpeedText || ''
  const up = merged.up || task.uploadSpeedText || ''
  return { down, up }
}

const activeTasks = computed(() => props.tasks.filter((item) => item.status === 'RUNNING'))

const globalDownloadBps = computed(() => {
  return activeTasks.value.reduce((sum, task) => {
    const { down } = resolveTaskSpeed(task)
    return sum + parseSpeedToBps(down)
  }, 0)
})

const globalUploadBps = computed(() => {
  return activeTasks.value.reduce((sum, task) => {
    const { up } = resolveTaskSpeed(task)
    return sum + parseSpeedToBps(up)
  }, 0)
})

const globalDownloadText = computed(() => formatSpeed(globalDownloadBps.value))
const globalUploadText = computed(() => formatSpeed(globalUploadBps.value))
const displayedTasks = computed(() => props.tasks.slice(0, Math.max(1, props.maxRenderCount || 80)))
const isTruncated = computed(() => props.tasks.length > displayedTasks.value.length)

const formatBytes = (value) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  const num = Number(value)
  if (num < 1024) return `${num} B`
  const kb = num / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(2)} MB`
  const gb = mb / 1024
  return `${gb.toFixed(2)} GB`
}
</script>

<template>
  <v-card>
    <v-card-title class="d-flex align-center justify-space-between ga-3">
      <span>{{ props.title }}</span>
      <div class="d-flex align-center ga-2">
        <v-chip size="small" color="primary" variant="tonal">
          全局下载 {{ globalDownloadText }}
        </v-chip>
        <v-chip size="small" color="info" variant="tonal">
          全局上传 {{ globalUploadText }}
        </v-chip>
        <v-btn v-if="props.showRefreshButton" size="small" variant="outlined" :loading="props.refreshingTasks" @click="emit('refresh')">
          <v-icon start>mdi-refresh</v-icon>
          刷新
        </v-btn>
        <v-chip :color="props.sseConnected ? 'success' : 'warning'" size="small" variant="tonal">
          {{ props.sseConnected ? '实时推送已连接' : '实时推送断开' }}
        </v-chip>
      </div>
    </v-card-title>
    <v-card-text>
      <v-alert v-if="isTruncated" type="info" variant="tonal" density="comfortable" class="mb-3">
        当前仅展示最近 {{ displayedTasks.length }} 条任务（总计 {{ props.tasks.length }} 条）。
      </v-alert>
      <v-table density="compact" fixed-header height="420">
        <thead>
          <tr>
            <th>任务</th>
            <th>目标库</th>
            <th>状态</th>
            <th>进度</th>
            <th>下载/上传速度</th>
            <th>已下载/总大小</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in displayedTasks" :key="task.id">
            <td class="title-cell">{{ task.title }}</td>
            <td>{{ task.libraryName || task.libraryId }}</td>
            <td>
              <v-chip :color="statusColor(task.status)" size="small" variant="flat">
                {{ formatStatus(task.status) }}
              </v-chip>
            </td>
            <td>
              <div class="d-flex align-center ga-2">
                <v-progress-linear :model-value="task.progressPercent || 0" height="8" rounded color="primary" style="width:120px;" />
                <span>{{ task.progressPercent || 0 }}%</span>
              </div>
            </td>
            <td>{{ resolveTaskSpeed(task).down || '-' }} / {{ resolveTaskSpeed(task).up || '-' }}</td>
            <td>{{ formatBytes(task.downloadedBytes) }} / {{ formatBytes(task.totalBytes) }}</td>
            <td>
              <div class="d-flex ga-2">
                <v-btn
                  size="small"
                  color="warning"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  :disabled="!canCancel(task.status)"
                  @click="emit('cancel', task)"
                >
                  取消
                </v-btn>
                <v-btn
                  size="small"
                  color="info"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  :disabled="!canRetry(task.status)"
                  @click="emit('retry', task)"
                >
                  重试
                </v-btn>
                <v-btn
                  size="small"
                  variant="outlined"
                  :disabled="task.status !== 'COMPLETED'"
                  @click="emit('binding', task.id)"
                >
                  查看绑定
                </v-btn>
                <v-btn
                  size="small"
                  color="error"
                  variant="outlined"
                  :loading="props.actionLoadingTaskId === task.id"
                  :disabled="!canDelete(task.status)"
                  @click="emit('delete', task)"
                >
                  删除
                </v-btn>
              </div>
            </td>
          </tr>
          <tr v-if="props.tasks.length === 0">
            <td colspan="7" class="text-center text-medium-emphasis py-6">暂无下载任务</td>
          </tr>
        </tbody>
      </v-table>
    </v-card-text>
  </v-card>
</template>

<style scoped>
.title-cell {
  max-width: 420px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
