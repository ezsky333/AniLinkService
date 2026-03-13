<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { formatAnimeType } from '../utils/animeType'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const errorMessage = ref('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(12)
const totalElements = ref(0)
const totalPages = ref(0)
const list = ref([])

const hasResult = computed(() => list.value.length > 0)
const canPrev = computed(() => page.value > 1)
const canNext = computed(() => page.value < totalPages.value)

const buildAnimeMeta = (anime) => {
  const parts = []
  if (anime?.type) parts.push(formatAnimeType(anime.type))
  if (anime?.animeId) parts.push(`ID: ${anime.animeId}`)
  return parts.length ? parts.join(' | ') : '暂无信息'
}



const toAnimeDetail = (animeId) => {
  router.push(`/anime/${animeId}`)
}

const normalizeResponse = (res) => {
  const data = res?.data?.data
  list.value = Array.isArray(data?.content) ? data.content : []
  totalElements.value = Number(data?.totalElements || 0)
  totalPages.value = Number(data?.totalPages || 0)
  page.value = Number(data?.currentPage || page.value)
  pageSize.value = Number(data?.pageSize || pageSize.value)
}

const fetchAnimes = async () => {
  loading.value = true
  errorMessage.value = ''

  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value
    }

    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }

    const res = await axios.get('/api/animes', { params })
    if (res.data?.code !== 200) {
      throw new Error(res.data?.msg || '请求失败')
    }

    normalizeResponse(res)
  } catch (error) {
    errorMessage.value = error?.response?.data?.msg || error?.message || '加载失败'
    list.value = []
    totalElements.value = 0
    totalPages.value = 0
  } finally {
    loading.value = false
  }
}

const syncFromRouteAndFetch = async () => {
  keyword.value = String(route.query.q || '')

  const parsedPage = Number(route.query.page || 1)
  page.value = Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 1

  await fetchAnimes()
}

const updateRouteQuery = () => {
  const query = {
    page: String(page.value)
  }

  if (keyword.value.trim()) {
    query.q = keyword.value.trim()
  }

  router.push({ path: '/search', query })
}

const search = () => {
  page.value = 1
  updateRouteQuery()
}

const prevPage = () => {
  if (!canPrev.value) return
  page.value -= 1
  updateRouteQuery()
}

const nextPage = () => {
  if (!canNext.value) return
  page.value += 1
  updateRouteQuery()
}

watch(
  () => [route.query.q, route.query.page],
  () => {
    syncFromRouteAndFetch()
  }
)

onMounted(() => {
  syncFromRouteAndFetch()
})
</script>

<template>
  <div class="search-page unified-page-shell">
    <v-card class="elevation-2 unified-panel search-panel">
      <v-card-title class="text-h5 d-flex align-center justify-space-between unified-panel-title">
        <div class="d-flex align-center">
          <i class="mdi mdi-magnify mr-3" style="color: #4b7bec;"></i>
          发现
        </div>
        <span class="search-meta" v-if="!loading">共 {{ totalElements }} 条结果</span>
      </v-card-title>

      <v-card-text class="pa-6">
        <div class="search-controls mb-4">
          <input
            v-model="keyword"
            type="text"
            placeholder="输入标题关键词"
            @keyup.enter="search"
          />
          <button @click="search">搜索</button>
        </div>

        <v-alert v-if="loading" type="info" variant="tonal" class="state-block">正在加载...</v-alert>
        <v-alert v-else-if="errorMessage" type="error" variant="tonal" class="state-block">{{ errorMessage }}</v-alert>
        <v-alert v-else-if="!hasResult" type="info" variant="tonal" class="state-block">没有找到匹配的动漫</v-alert>

        <section v-else class="result-grid">
          <v-card
            v-for="anime in list"
            :key="anime.id || anime.animeId"
            class="anime-card schedule-card"
            elevation="1"
            @click="toAnimeDetail(anime.animeId)"
          >
            <div class="poster-wrap">
              <img
                v-if="anime.imageUrl"
                :src="anime.imageUrl"
                :alt="anime.title"
                loading="lazy"
              />
              <div v-else class="cover-placeholder">无封面</div>
            </div>

            <div class="anime-info">
              <h3 class="anime-title" :title="anime.title">{{ anime.title || '未命名动漫' }}</h3>
              <p class="sub-info">{{ buildAnimeMeta(anime) }}</p>
            </div>
          </v-card>
        </section>

        <section v-if="!loading && totalPages > 1" class="pagination mt-4">
          <button :disabled="!canPrev" @click="prevPage">上一页</button>
          <span>第 {{ page }} / {{ totalPages }} 页</span>
          <button :disabled="!canNext" @click="nextPage">下一页</button>
        </section>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.search-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.search-panel {
  border-radius: 16px;
}

.search-controls {
  display: flex;
  gap: 10px;
}

.search-controls input {
  flex: 1;
  min-width: 0;
  border: 1px solid #d9cab6;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.95rem;
  outline: none;
}

.search-controls input:focus {
  border-color: #b99a7e;
  box-shadow: 0 0 0 2px rgba(185, 154, 126, 0.2);
}

.search-controls button {
  border: 0;
  background: #c45d2b;
  color: #fff;
  border-radius: 10px;
  padding: 10px 18px;
  cursor: pointer;
  font-weight: 600;
}

.search-controls button:hover {
  background: #ac4d20;
}

.search-meta {
  color: #6b5f55;
  font-size: 0.92rem;
}

.state-block {
  margin-bottom: 12px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.anime-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.schedule-card {
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.schedule-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.16) !important;
}

.poster-wrap {
  width: 100%;
  aspect-ratio: 3 / 4;
  background: linear-gradient(180deg, #f3f5f9 0%, #eceff4 100%);
  overflow: hidden;
}

.poster-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center top;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a7c6e;
  font-size: 0.9rem;
}

.anime-info {
  padding: 12px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

.anime-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2e241e;
  line-height: 1.35;
  min-height: calc(2 * 1.35em);
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sub-info {
  margin: auto 0 0;
  font-size: 12px;
  color: #6b5f55;
  line-height: 1.5;
  word-break: break-word;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.pagination button {
  border: 1px solid #d9cab6;
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 680px) {
  .search-controls {
    flex-direction: column;
  }

  .search-controls button {
    width: 100%;
  }

  .result-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }

  .anime-title {
    font-size: 13px;
    line-height: 1.3;
    min-height: calc(2 * 1.3em);
  }
}
</style>
