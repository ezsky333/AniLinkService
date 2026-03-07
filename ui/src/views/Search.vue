<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

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

const buildCoverUrl = (localImagePath) => {
  if (!localImagePath) {
    return ''
  }
  return `/images/dandan/${encodeURI(localImagePath)}`
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
  <div class="search-page">
    <section class="search-header">
      <h1>动漫搜索</h1>
      <div class="search-controls">
        <input
          v-model="keyword"
          type="text"
          placeholder="输入标题关键词"
          @keyup.enter="search"
        />
        <button @click="search">搜索</button>
      </div>
      <p class="search-meta" v-if="!loading">
        共 {{ totalElements }} 条结果
      </p>
    </section>

    <section v-if="loading" class="state-block">正在加载...</section>
    <section v-else-if="errorMessage" class="state-block error">{{ errorMessage }}</section>
    <section v-else-if="!hasResult" class="state-block">没有找到匹配的动漫</section>

    <section v-else class="result-grid">
      <article
        v-for="anime in list"
        :key="anime.id || anime.animeId"
        class="anime-card"
        @click="toAnimeDetail(anime.animeId)"
      >
        <div class="cover-wrap">
          <img
            v-if="buildCoverUrl(anime.localImagePath)"
            :src="buildCoverUrl(anime.localImagePath)"
            :alt="anime.title"
            loading="lazy"
          />
          <div v-else class="cover-placeholder">无封面</div>
        </div>

        <div class="anime-info">
          <h3 :title="anime.title">{{ anime.title || '未命名动漫' }}</h3>
          <p class="sub-info">年份: {{ anime.year || '未知' }}</p>
          <p class="sub-info">集数: {{ anime.episodes || '-' }}</p>
        </div>
      </article>
    </section>

    <section v-if="!loading && totalPages > 1" class="pagination">
      <button :disabled="!canPrev" @click="prevPage">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页</span>
      <button :disabled="!canNext" @click="nextPage">下一页</button>
    </section>
  </div>
</template>

<style scoped>
.search-page {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.search-header {
  background: #fffaf6;
  border: 1px solid #eadfce;
  border-radius: 14px;
  padding: 18px;
}

.search-header h1 {
  margin: 0;
  color: #2e241e;
  font-size: 1.4rem;
}

.search-controls {
  margin-top: 12px;
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
  margin-top: 10px;
  color: #6b5f55;
  font-size: 0.92rem;
}

.state-block {
  background: #fff;
  border: 1px solid #eadfce;
  border-radius: 14px;
  padding: 30px 16px;
  text-align: center;
  color: #5b4f45;
}

.state-block.error {
  color: #b62020;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
}

.anime-card {
  border: 1px solid #eadfce;
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.anime-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}

.cover-wrap {
  width: 100%;
  aspect-ratio: 3 / 4;
  background: #f3ece4;
}

.cover-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  padding: 10px;
}

.anime-info h3 {
  margin: 0;
  font-size: 0.95rem;
  color: #2e241e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sub-info {
  margin: 6px 0 0;
  font-size: 0.84rem;
  color: #6b5f55;
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
}
</style>
