package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "动漫信息视图对象")
public class AnimeVO {
    
    @Schema(description = "动漫数据库ID")
    private Long id;
    
    @Schema(description = "弹幕库动漫ID")
    private Long animeId;
    
    @Schema(description = "动漫标题")
    private String title;
    
    @Schema(description = "动漫类型（TV/电影/OVA等）")
    private String type;
    
    @Schema(description = "动漫封面图片URL")
    private String imageUrl;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
