package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.PlayHistory;

import java.util.Optional;

@Repository
public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {
    
    /**
     * 根据用户ID和番剧ID查询播放历史
     */
    Optional<PlayHistory> findByUserIdAndAnimeId(Long userId, Long animeId);
    
    /**
     * 根据用户ID查询播放历史（分页，按最后播放时间倒序）
     */
    Page<PlayHistory> findByUserIdOrderByLastPlayTimeDesc(Long userId, Pageable pageable);
    
    /**
     * 删除用户的所有播放历史
     */
    void deleteByUserId(Long userId);
}
