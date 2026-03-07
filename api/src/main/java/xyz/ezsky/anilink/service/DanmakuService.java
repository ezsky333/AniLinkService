package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.ApiCache;
import xyz.ezsky.anilink.repository.ApiCacheRepository;
import xyz.ezsky.anilink.util.DandanClientUtil;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 弹幕服务
 * 
 * <p>提供弹幕获取功能，带数据库缓存</p>
 */
@Service
@Log4j2
public class DanmakuService {

    private static final String DANDAN_BASE = "https://api.dandanplay.net";
    // 弹幕缓存时间：30分钟
    private static final long COMMENT_CACHE_TTL_MINUTES = 30;

    @Autowired
    private ApiCacheRepository apiCacheRepository;

    @Autowired
    private DandanClientUtil dandanClientUtil;

    /**
     * 获取指定弹幕库的弹幕（带缓存）
     *
     * @param episodeId 弹幕库ID
     * @param withRelated 是否包含第三方关联弹幕
     * @return 弹幕JSON字符串
     */
    public String getCommentByEpisodeId(Long episodeId, Boolean withRelated) {
        String cacheKey = buildCommentCacheKey(episodeId, withRelated);
        LocalDateTime now = LocalDateTime.now();

        // 检查有效缓存
        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        if (validCache.isPresent()) {
            log.debug("返回缓存的弹幕数据: episodeId={}", episodeId);
            return validCache.get().getCacheValue();
        }

        // 检查过期缓存（用于降级）
        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);

        // 构建请求路径
        String path = "/api/v2/comment/" + episodeId;
        if (Boolean.TRUE.equals(withRelated)) {
            path += "?withRelated=true";
        }

        try {
            ResponseEntity<String> response = dandanClientUtil.get(DANDAN_BASE, path);
            String responseBody = response.getBody();
            
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                // 保存到缓存
                upsertCache(cacheKey, responseBody, now.plusMinutes(COMMENT_CACHE_TTL_MINUTES));
                log.info("成功获取并缓存弹幕数据: episodeId={}, withRelated={}", episodeId, withRelated);
                return responseBody;
            }
            
            log.warn("弹幕请求返回非成功状态: episodeId={}, status={}", episodeId, response.getStatusCode());
        } catch (Exception ex) {
            log.error("弹幕请求失败: episodeId={}", episodeId, ex);
        }

        // 如果请求失败且存在过期缓存，返回过期缓存
        if (staleCache.isPresent()) {
            log.warn("由于上游失败，返回过期的弹幕缓存: episodeId={}", episodeId);
            return staleCache.get().getCacheValue();
        }

        return null;
    }

    /**
     * 构建弹幕缓存键
     */
    private String buildCommentCacheKey(Long episodeId, Boolean withRelated) {
        String key = "dandan:comment:" + episodeId;
        if (Boolean.TRUE.equals(withRelated)) {
            key += ":related";
        }
        return key;
    }

    /**
     * 更新或插入缓存
     */
    private void upsertCache(String cacheKey, String cacheValue, LocalDateTime expireTime) {
        LocalDateTime now = LocalDateTime.now();
        ApiCache cache = apiCacheRepository.findByCacheKey(cacheKey).orElseGet(ApiCache::new);
        
        if (cache.getId() == null) {
            cache.setCreatedAt(now);
        }
        
        cache.setCacheKey(cacheKey);
        cache.setCacheValue(cacheValue);
        cache.setExpireTime(expireTime);
        cache.setUpdatedAt(now);
        
        apiCacheRepository.save(cache);
    }
}
