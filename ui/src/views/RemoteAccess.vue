<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import QRCode from 'qrcode'
import { showAppMessage } from '../utils/ui-feedback'

const API_BASE = '/api'

const loading = ref(false)
const regenerating = ref(false)
const canvasRef = ref(null)
const credential = ref({
  remoteAccessEnabled: false,
  tokenRequired: false,
  requiredRole: 'user',
  currentUser: '',
  remoteAccessToken: ''
})

const pageHost = computed(() => {
  if (typeof window === 'undefined') {
    return ''
  }
  return window.location.hostname || ''
})

const pagePort = computed(() => {
  if (typeof window === 'undefined') {
    return 0
  }
  const port = window.location.port
  if (port) {
    return Number(port)
  }
  return window.location.protocol === 'https:' ? 443 : 80
})

const qrPayload = computed(() => {
  const payload = {
    about: '请使用支持弹弹play远程访问功能的客户端扫描此二维码',
    ip: pageHost.value ? [pageHost.value] : [],
    port: pagePort.value,
    machineName: pageHost.value || 'Unknown',
    currentUser: credential.value.currentUser || '',
    tokenRequired: !!credential.value.tokenRequired
  }

  if (credential.value.tokenRequired && credential.value.remoteAccessToken) {
    payload.token = credential.value.remoteAccessToken
  }

  return JSON.stringify(payload)
})

const drawQrCode = async () => {
  if (!canvasRef.value) {
    return
  }

  try {
    await QRCode.toCanvas(canvasRef.value, qrPayload.value, {
      width: 260,
      margin: 1,
      color: {
        dark: '#1f2937',
        light: '#ffffff'
      }
    })
  } catch (error) {
    console.error('生成二维码失败:', error)
  }
}

const fetchCredential = async () => {
  loading.value = true
  try {
    const res = await axios.get(`${API_BASE}/remote-access/credential`)
    if (res.data?.code === 200 && res.data?.data) {
      credential.value = {
        ...credential.value,
        ...res.data.data
      }
      await drawQrCode()
      return
    }
    showAppMessage(res.data?.msg || '获取远程访问信息失败', 'error')
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '获取远程访问信息失败', 'error')
  } finally {
    loading.value = false
  }
}

const regenerateToken = async () => {
  regenerating.value = true
  try {
    const res = await axios.post(`${API_BASE}/remote-access/credential/regenerate`)
    if (res.data?.code === 200 && res.data?.data) {
      credential.value.remoteAccessToken = res.data.data
      await drawQrCode()
      showAppMessage('已重新生成远程访问密钥', 'success')
      return
    }
    showAppMessage(res.data?.msg || '重置密钥失败', 'error')
  } catch (error) {
    showAppMessage(error.response?.data?.msg || '重置密钥失败，请稍后重试', 'error')
  } finally {
    regenerating.value = false
  }
}

watch(qrPayload, () => {
  drawQrCode()
})

onMounted(() => {
  fetchCredential()
})
</script>

<template>
  <div class="remote-access-page">
    <v-card class="remote-card" :loading="loading">
      <v-card-title class="text-h6 d-flex align-center ga-2">
        <v-icon color="teal-darken-2">mdi-qrcode-scan</v-icon>
        远程访问
      </v-card-title>
      <v-card-text>
        <v-alert
          v-if="credential.remoteAccessEnabled === false"
          type="warning"
          variant="tonal"
          class="mb-4"
        >
          远程访问当前已关闭，API v1 不可用。
        </v-alert>

        <div class="remote-content">
          <div class="qr-box">
            <canvas ref="canvasRef" class="qr-canvas"></canvas>
            <div class="qr-caption">扫码导入远程访问信息</div>
          </div>

          <div class="info-box">
            <v-list density="compact" lines="two" class="info-list">
              <v-list-item title="主机地址">
                <template #subtitle>
                  {{ pageHost }}
                </template>
              </v-list-item>
              <v-list-item title="端口">
                <template #subtitle>
                  {{ pagePort }}
                </template>
              </v-list-item>
              <v-list-item title="当前用户">
                <template #subtitle>
                  {{ credential.currentUser || '-' }}
                </template>
              </v-list-item>
              <v-list-item title="授权状态">
                <template #subtitle>
                  {{ credential.tokenRequired ? '需要授权' : '无需授权' }}
                </template>
              </v-list-item>
              <v-list-item title="访问角色要求">
                <template #subtitle>
                  {{ credential.requiredRole || 'user' }}
                </template>
              </v-list-item>
            </v-list>

            <v-text-field
              v-if="credential.tokenRequired"
              v-model="credential.remoteAccessToken"
              label="当前用户远程密钥"
              variant="outlined"
              readonly
              class="mt-3"
            />

            <v-btn
              v-if="credential.tokenRequired"
              color="deep-orange-darken-1"
              variant="elevated"
              prepend-icon="mdi-refresh"
              :loading="regenerating"
              :disabled="regenerating"
              @click="regenerateToken"
            >
              重新生成密钥
            </v-btn>
          </div>
        </div>

        <v-expansion-panels class="mt-6">
          <v-expansion-panel>
            <v-expansion-panel-title>二维码原始数据</v-expansion-panel-title>
            <v-expansion-panel-text>
              <pre class="payload-preview">{{ qrPayload }}</pre>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.remote-access-page {
  width: 100%;
}

.remote-card {
  border-radius: 14px;
}

.remote-content {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
}

.qr-box {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qr-canvas {
  width: 260px;
  height: 260px;
}

.qr-caption {
  margin-top: 8px;
  color: #6b7280;
  font-size: 13px;
}

.info-box {
  min-width: 0;
}

.info-list {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.payload-preview {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .remote-content {
    grid-template-columns: 1fr;
  }

  .qr-box {
    width: fit-content;
    margin: 0 auto;
  }
}
</style>
