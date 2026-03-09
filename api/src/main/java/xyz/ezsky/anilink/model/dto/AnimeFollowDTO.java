package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 追番请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnimeFollowDTO {
    
    private Long animeId;
    
    private String animeTitle;
    
    private String imageUrl;
    
    /**
     * 追番状态
     */
    private String status;
    
    /**
     * 标签
     */
    private String tags;
}
