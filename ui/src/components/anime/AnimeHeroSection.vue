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
          <span class="anime-rating-label">弹弹评分</span>
        </div>
        <div class="anime-rating-others">
          <span>🍙 Bangumi {{ ratingBangumi }}</span>
          <span>📀 AniDB {{ ratingAnidb }}</span>
        </div>
      </div>

      <div class="anime-status-badge">
        <span class="anime-badge"><i class="mdi mdi-bell"></i> {{ airingStatusText }}</span>
        <span class="anime-badge anime-badge-air">更新 {{ mainEpisodes.length }}/{{ totalEpisodes }}</span>
        <span 
          v-if="showFollowBtn" 
          class="anime-follow-btn" 
          @click="toggleFollow" 
          :class="{ following: isFollowing, loading: followLoading }"
          :title="isFollowing ? '取消追番' : '添加追番'"
        >
          <i :class="isFollowing ? 'mdi mdi-bookmark-check' : 'mdi mdi-bookmark-outline'"></i> 
          {{ isFollowing ? '已追番' : '追番' }}
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
  isOnAir: {
    type: Boolean,
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
  },
  isFollowing: {
    type: Boolean,
    default: false
  },
  followLoading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:isSummaryExpanded', 'toggleFavorite', 'toggleFollow']);

const showFollowBtn = computed(() => {
  return !!localStorage.getItem('token');
});

const airingStatusText = computed(() => {
  if (props.isOnAir) {
    return props.airDayText ? `放送中 · ${props.airDayText}` : '放送中';
  }
  return '未在更新';
});

const toggleSummary = () => {
  emit('update:isSummaryExpanded', !props.isSummaryExpanded);
};

const toggleFavorite = () => {
  emit('toggleFavorite');
};

const toggleFollow = () => {
  emit('toggleFollow');
};
</script>

<style scoped>
@import '../../styles/anime.css';
</style>
