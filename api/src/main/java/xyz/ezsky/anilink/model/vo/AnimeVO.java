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
    
    @Schema(description = "动漫备用标题")
    private String altTitle;
    
    @Schema(description = "上映年份")
    private String year;
    
    @Schema(description = "总集数")
    private Integer episodes;
    
    @Schema(description = "动漫类型（TV/电影/OVA等）")
    private String type;
    
    @Schema(description = "动漫时长或片长")
    private String duration;
    
    @Schema(description = "动漫简介")
    private String summary;
    
    @Schema(description = "标签列表（逗号分隔）")
    private String tags;
    
    @Schema(description = "评分")
    private Double rating;
    
    @Schema(description = "动漫封面图片URL")
    private String imageUrl;
    
    @Schema(description = "本地封面图片路径")
    private String localImagePath;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
