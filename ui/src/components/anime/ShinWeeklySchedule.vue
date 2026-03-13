<script setup>
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits(['select-anime'])

const loading = ref(false)
const error = ref('')
const bangumiList = ref([])
const getTodayDay = () => new Date().getDay()
const activeDay = ref(getTodayDay())

const weekTabs = [
  { label: '周日', value: 0 },
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 }
]

const normalizeAirDay = (day) => {
  if (day === 7) return 0
  return Number.isInteger(day) ? day : -1
}

const fetchShinBangumi = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/animes/shin/raw-json')
    const result = await response.json()
    if (result.code !== 200 || !result.data || !Array.isArray(result.data.bangumiList)) {
      throw new Error('新番接口返回结构不正确')
    }

    bangumiList.value = result.data.bangumiList
      .map((item) => ({
        ...item,
        airDay: normalizeAirDay(item.airDay)
      }))
      .filter((item) => item.airDay >= 0 && item.airDay <= 6)
  } catch (e) {
    error.value = e?.message || '新番数据加载失败'
    bangumiList.value = []
  } finally {
    loading.value = false
  }
}

const filteredBangumi = computed(() => {
  return bangumiList.value
    .filter((item) => item.airDay === activeDay.value)
    .sort((a, b) => (b.rating || 0) - (a.rating || 0))
})

const dayCount = (day) => {
  return bangumiList.value.filter((item) => item.airDay === day).length
}

const handleSelectAnime = (animeId) => {
  if (animeId !== null && animeId !== undefined) {
    emit('select-anime', animeId)
  }
}

onMounted(() => {
  // 每次进入页面都以当天周几作为默认标签
  activeDay.value = getTodayDay()
  fetchShinBangumi()
})
</script>

<template>
  <v-card class="elevation-2 mb-6">
    <v-card-title class="text-h5 bg-grey-lighten-4">
      <i class="fa-solid fa-calendar-week mr-3" style="color: #4b7bec;"></i>
      新番时间表
    </v-card-title>

    <v-card-text class="pa-6">
      <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
        {{ error }}
      </v-alert>

      <div class="weekday-tabs mb-4">
        <v-btn
          v-for="tab in weekTabs"
          :key="tab.value"
          :variant="activeDay === tab.value ? 'flat' : 'tonal'"
          :color="activeDay === tab.value ? 'primary' : 'grey'"
          size="small"
          rounded="pill"
          class="weekday-btn"
          @click="activeDay = tab.value"
        >
          {{ tab.label }}
          <span class="weekday-count">{{ dayCount(tab.value) }}</span>
        </v-btn>
      </div>

      <v-skeleton-loader
        v-if="loading"
        type="image, article"
        class="mb-3"
      />

      <div v-else-if="filteredBangumi.length > 0" class="schedule-grid">
        <v-card
          v-for="anime in filteredBangumi"
          :key="anime.animeId"
          class="schedule-card"
          elevation="1"
          @click="handleSelectAnime(anime.animeId)"
        >
          <div class="poster-wrap">
            <v-img
              :src="anime.imageUrl || 'https://assets.anixplayer.net/image/poster/default.jpg'"
              :aspect-ratio="2 / 3"
              class="poster-image"
            />
          </div>
          <v-card-text class="pa-3">
            <div class="title-row">
              <p class="anime-title">{{ anime.animeTitle }}</p>
              <v-chip
                v-if="anime.isOnAir"
                color="green"
                size="x-small"
                variant="tonal"
                class="on-air-chip"
              >连载中</v-chip>
            </div>
            <div class="meta-row">
              <span class="rating">
                <i class="fa-solid fa-star"></i>
                {{ Number(anime.rating || 0).toFixed(1) }}
              </span>
              <span v-if="anime.isRestricted" class="restricted">限制级</span>
            </div>
          </v-card-text>
        </v-card>
      </div>

      <v-alert v-else type="info" variant="tonal">该日暂无可展示的新番更新</v-alert>
    </v-card-text>
  </v-card>
</template>

<style scoped>
.weekday-tabs {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.weekday-btn {
  text-transform: none;
}

.weekday-count {
  margin-left: 6px;
  font-weight: 700;
  opacity: 0.85;
}

.schedule-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.schedule-card {
  height: 100%;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  display: flex;
  flex-direction: column;
}

.schedule-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 18px rgba(0, 0, 0, 0.16) !important;
}

.poster-wrap {
  background: linear-gradient(180deg, #f3f5f9 0%, #eceff4 100%);
}

.poster-image {
  width: 100%;
}

.poster-image :deep(.v-img__img) {
  object-fit: cover;
  object-position: center top;
}

.poster-image :deep(.v-responsive__content) {
  background: transparent;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.anime-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: #2e241e;
  margin: 0;
  line-height: 1.35;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.on-air-chip {
  flex-shrink: 0;
  white-space: nowrap;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6b5f55;
  font-size: 12px;
}

.rating {
  color: #c45d2b;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.restricted {
  color: #d32f2f;
  font-weight: 600;
}

@media (min-width: 2560px) {
  .schedule-grid {
    max-width: 2200px;
    margin-inline: auto;
  }
}

@media (max-width: 600px) {
  .weekday-tabs {
    gap: 6px;
  }

  .title-row {
    margin-bottom: 6px;
  }

  .anime-title {
    font-size: 13px;
    line-height: 1.3;
  }

  .meta-row {
    font-size: 11px;
  }

  .schedule-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }

  .weekday-btn {
    min-width: 64px;
    padding-inline: 10px;
  }
}

</style>
