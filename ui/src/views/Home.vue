<script setup>
import { useRouter } from 'vue-router'
import ShinWeeklySchedule from '../components/anime/ShinWeeklySchedule.vue'

const router = useRouter()

const goToAnime = (animeId) => {
  router.push(`/anime/${animeId}`)
}
</script>

<template>
  <div class="home-container">
    <ShinWeeklySchedule @select-anime="goToAnime" />

    <v-card class="elevation-2 mb-6">
      <v-card-title class="text-h5 bg-grey-lighten-4">
        <i class="fa-solid fa-star" style="margin-right: 12px; color: #ffc107;"></i>
        最近在播
      </v-card-title>
      <v-card-text class="pa-6">
        <v-row :gutter="2">
          <v-col v-for="i in 6" :key="i" cols="12" sm="6" md="4" lg="2" class="anime-item">
            <v-card class="anime-card" elevation="1" @click="goToAnime(i)">
              <v-img
                height="200"
                src="https://via.placeholder.com/150x200?text=Anime+1"
                alt="anime"
                class="anime-poster"
              />
              <v-card-text class="pa-3">
                <p class="anime-title text-caption">动画标题 {{ i }}</p>
                <div class="anime-rating">
                  <i class="fa-solid fa-star" style="color: #ffc107; font-size: 12px;"></i>
                  <span>9.5</span>
                </div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card class="elevation-2">
      <v-card-title class="text-h5 bg-grey-lighten-4">
        <i class="fa-solid fa-fire" style="margin-right: 12px; color: #ff6b6b;"></i>
        热门推荐
      </v-card-title>
      <v-card-text class="pa-6">
        <v-row :gutter="2">
          <v-col v-for="i in 6" :key="`hot-${i}`" cols="12" sm="6" md="4" lg="2" class="anime-item">
            <v-card class="anime-card" elevation="1" @click="goToAnime(i)">
              <v-img
                height="200"
                src="https://via.placeholder.com/150x200?text=Hot+Anime"
                alt="anime"
                class="anime-poster"
              />
              <v-card-text class="pa-3">
                <p class="anime-title text-caption">热门动画 {{ i }}</p>
                <div class="anime-rating">
                  <i class="fa-solid fa-star" style="color: #ffc107; font-size: 12px;"></i>
                  <span>8.8</span>
                </div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </div>
</template>


<style scoped>
.home-container {
  padding: 24px 0;
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
