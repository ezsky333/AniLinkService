package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.ApiCache;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ApiCacheRepository extends JpaRepository<ApiCache, Long> {

    Optional<ApiCache> findByCacheKey(String cacheKey);

    Optional<ApiCache> findByCacheKeyAndExpireTimeAfter(String cacheKey, LocalDateTime time);
}
