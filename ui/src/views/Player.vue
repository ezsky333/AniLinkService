<template>
  <!-- 加载中 -->
  <div class="player-page" v-if="loading && !animeData" key="loading">
    <div class="loading-state">加载中...</div>
  </div>

  <!-- 发生错误 -->
  <div class="player-page" v-else-if="error" key="error">
    <div class="error-state">{{ error }}</div>
  </div>

  <!-- 正常显示 -->
  <div class="player-page" v-else key="loaded">
    <div class="player-layout">
      <!-- 左侧：播放器和主要内容 -->
      <div class="player-main">
        <!-- 播放器容器 -->
        <div class="player-card">
          <div ref="artRef" class="artplayer-container"></div>
        </div>

        <!-- 番剧信息部分 -->
        <div v-if="animeData" class="anime-info-section">
          <!-- 头部信息 -->
          <AnimeHeroSection 
            :anime-data="animeData"
            :title-info="titleInfo"
            :is-on-air="isOnAir"
            :air-day-text="airDayText"
            :rating-main="ratingMain"
            :rating-bangumi="ratingBangumi"
            :rating-anidb="ratingAnidb"
            :main-episodes="mainEpisodes"
            :total-episodes="totalEpisodes"
            :formatted-summary="formattedSummary"
            :is-summary-expanded="isSummaryExpanded"
            :is-favorited="isFavorited"
            @update:is-summary-expanded="isSummaryExpanded = $event"
            @toggleFavorite="toggleFavorite"
          />

          <!-- 分集列表 -->
          <EpisodeListSection
            :episodes="animeData.episodes"
            :main-count="mainEpisodes.length"
            :total-count="animeData.episodes.length"
            :playable-episode-keys="playableEpisodeKeys"
            :current-episode-id="episodeId"
            @playEpisode="playEpisode"
          />

          <!-- 预告片 -->
          <TrailerCarousel :trailers="animeData.trailers" />

          <!-- 外部链接 -->
          <FooterLinks
            :databases="animeData.onlineDatabases"
            :copyright-text="copyrightText"
          />
        </div>
      </div>

      <!-- 右侧边栏 -->
      <div v-if="animeData" class="player-sidebar">
        <!-- 制作信息 -->
        <MetadataCard
          :staff-list="staffList"
          :tags="animeData.tags"
        />

        <!-- 相关作品 -->
        <RelatedWorksCarousel :relateds="animeData.relateds" />
      </div>
    </div>

    <!-- 资源选择对话框 -->
    <div v-if="showResourceDialog" class="resource-dialog-mask" @click.self="closeResourceDialog">
      <div class="resource-dialog">
        <h3 class="resource-dialog-title">选择播放资源</h3>
        <p class="resource-dialog-subtitle">{{ selectedEpisodeTitle }}</p>
        <div class="resource-list">
          <button
            v-for="(resource, index) in selectedResources"
            :key="resource.id"
            class="resource-item"
            @click="selectResource(resource)"
          >
            <span class="resource-name">{{ resource.fileName || `资源 ${index + 1}` }}</span>
            <span class="resource-meta">ID: {{ resource.id }}</span>
          </button>
        </div>
        <button class="resource-cancel-btn" @click="closeResourceDialog">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import AnimeHeroSection from '../components/anime/AnimeHeroSection.vue'
import EpisodeListSection from '../components/anime/EpisodeListSection.vue'
import TrailerCarousel from '../components/anime/TrailerCarousel.vue'
import RelatedWorksCarousel from '../components/anime/RelatedWorksCarousel.vue'
import MetadataCard from '../components/anime/MetadataCard.vue'
import FooterLinks from '../components/anime/FooterLinks.vue'

const route = useRoute()
const router = useRouter()

const videoId = computed(() => String(route.params.videoId || ''))
const animeId = computed(() => String(route.query.animeId || ''))
const episodeId = computed(() => String(route.query.episodeId || ''))

// Anime Data State
const animeData = ref(null)
const existingEpisodes = ref([])
const loading = ref(false)
const error = ref(null)
const isSummaryExpanded = ref(false)
const isFavorited = ref(false)
const showResourceDialog = ref(false)
const selectedResources = ref([])
const selectedEpisodeTitle = ref('')

const artRef = ref(null)
const art = shallowRef(null)

const DANMAKU_SETTINGS_STORAGE_KEY = 'anilink:danmaku:settings:v1'

