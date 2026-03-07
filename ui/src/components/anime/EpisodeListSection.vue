<template>
  <div class="anime-episode-section">
    <div class="anime-section-header">
      <div class="anime-tabs">
        <button 
          v-for="tab in tabs" 
          :key="tab.value"
          class="anime-tab-btn" 
          :class="{ active: activeTab === tab.value }" 
          @click="selectTab(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span class="anime-episode-count">共{{ totalCount }}集 (主线{{ mainCount }})</span>
    </div>
    <div class="anime-episode-list">
      <div 
        v-for="ep in displayedEpisodes" 
        :key="ep.episodeId" 
        class="anime-episode-card" 
        :class="{ today: isToday(ep) }"
      >
        <div class="anime-episode-left">
          <span class="anime-episode-num">{{ episodeNumberDisplay(ep) }}</span>
          <span class="anime-episode-title">{{ ep.episodeTitle }}</span>
          <span v-if="isToday(ep)" class="anime-today-tag">今日更新</span>
        </div>
        <div class="anime-episode-meta">
          <span>{{ formatDate(ep.airDate) }}</span>
          <a
            href="#"
            class="anime-watch-btn"
            :class="{ disabled: !canPlay(ep) }"
            :aria-disabled="!canPlay(ep)"
            :tabindex="canPlay(ep) ? 0 : -1"
            @click.prevent="playEpisode(ep)"
          >
            <i class="mdi mdi-play-circle"></i> 观看
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  episodes: {
    type: Array,
    required: true
  },
  mainCount: {
    type: Number,
    required: true
  },
  totalCount: {
    type: Number,
    required: true
  },
  playableEpisodeKeys: {
    type: Set,
    default: () => new Set()
  }
});

const emit = defineEmits(['playEpisode']);

const activeTab = ref('main');

const tabs = [
  { label: '正片', value: 'main' },
  { label: '特典/短片', value: 'special' },
  { label: '全部', value: 'all' }
];

const todayStr = new Date().toISOString().slice(0, 10);

const formatDate = (iso) => iso ? iso.slice(0, 10) : '';

const isToday = (ep) => formatDate(ep.airDate) === todayStr;

const isFuture = (ep) => new Date(ep.airDate) > new Date();

const isEpisodeExisting = (ep) => {
  if (!ep || ep.episodeId === undefined || ep.episodeId === null) {
    return false;
  }
  return props.playableEpisodeKeys.has(String(ep.episodeId));
};

const canPlay = (ep) => isEpisodeExisting(ep) && !isFuture(ep);

const getEpisodeType = (ep) => {
  const num = ep.episodeNumber;
  if (/^\d+$/.test(num)) return 'main';
  if (num.startsWith('S')) return 'special';
  if (num.startsWith('C')) return 'credit';
  return 'other';
};

const mainEpisodes = computed(() => 
  props.episodes?.filter(ep => getEpisodeType(ep) === 'main') || []
);

const specialEpisodes = computed(() => 
  props.episodes?.filter(ep => ['special', 'credit'].includes(getEpisodeType(ep))) || []
);

const allEpisodesSorted = computed(() => 
  [...(props.episodes || [])].sort((a, b) => new Date(a.airDate) - new Date(b.airDate))
);

const displayedEpisodes = computed(() => {
  if (activeTab.value === 'main') return mainEpisodes.value;
  if (activeTab.value === 'special') return specialEpisodes.value;
  return allEpisodesSorted.value;
});

const episodeNumberDisplay = (ep) => {
  const type = getEpisodeType(ep);
  if (type === 'main') return `第${ep.episodeNumber}话`;
  if (type === 'special') return '特典';
  if (type === 'credit') return '主题';
  return ep.episodeNumber;
};

const selectTab = (value) => {
  activeTab.value = value;
};

const playEpisode = (ep) => {
  if (!canPlay(ep)) return;
  emit('playEpisode', ep);
};
</script>

<style scoped>
@import '../../styles/anime.css';

.anime-watch-btn.disabled {
  background: #d4d4d8;
  color: #71717a;
  cursor: not-allowed;
  box-shadow: none;
}

.anime-watch-btn.disabled:hover {
  transform: none;
  filter: none;
}
</style>
