package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "剧集信息视图对象")
public class EpisodeVO {
    
    @Schema(description = "视频文件ID")
    private Long id;
    
    @Schema(description = "弹幕库动漫ID")
    private Long animeId;
    
    @Schema(description = "动漫标题")
    private String animeTitle;
    
    @Schema(description = "弹幕库剧集ID")
    private String episodeId;
    
    @Schema(description = "剧集标题")
    private String episodeTitle;
    
    @Schema(description = "文件名")
    private String fileName;
    
    @Schema(description = "文件路径")
    private String filePath;
    
    @Schema(description = "文件大小（字节）")
    private Long size;
    
    @Schema(description = "视频时长（毫秒）")
    private Long duration;
    
    @Schema(description = "视频宽度（像素）")
    private Integer width;
    
    @Schema(description = "视频高度（像素）")
    private Integer height;
    
    @Schema(description = "视频编码格式")
    private String videoCodec;
    
    @Schema(description = "音频编码格式")
    private String audioCodec;
    
    @Schema(description = "容器格式")
    private String containerFormat;
    
    @Schema(description = "帧率")
    private Double fps;
    
    @Schema(description = "文件MD5哈希值（前16MB）")
    private String hash;
    
    @Schema(description = "是否已获取完整元数据")
    private Boolean metadataFetched;
    
    @Schema(description = "创建时间")
    private Timestamp createdAt;
    
    @Schema(description = "更新时间")
    private Timestamp updatedAt;
}