const DEFAULT_DANMAKU_SETTINGS = {
  speed: 7,
  opacity: 1,
  fontSize: 25,
  color: '#FFFFFF',
  mode: 0,
  margin: [10, '25%'],
  antiOverlap: true,
  synchronousPlayback: false,
  lockTime: 5,
  maxLength: 200,
  theme: 'dark',
  emitter: true,
  visible: true,
}

const normalizeDanmakuSettings = (raw) => {
  if (!raw || typeof raw !== 'object') {
    return {}
  }

  const margin = Array.isArray(raw.margin) && raw.margin.length === 2
    ? [raw.margin[0], raw.margin[1]]
    : undefined

  return {
    speed: typeof raw.speed === 'number' ? raw.speed : undefined,
    opacity: typeof raw.opacity === 'number' ? raw.opacity : undefined,
    fontSize: typeof raw.fontSize === 'number' || typeof raw.fontSize === 'string' ? raw.fontSize : undefined,
    color: typeof raw.color === 'string' ? raw.color : undefined,
    mode: typeof raw.mode === 'number' ? raw.mode : undefined,
    margin,
    antiOverlap: typeof raw.antiOverlap === 'boolean' ? raw.antiOverlap : undefined,
    synchronousPlayback: typeof raw.synchronousPlayback === 'boolean' ? raw.synchronousPlayback : undefined,
    lockTime: typeof raw.lockTime === 'number' ? raw.lockTime : undefined,
    maxLength: typeof raw.maxLength === 'number' ? raw.maxLength : undefined,
    theme: raw.theme === 'light' || raw.theme === 'dark' ? raw.theme : undefined,
    emitter: typeof raw.emitter === 'boolean' ? raw.emitter : undefined,
    visible: typeof raw.visible === 'boolean' ? raw.visible : undefined,
  }
}

const loadDanmakuSettings = () => {
  try {
    const cache = localStorage.getItem(DANMAKU_SETTINGS_STORAGE_KEY)
    if (!cache) {
      return {}
    }
    const parsed = JSON.parse(cache)
    return normalizeDanmakuSettings(parsed)
  } catch (e) {
    console.warn('读取弹幕配置缓存失败:', e)
    return {}
  }
}

const saveDanmakuSettings = (option) => {
  try {
    const normalized = normalizeDanmakuSettings(option)
    localStorage.setItem(DANMAKU_SETTINGS_STORAGE_KEY, JSON.stringify(normalized))
  } catch (e) {
    console.warn('保存弹幕配置缓存失败:', e)
  }
}

/**
 * 获取番剧数据
 */
const fetchAnimeData = async () => {
  try {
    if (!animeId.value) return

    loading.value = true
    error.value = null

    const response = await fetch(`/api/animes/${animeId.value}/raw-json`)
    const result = await response.json()
    if (result.code === 200 && result.data && result.data.bangumi) {
      animeData.value = result.data.bangumi
    }

    // 获取该番剧的所有剧集
    try {
      const episodesResponse = await fetch(`/api/animes/${animeId.value}/episodes?page=1&pageSize=9999`)
      const episodesResult = await episodesResponse.json()
      if (episodesResult.code === 200 && episodesResult.data && Array.isArray(episodesResult.data.content)) {
        existingEpisodes.value = episodesResult.data.content
      }
    } catch {
      existingEpisodes.value = []
    }
  } catch (err) {
    animeData.value = null
    error.value = err.message
  } finally {
    loading.value = false
  }
}

/**
 * Computed properties for anime display
 */
const formatDate = (iso) => iso ? iso.slice(0, 10) : ''
const todayStr = new Date().toISOString().slice(0, 10)
const isFuture = (ep) => new Date(ep.airDate) > new Date()

const getEpisodeType = (ep) => {
  const num = ep.episodeNumber
  if (/^\d+$/.test(num)) return 'main'
  if (num.startsWith('S')) return 'special'
  if (num.startsWith('C')) return 'credit'
  return 'other'
}

const mainEpisodes = computed(() => 
  animeData.value?.episodes.filter(ep => getEpisodeType(ep) === 'main') || []
)

const formatRating = (value, digits = 1) => {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(digits) : '--'
}

const isOnAir = computed(() => Boolean(animeData.value?.isOnAir))
const ratingMain = computed(() => formatRating(animeData.value?.rating, 1))
const ratingBangumi = computed(() => animeData.value?.ratingDetails?.['Bangumi评分'] ?? '7.8')
const ratingAnidb = computed(() => animeData.value?.ratingDetails?.['Anidb连载中评分'] ?? '8.51')

