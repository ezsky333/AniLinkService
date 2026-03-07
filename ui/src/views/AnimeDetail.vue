<template>
  <div class="page-wrapper" v-if="loading">
    <div class="loading">加载中...</div>
  </div>
  <div class="page-wrapper" v-else-if="error">
    <div class="error">数据加载失败: {{ error }}</div>
  </div>
  <div class="page" v-else>
    <div class="anime-detail-layout">
      <!-- 左侧：头部信息和分集 -->
      <div class="anime-left-column">
        <!-- 头部信息 -->
        <AnimeHeroSection 
          :anime-data="animeData"
          :title-info="titleInfo"
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

        <!-- 分集区 -->
        <EpisodeListSection
          :episodes="animeData.episodes"
          :main-count="mainEpisodes.length"
          :total-count="animeData.episodes.length"
          :playable-episode-keys="playableEpisodeKeys"
          @playEpisode="playEpisode"
        />

        <!-- 预告片 -->
        <TrailerCarousel :trailers="animeData.trailers" />

        <!-- 外部链接和版权 -->
        <FooterLinks
          :databases="animeData.onlineDatabases"
          :copyright-text="copyrightText"
        />
      </div>

      <!-- 右侧边栏 -->
      <div class="anime-sidebar">
        <!-- 制作信息 -->
        <MetadataCard
          :staff-list="staffList"
          :tags="animeData.tags"
        />

        <!-- 相关作品 -->
        <RelatedWorksCarousel :relateds="animeData.relateds" />
      </div>
    </div>

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
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AnimeHeroSection from '../components/anime/AnimeHeroSection.vue';
import EpisodeListSection from '../components/anime/EpisodeListSection.vue';
import TrailerCarousel from '../components/anime/TrailerCarousel.vue';
import RelatedWorksCarousel from '../components/anime/RelatedWorksCarousel.vue';
import MetadataCard from '../components/anime/MetadataCard.vue';
import FooterLinks from '../components/anime/FooterLinks.vue';

// Props
const props = defineProps({
  animeId: {
    type: [String, Number],
    required: true
  }
});

// State
const animeData = ref(null);
const existingEpisodes = ref([]);
const loading = ref(true);
const error = ref(null);
const isSummaryExpanded = ref(false);
const isFavorited = ref(false);
const route = useRoute();
const router = useRouter();
const showResourceDialog = ref(false);
const selectedResources = ref([]);
const selectedEpisodeTitle = ref('');

// Fetch Data
const fetchAnimeData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const response = await fetch(`/api/animes/${route.params.animeId}/raw-json`);
    const result = await response.json();
    if (result.code === 200 && result.data && result.data.bangumi) {
      animeData.value = result.data.bangumi;
    } else {
      throw new Error('Unexpected response structure');
    }

    try {
      const episodesResponse = await fetch(`/api/animes/${route.params.animeId}/episodes?page=1&pageSize=9999`);
      const episodesResult = await episodesResponse.json();
      if (episodesResult.code === 200 && episodesResult.data && Array.isArray(episodesResult.data.content)) {
        existingEpisodes.value = episodesResult.data.content;
      } else {
        existingEpisodes.value = [];
      }
    } catch {
      existingEpisodes.value = [];
    }
  } catch (err) {
    animeData.value = null;
    existingEpisodes.value = [];
    error.value = err.message;
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchAnimeData();
});

// Watch route params
watch(() => route.params.animeId, () => {
  closeResourceDialog();
  isSummaryExpanded.value = false;
  fetchAnimeData();
});

// Utility Functions
const formatDate = (iso) => iso ? iso.slice(0, 10) : '';
const todayStr = new Date().toISOString().slice(0, 10);
const isToday = (ep) => formatDate(ep.airDate) === todayStr;
const isFuture = (ep) => new Date(ep.airDate) > new Date();

// Episode type classification
const getEpisodeType = (ep) => {
  const num = ep.episodeNumber;
  if (/^\d+$/.test(num)) return 'main';
  if (num.startsWith('S')) return 'special';
  if (num.startsWith('C')) return 'credit';
  return 'other';
};

// Computed Properties
const mainEpisodes = computed(() => 
  animeData.value?.episodes.filter(ep => getEpisodeType(ep) === 'main') || []
);

