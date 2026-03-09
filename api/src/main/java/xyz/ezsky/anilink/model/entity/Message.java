package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体类
 * 记录系统消息，如新剧集更新通知
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_msg_user", columnList = "user_id"),
    @Index(name = "idx_msg_created", columnList = "created_at"),
    @Index(name = "idx_msg_read", columnList = "is_read")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * 消息类型：episode_update(剧集更新)、system(系统消息)等
     */
    @Column(name = "type", nullable = false, length = 50)
    private String type;
    
    /**
     * 消息标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    
    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    /**
     * 关联的番剧ID
     */
    @Column(name = "anime_id")
    private Long animeId;
    
    /**
     * 关联的番剧标题
     */
    @Column(name = "anime_title", length = 500)
    private String animeTitle;
    
    /**
     * 关联的视频ID
     */
    @Column(name = "video_id")
    private Long videoId;
    
    /**
     * 关联的剧集ID（来自DanDan API）
     */
    @Column(name = "episode_id")
    private String episodeId;
    
    /**
     * 是否已读
     */
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
    
    /**
     * 消息读取时间
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