const totalEpisodes = computed(() => {
  const meta = animeData.value?.metadata || []
  const epItem = meta.find(m => m.startsWith('话数'))
  return epItem ? epItem.split(':')[1]?.trim() : '10'
})

const formattedSummary = computed(() => {
  return animeData.value?.summary?.replace(/\n/g, '<br>') || ''
})

const playableEpisodeKeys = computed(() => {
  const set = new Set()
  existingEpisodes.value.forEach((ep) => {
    if (ep.episodeId !== undefined && ep.episodeId !== null) {
      set.add(String(ep.episodeId))
    }
  })
  return set
})

const titleInfo = computed(() => {
  const titles = animeData.value?.titles || []
  if (titles.length === 0) return { main: '', sub: '' }
  return {
    main: titles[0]?.title || '',
    sub: titles[1]?.title || ''
  }
})

const airDayText = computed(() => {
  const day = animeData.value?.airDay
  const dayMap = { 0: '周日', 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
  return dayMap[day] || ''
})

const staffList = computed(() => {
  const meta = animeData.value?.metadata || []
  const keys = ['原作', '导演', '音乐', '动画制作']
  return meta
    .filter(item => keys.some(key => item.startsWith(key)))
    .map(item => {
      const parts = item.split(':')
      if (parts.length >= 2) {
        return `<strong>${parts[0]}:</strong>${parts.slice(1).join(':')}`
      }
      return item
    })
})

const copyrightText = computed(() => {
  const meta = animeData.value?.metadata || []
  return meta.find(m => m.startsWith('Copyright')) || ''
})

/**
 * 将弹弹play弹幕格式转换为Artplayer格式
 * @param {Array} chats - 弹弹play返回的弹幕数组
 * @returns {Array} 转换后的弹幕数组
 */
const convertDandanToArtplayer = (chats) => {
  if (!Array.isArray(chats)) {
    console.warn('弹幕数据不是数组:', typeof chats, chats)
    return []
  }

  if (chats.length === 0) {
    console.log('弹幕数组为空')
    return []
  }

  return chats
    .filter((chat) => {
      // 检查弹幕对象是否包含必要字段
      return chat && (chat.p || chat.mode || chat.mode !== undefined) && (chat.m || chat.text)
    })
    .map((chat) => {
      try {
        // 如果已经是转换后的格式，直接返回
        if (chat.text && (chat.mode !== undefined || chat.time !== undefined)) {
          return {
            text: chat.text,
            time: chat.time || 0,
            mode: chat.mode || 0,
            color: chat.color || '#FFFFFF',
            border: chat.border || false,
          }
        }

        // 解析 p 字段: "出现时间,模式,颜色,用户ID"
        if (!chat.p || !chat.m) {
          console.warn('弹幕缺少必要字段 p 或 m:', chat)
          return null
        }

        const pParts = chat.p.split(',')
        if (pParts.length < 3) {
          console.warn('弹幕 p 字段格式不正确:', chat.p)
          return null
        }

        const time = parseFloat(pParts[0]) || 0
        const dandanMode = parseInt(pParts[1]) || 1
        const colorInt = parseInt(pParts[2]) || 16777215 // 白色

        // 模式转换
        // 弹弹play: 1=普通, 4=底部, 5=顶部
        // Artplayer: 0=滚动, 1=顶部, 2=底部
        let artplayerMode = 0
        if (dandanMode === 5) {
          artplayerMode = 1 // 顶部
        } else if (dandanMode === 4) {
          artplayerMode = 2 // 底部
        } else {
          artplayerMode = 0 // 默认滚动
        }

        // 颜色转换: 十进制RGB到十六进制
        const color = '#' + colorInt.toString(16).padStart(6, '0').toUpperCase()

        return {
          text: chat.m,
          time,
          mode: artplayerMode,
          color,
          border: false,
        }
      } catch (error) {
        console.error('弹幕转换失败:', chat, error)
        return null
      }
    })
    .filter((item) => item !== null)
}

/**
 * 根据episodeId获取弹幕数据
 */
const fetchDanmaku = async (episodeId) => {
  try {
    if (!episodeId) {
      console.warn('episodeId为空，无法获取弹幕')
      return []
    }

    const response = await fetch(`/api/v2/comment/${episodeId}?withRelated=false`)
    if (!response.ok) {
      throw new Error(`弹幕接口返回状态: ${response.status}`)
    }

    const data = await response.json()
    console.log('弹幕API返回数据:', data)

    // 处理后端自定义格式返回
    if (data.code === 200 && data.data) {
      // 优先使用 comments 字段，其次使用 chats 字段
      const comments = data.data.comments || data.data.chats || []
      return convertDandanToArtplayer(comments)
    }

    // 处理标准ApiResponseVO格式
    if (data.success && data.data) {
      const comments = data.data.chats || data.data.comments || []
      return convertDandanToArtplayer(comments)
    }

    // 处理直接返回弹幕数组
    if (data.chats) {
      return convertDandanToArtplayer(data.chats)
    }

    if (data.comments) {
      return convertDandanToArtplayer(data.comments)
    }

    console.warn('弹幕数据格式未知', data)
    return []
  } catch (error) {
    console.error('获取弹幕失败:', error)
    return []
  }
}

const goBack = () => {
  if (animeId.value) {
    router.push(`/anime/${animeId.value}`)
    return
  }
  router.back()
}

/**
 * 根据episodeId获取可用资源
 */
const getEpisodeResources = (episodeId) => {
  if (episodeId === undefined || episodeId === null) {
    return []
  }
  const key = String(episodeId)
  return existingEpisodes.value.filter((item) => String(item.episodeId) === key && item.id !== undefined && item.id !== null)
}

/**
 * 选择资源并播放
 */
const goToPlayer = (resource) => {
  showResourceDialog.value = false
  selectedResources.value = []
  // 更新当前播放的视频
  router.push({
    name: 'Player',
    params: { videoId: String(resource.id) },
    query: {
      animeId: String(animeId.value),
      episodeId: String(resource.episodeId ?? '')
    }
  })
}

/**
 * 关闭资源选择对话框
 */
const closeResourceDialog = () => {
  showResourceDialog.value = false
  selectedResources.value = []
  selectedEpisodeTitle.value = ''
}

/**
 * 选择资源
 */
const selectResource = (resource) => {
  goToPlayer(resource)
}

/**
 * 播放剧集
 */
const playEpisode = (ep) => {
  if (isFuture(ep)) return
  const resources = getEpisodeResources(ep.episodeId)
  if (resources.length === 0) return

  if (resources.length === 1) {
    goToPlayer(resources[0])
    return
  }

  selectedResources.value = resources
  selectedEpisodeTitle.value = ep.episodeTitle || `第${ep.episodeNumber}话`
  showResourceDialog.value = true
}

/**
 * 切换收藏
 */
const toggleFavorite = () => {
  isFavorited.value = !isFavorited.value
}

onMounted(async () => {
  // 获取番剧数据
  await fetchAnimeData()

  // 获取弹幕数据
  const danmakuData = await fetchDanmaku(episodeId.value)
  const persistedDanmakuSettings = loadDanmakuSettings()
  const danmakuOptions = {
    ...DEFAULT_DANMAKU_SETTINGS,
    ...persistedDanmakuSettings,
    danmuku: danmakuData,
    useWorker: true,
    minWidth: 200,
    maxWidth: 500,
    filter: (danmu) => danmu.text && danmu.text.length < 200,
    beforeEmit: (danmu) => danmu.text && !!danmu.text.trim(),
  }

  // 初始化 Artplayer
  art.value = new Artplayer({
    container: artRef.value,
    url: videoId.value ? `/api/media-files/stream/${videoId.value}` : '',
    poster: '',
    volume: 0.5,
    isLive: false,
    muted: false,
    autoplay: false,
    pip: true,
    autoSize: false,
    autoMini: true,
    screenshot: true,
    setting: true,
    loop: false,
    flip: true,
    playbackRate: true,
    aspectRatio: true,
    fullscreen: true,
    fullscreenWeb: true,
    miniProgressBar: true,
    mutex: true,
    backdrop: true,
    playsInline: true,
    autoPlayback: false,
    airplay: true,
    theme: '#c45d2b',
    lang: 'zh-cn',
    moreVideoAttr: {
      crossOrigin: 'anonymous',
    },
    plugins: [
      artplayerPluginDanmuku(danmakuOptions),
    ],
  })

  // 监听播放器事件
  art.value.on('ready', () => {
    console.log('播放器已就绪')
  })

  art.value.on('play', () => {
    console.log('开始播放')
  })

  art.value.on('pause', () => {
    console.log('暂停播放')
  })

  art.value.on('error', (error) => {
    console.error('播放器错误:', error)
  })

  // 弹幕事件
  art.value.on('artplayerPluginDanmuku:visible', (danmu) => {
    
  })

  art.value.on('artplayerPluginDanmuku:loaded', (danmus) => {
    console.log('已加载弹幕数:', danmus.length)
  })

  art.value.on('artplayerPluginDanmuku:config', (option) => {
    saveDanmakuSettings(option)
  })

  art.value.on('artplayerPluginDanmuku:show', () => {
    const option = art.value?.plugins?.artplayerPluginDanmuku?.option
    if (option) {
      saveDanmakuSettings({ ...option, visible: true })
    }
  })

  art.value.on('artplayerPluginDanmuku:hide', () => {
    const option = art.value?.plugins?.artplayerPluginDanmuku?.option
    if (option) {
      saveDanmakuSettings({ ...option, visible: false })
    }
  })

  art.value.on('artplayerPluginDanmuku:error', (error) => {
    console.error('弹幕加载错误:', error)
  })
})

/**
 * 监听路由变化，当animeId改变时重新获取数据
 */
watch(() => route.params.videoId, async () => {
  closeResourceDialog()
  isSummaryExpanded.value = false
  // 刷新弹幕
  const danmakuData = await fetchDanmaku(episodeId.value)
  if (art.value && art.value.plugins.artplayerPluginDanmuku) {
    art.value.plugins.artplayerPluginDanmuku.setDanmuku(danmakuData)
  }
})

watch(() => animeId.value, async () => {
  closeResourceDialog()
  isSummaryExpanded.value = false
  await fetchAnimeData()
})

onBeforeUnmount(() => {
  if (art.value) {
    art.value.destroy(false)
  }
})
</script>

<style scoped>
/* Loading and Error States */
.loading-state,
.error-state {
  text-align: center;
  font-size: 1.1rem;
  color: #6b5f55;
  padding: 60px 20px;
}

.error-state {
  color: #d32f2f;
}

/* Main Layout */
.player-page {
  padding: 24px 0;
}

.player-layout {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  gap: 28px;
  background: white;
  border-radius: 32px;
  overflow: hidden;
  padding: 32px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
}

.player-main {
  flex: 1;
  min-width: 0;
}

.player-sidebar {
  width: 280px;
  flex-shrink: 0;
}

/* Player Card */
.player-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  margin-bottom: 28px;
}

