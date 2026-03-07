export const ANIME_TYPE_LABELS = {
  tvseries: 'TV动画',
  tvspecial: 'TV特别篇',
  ova: 'OVA/OAD',
  movie: '剧场版',
  musicvideo: '音乐MV',
  web: '网络动画',
  other: '其他',
  jpmovie: '日本电影',
  jpdrama: '日剧',
  unknown: '未知类型',
  tmdbtv: 'TMDB电视剧',
  tmdbmovie: 'TMDB电影'
}

export const formatAnimeType = (type) => {
  if (!type) return '未知类型'
  const key = String(type).trim().toLowerCase()
  return ANIME_TYPE_LABELS[key] || '其他'
}
