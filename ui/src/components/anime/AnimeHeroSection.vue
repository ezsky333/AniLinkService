<template>
  <div class="anime-hero">
    <div class="anime-poster">
      <img :src="animeData.imageUrl" :alt="animeData.animeTitle" loading="lazy" />
    </div>
    <div class="anime-info">
      <h1 class="anime-title-main">{{ titleInfo.main }}</h1>
      <div class="anime-title-sub">{{ titleInfo.sub }}</div>
      
      <div class="anime-rating-block">
        <div class="anime-rating-main">
          <span class="anime-score">{{ ratingMain }}</span>
          <span class="anime-rating-label">弹弹连载</span>
        </div>
        <div class="anime-rating-others">
          <span>🍙 Bangumi {{ ratingBangumi }}</span>
          <span>📀 AniDB {{ ratingAnidb }}</span>
        </div>
      </div>

      <div class="anime-status-badge">
        <span class="anime-badge"><i class="mdi mdi-bell"></i> 放送中 · {{ airDayText }}</span>
        <span class="anime-badge anime-badge-air">更新 {{ mainEpisodes.length }}/{{ totalEpisodes }}</span>
        <span class="anime-fav-btn" @click="toggleFavorite" :class="{ favorited: isFavorited }">
          <i :class="isFavorited ? 'mdi mdi-star' : 'mdi mdi-star-outline'"></i> 收藏
        </span>
      </div>

      <!-- 简介摘要 -->
      <div class="anime-summary-block">
        <div class="anime-summary" :class="{ expanded: isSummaryExpanded }">
          <p v-html="formattedSummary"></p>
        </div>
        <button class="anime-expand-btn" @click="toggleSummary">
          {{ isSummaryExpanded ? '收起' : '展开' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  animeData: {
    type: Object,
    required: true
  },
  titleInfo: {
    type: Object,
    required: true
  },
  airDayText: {
    type: String,
    required: true
  },
  ratingMain: {
    type: [String, Number],
    required: true
  },
  ratingBangumi: {
    type: [String, Number],
    required: true
  },
  ratingAnidb: {
    type: [String, Number],
    required: true
  },
  mainEpisodes: {
    type: Array,
    required: true
  },
  totalEpisodes: {
    type: [String, Number],
    required: true
  },
  formattedSummary: {
    type: String,
    required: true
  },
  isSummaryExpanded: {
    type: Boolean,
    required: true
  },
  isFavorited: {
    type: Boolean,
    required: true
  }
});

const emit = defineEmits(['update:isSummaryExpanded', 'toggleFavorite']);

const toggleSummary = () => {
  emit('update:isSummaryExpanded', !props.isSummaryExpanded);
};

const toggleFavorite = () => {
  emit('toggleFavorite');
};
</script>

<style scoped>
@import '../../styles/anime.css';
</style>
