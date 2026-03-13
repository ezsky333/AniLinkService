<script setup>
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import ShinWeeklySchedule from '../components/anime/ShinWeeklySchedule.vue'
import MyFollowingList from '../components/anime/MyFollowingList.vue'

const router = useRouter()

const goToAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}

const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token')
})
</script>

<template>
  <div class="home-container unified-page-shell">
    <MyFollowingList :is-logged-in="isLoggedIn" @select-anime="goToAnime" />
    <ShinWeeklySchedule @select-anime="goToAnime" />
  </div>
</template>


<style scoped>
.home-container {
  padding: 0;
}

.anime-item {
  cursor: pointer;
}

.anime-card {
  transition: all 0.3s ease;
  height: 100%;
  overflow: hidden;
}

.anime-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.15) !important;
}

.anime-poster {
  transition: transform 0.3s ease;
}

.anime-card:hover .anime-poster {
  transform: scale(1.05);
}

.anime-title {
  font-weight: 500;
  color: #2e241e;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0 0 8px 0;
}

.anime-rating {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b5f55;
}

.anime-rating span {
  font-weight: 600;
  color: #c45d2b;
}
</style>
