package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * 媒体文件数据传输对象
 * 用于API响应和前端数据展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFileDTO {
    private Long id;
    private Long libraryId;
    private String fileName;
    private String filePath;
    private Long size;
    private Long lastModified;
    
    // 外部接口获取的字段
    private String episodeId;
    private Long animeId;
    private String animeTitle;
    private String episodeTitle;
    
    // FFprobe 提取的字段
    private Long duration;
    private String hash;
    private Integer width;
    private Integer height;
    private String aspectRatio;
    private String colorDepth;
    private String hdrType;
    private String colorSpace;
    private String colorPrimaries;
    private Long videoBitrate;
    private Double fps;
    private String containerFormat;
    private String videoCodec;
    private String audioCodec;
    private Boolean metadataFetched;
    
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
