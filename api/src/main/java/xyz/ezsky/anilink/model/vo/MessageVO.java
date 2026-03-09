package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageVO {
    
    private Long id;
    
    private Long userId;
    
    /**
     * 消息类型：episode_update(剧集更新)、system(系统消息)等
     */
    private String type;
    
    /**
     * 消息标题
     */
    private String title;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 关联的番剧ID
     */
    private Long animeId;
    
    /**
     * 关联的番剧标题
     */
    private String animeTitle;
    
    /**
     * 关联的视频ID
     */
    private Long videoId;
    
    /**
     * 关联的剧集ID（来自DanDan API）
     */
    private String episodeId;
    
    /**
     * 是否已读
     */
    private Boolean isRead;
    
    /**
     * 消息读取时间
     */
    private LocalDateTime readAt;
    
    private LocalDateTime createdAt;
}