.artplayer-container {
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 280px;
  height: auto;
  background: #000;
}

.artplayer-container :deep(.art-video-player) {
  width: 100% !important;
  height: 100% !important;
}

/* Info Section */
.anime-info-section {
  margin-top: 0;
}

/* Resource Dialog */
.resource-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
  padding: 20px;
}

.resource-dialog {
  width: min(560px, 100%);
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.25);
  padding: 20px;
}

.resource-dialog-title {
  margin: 0;
  font-size: 1.2rem;
  color: #2e241e;
}

.resource-dialog-subtitle {
  margin: 8px 0 14px;
  color: #6b5f55;
}

.resource-list {
  display: grid;
  gap: 10px;
  max-height: 50vh;
  overflow: auto;
}

.resource-item {
  border: 1px solid #e9e2dc;
  background: #fffaf6;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-item:hover {
  border-color: #c45d2b;
  background: #fff1e8;
}

.resource-name {
  color: #2e241e;
  font-weight: 600;
}

.resource-meta {
  color: #8b7e74;
  font-size: 0.85rem;
}

.resource-cancel-btn {
  margin-top: 14px;
  width: 100%;
  border: 1px solid #dfd2c8;
  background: #ffffff;
  color: #5f5148;
  border-radius: 10px;
  height: 40px;
  cursor: pointer;
  transition: 0.2s ease;
}

.resource-cancel-btn:hover {
  background: #f9f4ef;
}

/* Responsive Design */
@media (max-width: 1199px) {
  .player-layout {
    flex-direction: column;
    padding: 20px;
  }

  .player-sidebar {
    width: 100%;
  }
}

@media (max-width: 799px) {
  .player-layout {
    padding: 16px;
  }
}

@media (max-width: 479px) {
  .artplayer-container {
    min-height: 200px;
  }
}
</style>