const ratingMain = computed(() => animeData.value?.ratingDetails?.['弹弹play连载中评分']?.toFixed(2) ?? '9.55');
const ratingBangumi = computed(() => animeData.value?.ratingDetails?.['Bangumi评分'] ?? '7.8');
const ratingAnidb = computed(() => animeData.value?.ratingDetails?.['Anidb连载中评分'] ?? '8.51');

const totalEpisodes = computed(() => {
  const meta = animeData.value?.metadata || [];
  const epItem = meta.find(m => m.startsWith('话数'));
  return epItem ? epItem.split(':')[1]?.trim() : '10';
});

const formattedSummary = computed(() => {
  return animeData.value?.summary?.replace(/\n/g, '<br>') || '';
});

const playableEpisodeKeys = computed(() => {
  const set = new Set();
  existingEpisodes.value.forEach((ep) => {
    if (ep.episodeId !== undefined && ep.episodeId !== null) {
      set.add(String(ep.episodeId));
    }
  });
  return set;
});

const titleInfo = computed(() => {
  const titles = animeData.value?.titles || [];
  if (titles.length === 0) return { main: '', sub: '' };
  return {
    main: titles[0]?.title || '',
    sub: titles[1]?.title || ''
  };
});

const airDayText = computed(() => {
  const day = animeData.value?.airDay;
  const dayMap = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' };
  return dayMap[day] || '';
});

const staffList = computed(() => {
  const meta = animeData.value?.metadata || [];
  const keys = ['原作', '导演', '音乐', '动画制作'];
  return meta
    .filter(item => keys.some(key => item.startsWith(key)))
    .map(item => {
      const parts = item.split(':');
      if (parts.length >= 2) {
        return `<strong>${parts[0]}:</strong>${parts.slice(1).join(':')}`;
      }
      return item;
    });
});

const copyrightText = computed(() => {
  const meta = animeData.value?.metadata || [];
  return meta.find(m => m.startsWith('Copyright')) || '©山田鐘人・アベツカサ／小学館／「葬送のフリーレン」製作委員会';
});

// Event Handlers
const toggleFavorite = () => {
  isFavorited.value = !isFavorited.value;
};

const getEpisodeResources = (episodeId) => {
  if (episodeId === undefined || episodeId === null) {
    return [];
  }
  const key = String(episodeId);
  return existingEpisodes.value.filter((item) => String(item.episodeId) === key && item.id !== undefined && item.id !== null);
};

const goToPlayer = (resource) => {
  showResourceDialog.value = false;
  selectedResources.value = [];
  router.push({
    name: 'Player',
    params: { videoId: String(resource.id) },
    query: {
      animeId: String(route.params.animeId),
      episodeId: String(resource.episodeId ?? '')
    }
  });
};

const closeResourceDialog = () => {
  showResourceDialog.value = false;
  selectedResources.value = [];
  selectedEpisodeTitle.value = '';
};

const selectResource = (resource) => {
  goToPlayer(resource);
};

const playEpisode = (ep) => {
  if (isFuture(ep)) return;
  const resources = getEpisodeResources(ep.episodeId);
  if (resources.length === 0) return;

  if (resources.length === 1) {
    goToPlayer(resources[0]);
    return;
  }

  selectedResources.value = resources;
  selectedEpisodeTitle.value = ep.episodeTitle || `第${ep.episodeNumber}话`;
  showResourceDialog.value = true;
};
</script>

<style scoped>
/* Page Wrapper */
.page-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  background: white;
  border-radius: 32px;
  box-shadow: 0 20px 40px -12px rgba(0, 0, 0, 0.2);
  padding: 32px;
  min-height: 400px;
}

.loading,
.error {
  text-align: center;
  font-size: 1.2rem;
  color: #6b5f55;
  padding: 60px;
}

.page {
  background: white;
  border-radius: 32px;
  overflow: hidden;
  padding: 32px;
}

/* Detail Layout */
.anime-detail-layout {
  display: flex;
  gap: 28px;
}

.anime-left-column {
  flex: 1;
  min-width: 0;
}

.anime-sidebar {
  width: 280px;
  flex-shrink: 0;
}

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
}

.resource-cancel-btn:hover {
  background: #f9f4ef;
}

/* Responsive */
@media (max-width: 799px) {
  .page {
    padding: 20px;
  }

  .anime-detail-layout {
    display: flex;
    flex-direction: column;
    gap: 20px;
    align-items: center;
  }

  .anime-left-column {
    width: 100%;
  }

  .anime-sidebar {
    width: 100%;
  }

  .resource-dialog {
    padding: 16px;
  }
}
</style>