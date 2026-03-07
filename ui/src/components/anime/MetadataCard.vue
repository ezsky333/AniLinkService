<template>
  <div class="anime-metadata-card">
    <h3 class="anime-metadata-title"><i class="mdi mdi-account-group"></i> 制作信息</h3>
    <div class="anime-staff-mini">
      <div v-for="item in staffList" :key="item" class="anime-staff-item" v-html="item"></div>
    </div>
    <div class="anime-tag-list">
      <span v-for="tag in tags" :key="tag.id" class="anime-tag">#{{ tag.name }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  staffList: {
    type: Array,
    required: true
  },
  tags: {
    type: Array,
    required: true
  }
});
</script>

<style scoped>
@import '../../styles/anime.css';
</style>
