package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 追番记录VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimeFollowVO {
    
    private Long id;
    
    private Long userId;
    
    private Long animeId;
    
    private String animeTitle;
    
    private String imageUrl;
    
    /**
     * 追番状态：watching(追番中)、completed(已完成)、dropped(已放弃)
     */
    private String status;
    
    /**
     * 用户的标签，用于分类管理
     */
    private String tags;
    
    private LocalDateTime followAt;
    
    private LocalDateTime updatedAt;
}
