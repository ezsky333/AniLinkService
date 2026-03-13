<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { showAppMessage } from '../../../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const saving = ref(false)
const subscriptions = ref([])
const libraries = ref([])
const dialog = ref(false)
const editingId = ref(null)
const contentDialog = ref(false)
const contentLoading = ref(false)
const contentTitle = ref('')
const contentCheckedAt = ref(null)
const fetchedContent = ref('')
const triggeringId = ref(null)

const formatLocalDateTime = (value) => {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const form = ref({
  name: '',
  feedUrl: '',
  libraryId: null,
  intervalMinutes: 30,
  enabled: true
})

const fetchLibraries = async () => {
  const res = await axios.get(`${API_BASE}/media-library`)
  libraries.value = (res.data?.data || []).map((item) => ({ ...item, id: String(item.id) }))
}

const fetchSubscriptions = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/resource-search/rss-subscriptions`)
    subscriptions.value = res.data?.data || []
  } catch (error) {
    console.error('获取 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '获取 RSS 订阅失败', 'error')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.value = {
    name: '',
    feedUrl: '',
    libraryId: libraries.value.length > 0 ? String(libraries.value[0].id) : null,
    intervalMinutes: 30,
    enabled: true
  }
  dialog.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.value = {
    name: row.name,
    feedUrl: row.feedUrl,
    libraryId: row.libraryId != null ? String(row.libraryId) : null,
    intervalMinutes: row.intervalMinutes || 30,
    enabled: row.enabled !== false
  }
  dialog.value = true
}

const saveSubscription = async () => {
  if (!form.value.name || !form.value.feedUrl || !form.value.libraryId) {
    showAppMessage('请填写订阅源名称、RSS 地址和目标媒体库', 'warning')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      feedUrl: form.value.feedUrl,
      libraryId: String(form.value.libraryId),
      intervalMinutes: Math.max(1, Number(form.value.intervalMinutes || 30)),
      enabled: !!form.value.enabled
    }
    let res
    if (editingId.value) {
      res = await axios.put(`${API_BASE}/resource-search/rss-subscriptions/${editingId.value}`, payload)
    } else {
      res = await axios.post(`${API_BASE}/resource-search/rss-subscriptions`, payload)
    }
    if (res.data?.code === 200) {
      showAppMessage('保存成功', 'success')
      dialog.value = false
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '保存失败', 'error')
    }
  } catch (error) {
    console.error('保存 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

const deleteSubscription = async (row) => {
  try {
    const res = await axios.delete(`${API_BASE}/resource-search/rss-subscriptions/${row.id}`)
    if (res.data?.code === 200) {
      showAppMessage('删除成功', 'success')
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '删除失败', 'error')
    }
  } catch (error) {
    console.error('删除 RSS 订阅失败:', error)
    showAppMessage(error.response?.data?.msg || '删除失败', 'error')
  }
}

const triggerNow = async (row) => {
  triggeringId.value = row.id
  try {
    const res = await axios.post(`${API_BASE}/resource-search/rss-subscriptions/${row.id}/trigger`)
    if (res.data?.code === 200) {
      showAppMessage('已触发检查', 'success')
      await fetchSubscriptions()
    } else {
      showAppMessage(res.data?.msg || '触发失败', 'error')
    }
  } catch (error) {
    console.error('触发 RSS 检查失败:', error)
    showAppMessage(error.response?.data?.msg || '触发失败', 'error')
  } finally {
    triggeringId.value = null
  }
}

const viewLastFetchedContent = async (row) => {
  contentDialog.value = true
  contentLoading.value = true
  contentTitle.value = row?.name || 'RSS 订阅'
  contentCheckedAt.value = null
  fetchedContent.value = ''
  try {
    const res = await axios.get(`${API_BASE}/resource-search/rss-subscriptions/${row.id}/last-content`)
    if (res.data?.code === 200 && res.data?.data) {
      contentTitle.value = res.data.data.name || contentTitle.value
      contentCheckedAt.value = res.data.data.lastCheckedAt || null
      fetchedContent.value = res.data.data.lastFetchedContent || ''
    } else {
      showAppMessage(res.data?.msg || '获取解析结果失败', 'error')
    }
  } catch (error) {
    console.error('获取 RSS 解析结果失败:', error)
    showAppMessage(error.response?.data?.msg || '获取解析结果失败', 'error')
  } finally {
    contentLoading.value = false
  }
}

onMounted(async () => {
  await fetchLibraries()
  await fetchSubscriptions()
})
</script>

<template>
  <div>
    <v-card>
      <v-card-title class="d-flex align-center justify-space-between">
        <span>
          <v-icon start>mdi-rss-box</v-icon>
          RSS 订阅下载
        </span>
        <v-btn color="primary" @click="openCreate">
          <v-icon start>mdi-plus</v-icon>
          新建订阅
        </v-btn>
      </v-card-title>
      <v-card-text>
        <v-table density="compact" fixed-header height="460">
          <thead>
            <tr>
              <th>名称</th>
              <th>RSS 地址</th>
              <th>目标媒体库</th>
              <th>间隔(分钟)</th>
              <th>启用</th>
              <th>最近检查</th>
              <th>错误</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in subscriptions" :key="row.id">
              <td>{{ row.name }}</td>
              <td class="ellipsis">{{ row.feedUrl }}</td>
              <td>{{ row.libraryName || row.libraryId }}</td>
              <td>{{ row.intervalMinutes }}</td>
              <td>
                <v-chip :color="row.enabled ? 'success' : 'grey'" size="small" variant="tonal">
                  {{ row.enabled ? '启用' : '停用' }}
                </v-chip>
              </td>
              <td>{{ formatLocalDateTime(row.lastCheckedAt) }}</td>
              <td class="ellipsis">{{ row.lastError || '-' }}</td>
              <td>
                <div class="d-flex ga-2">
                  <v-btn size="small" variant="outlined" color="info" :loading="triggeringId === row.id" @click="triggerNow(row)">立即检查</v-btn>
                  <v-btn size="small" variant="outlined" color="secondary" @click="viewLastFetchedContent(row)">查看解析结果</v-btn>
                  <v-btn size="small" variant="outlined" @click="openEdit(row)">编辑</v-btn>
                  <v-btn size="small" variant="outlined" color="error" @click="deleteSubscription(row)">删除</v-btn>
                </div>
              </td>
            </tr>
            <tr v-if="subscriptions.length === 0">
              <td colspan="8" class="text-center text-medium-emphasis py-6">暂无 RSS 订阅</td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>

    <v-dialog v-model="dialog" max-width="720">
      <v-card>
        <v-card-title>{{ editingId ? '编辑订阅' : '新建订阅' }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="form.name" label="订阅源名称" variant="outlined" class="mb-3" />
          <v-text-field v-model="form.feedUrl" label="RSS 地址" variant="outlined" class="mb-3" />
          <v-select
            v-model="form.libraryId"
            :items="libraries"
            item-title="name"
            item-value="id"
            label="目标媒体库"
            variant="outlined"
            class="mb-3"
          />
          <v-text-field
            v-model.number="form.intervalMinutes"
            type="number"
            min="1"
            label="更新间隔（分钟）"
            hint="最小 1 分钟"
            persistent-hint
            variant="outlined"
            class="mb-3"
          />
          <v-switch v-model="form.enabled" label="启用订阅" color="primary" inset />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">取消</v-btn>
          <v-btn color="primary" :loading="saving" @click="saveSubscription">保存</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="contentDialog" max-width="960">
      <v-card>
        <v-card-title class="d-flex align-center justify-space-between">
          <span>
            {{ contentTitle }} - 最近解析结果
          </span>
          <v-chip size="small" variant="tonal" color="primary">
            最近检查: {{ formatLocalDateTime(contentCheckedAt) }}
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-skeleton-loader v-if="contentLoading" type="paragraph@6" />
          <div v-else class="fetched-content-box">
            <pre v-if="fetchedContent">{{ fetchedContent }}</pre>
            <div v-else class="text-medium-emphasis">暂无解析结果，请先执行一次“立即检查”</div>
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" variant="text" @click="contentDialog = false">关闭</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.ellipsis {
  max-width: 280px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.fetched-content-box {
  max-height: 65vh;
  overflow: auto;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  background: #fafafa;
}

.fetched-content-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}
</style>
