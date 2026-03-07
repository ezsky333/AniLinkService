<template>
  <v-app>
    <v-main class="bg-grey-lighten-5">
      <v-container v-if="checkingInstall" class="fill-height d-flex align-center justify-center">
        <v-card class="elevation-2" width="100%" max-width="450">
          <v-card-text class="text-center pa-12">
            <v-progress-circular indeterminate color="primary" size="48" />
            <p class="mt-4 text-body-1">正在初始化系统...</p>
          </v-card-text>
        </v-card>
      </v-container>
      <router-view v-else />
    </v-main>
  </v-app>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const checkingInstall = ref(true)

const checkInstallStatus = async () => {
  try {
    const res = await axios.get('/api/site/config')
    const isInstalled = res.data?.data?.installed === true

    if (isInstalled) {
      localStorage.setItem('installed', 'true')
      try {
        localStorage.setItem('siteConfig', JSON.stringify(res.data.data))
      } catch (e) {
        console.error('保存配置失败:', e)
      }
    } else {
      localStorage.removeItem('installed')
    }
  } catch (error) {
    console.error('检查安装状态失败:', error)
    localStorage.removeItem('installed')
  } finally {
    checkingInstall.value = false
  }
}

onMounted(async () => {
  // 检查本地缓存的状态
  const stored = localStorage.getItem('installed')
  if (stored !== null) {
    checkingInstall.value = false
    return
  }

  // 如果没有本地缓存，从服务器获取
  await checkInstallStatus()
})
</script>

<style scoped>
.fill-height {
  min-height: 100vh;
}
</style>
