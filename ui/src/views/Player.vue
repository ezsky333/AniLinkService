<template>
  <div class="player-page">
    <div class="player-card">
      <div class="player-header">
        <h1>播放页</h1>
        <button class="back-btn" @click="goBack">返回详情</button>
      </div>

      <div class="player-meta">
        <p><strong>视频ID:</strong> {{ videoId }}</p>
        <p v-if="animeId"><strong>动画ID:</strong> {{ animeId }}</p>
        <p v-if="episodeId"><strong>剧集ID:</strong> {{ episodeId }}</p>
      </div>

      <div class="player-placeholder">
        <i class="mdi mdi-play-circle-outline"></i>
        <p>播放器容器已就绪，可根据视频ID加载实际播放源。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const videoId = computed(() => String(route.params.videoId || ''))
const animeId = computed(() => String(route.query.animeId || ''))
const episodeId = computed(() => String(route.query.episodeId || ''))

const goBack = () => {
  if (animeId.value) {
    router.push(`/anime/${animeId.value}`)
    return
  }
  router.back()
}
</script>

<style scoped>
.player-page {
  padding: 24px 0;
}

.player-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.12);
}

.player-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.player-header h1 {
  margin: 0;
  font-size: 1.6rem;
  color: #2e241e;
}

.back-btn {
  border: 1px solid #d7cbc1;
  background: #fff;
  border-radius: 10px;
  height: 38px;
  padding: 0 14px;
  color: #5f5148;
  cursor: pointer;
}

.back-btn:hover {
  background: #f9f4ef;
}

.player-meta {
  margin-top: 16px;
  color: #6b5f55;
}

.player-meta p {
  margin: 6px 0;
}

.player-placeholder {
  margin-top: 20px;
  border: 2px dashed #e3d7cc;
  border-radius: 14px;
  min-height: 280px;
  display: grid;
  place-content: center;
  text-align: center;
  color: #7d6f65;
  gap: 10px;
}

.player-placeholder i {
  font-size: 56px;
  color: #c45d2b;
}

@media (max-width: 799px) {
  .player-card {
    padding: 16px;
  }

  .player-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
