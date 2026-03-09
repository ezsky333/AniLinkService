package xyz.ezsky.anilink.service.notification;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.service.AnimeFollowService;
import xyz.ezsky.anilink.service.MessageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧集更新通知服务（异步）
 * 负责在新剧集匹配成功后异步通知追番用户
 */
@Service
@Log4j2
public class EpisodeUpdateNotificationService {
    
    @Autowired
    private AnimeFollowService animeFollowService;
    
    @Autowired
    private MessageService messageService;
    
    /**
     * 异步通知追番用户有新剧集
     * 使用新事务确保不影响主流程
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyFollowingUsersAsync(Long animeId, String animeTitle, String episodeTitle, 
                                         String episodeId, Long videoId) {
        if (animeId == null) {
            return;
        }
        
        try {
            // 获取追了这个番剧的所有用户
            List<Long> followingUserIds = animeFollowService.getUserIdsByAnimeId(animeId);
            
            if (followingUserIds.isEmpty()) {
                log.debug("No users following anime {}, skip notification", animeId);
                return;
            }
            
            // 批量创建消息记录
            int successCount = 0;
            int failCount = 0;
            
            String title = "新剧集更新";
            String content = String.format("《%s》更新了新剧集：%s", 
                animeTitle != null ? animeTitle : "未知番剧",
                episodeTitle != null ? episodeTitle : "新剧集");
            
            // 批量更新追番记录时间戳（一次性更新）
            Map<Long, Boolean> updateResults = new HashMap<>();
            for (Long userId : followingUserIds) {
                try {
                    animeFollowService.updateFollowTimestampAsync(userId, animeId);
                    updateResults.put(userId, true);
                } catch (Exception e) {
                    log.error("Failed to update follow timestamp for user {}: {}", userId, e.getMessage());
                    updateResults.put(userId, false);
                }
            }
            
            // 批量创建消息
            for (Long userId : followingUserIds) {
                try {
                    messageService.createMessage(
                        userId, 
                        "episode_update", 
                        title, 
                        content,
                        animeId,
                        animeTitle,
                        videoId,
                        episodeId
                    );
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to create message for user {}: {}", userId, e.getMessage());
                    failCount++;
                }
            }
            
            log.info("Episode update notification completed: animeId={}, total={}, success={}, fail={}", 
                animeId, followingUserIds.size(), successCount, failCount);
            
        } catch (Exception e) {
            log.error("Failed to notify following users for anime {}: {}", animeId, e.getMessage(), e);
        }
    }
    
    /**
     * 异步通知（基于MediaFile对象）
     */
    @Async
    public void notifyFollowingUsersAsync(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getAnimeId() == null) {
            return;
        }
        
        notifyFollowingUsersAsync(
            mediaFile.getAnimeId(),
            mediaFile.getAnimeTitle(),
            mediaFile.getEpisodeTitle(),
            mediaFile.getEpisodeId(),
            mediaFile.getId()
        );
    }
}
