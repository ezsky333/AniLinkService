package xyz.ezsky.anilink.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API v1 媒体库项目视图对象
 * 用于返回媒体库中的所有内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "媒体库中的视频信息项")
public class LibraryItemVO {
    
    @JsonProperty("AnimeId")
    @Schema(description = "动画编号", example = "14198")
    private Long animeId;
    
    @JsonProperty("EpisodeId")
    @Schema(description = "弹幕库编号", example = "141980003")
    private Long episodeId;
    
    @JsonProperty("AnimeTitle")
    @Schema(description = "主标题", example = "佐贺偶像是传奇")
    private String animeTitle;
    
    @JsonProperty("EpisodeTitle")
    @Schema(description = "子标题", example = "第3话 DEAD OR LIVE SAGA")
    private String episodeTitle;
    
    @JsonProperty("Id")
    @Schema(description = "此视频文件的唯一编号，GUID格式", example = "c004e475-d9bb-41e1-976b-7fce00997f3a")
    private String id;
    
    @JsonProperty("Hash")
    @Schema(description = "此视频的特征码（重要）", example = "03778309A0E8A09C2F43603A490F2E98")
    private String hash;
    
    @JsonProperty("Name")
    @Schema(description = "此视频的文件名（去除路径信息）", example = "[Zombieland Saga][03][BIG5][1080P].mp4")
    private String name;
    
    @JsonProperty("Path")
    @Schema(description = "此视频在硬盘上的完整路径", example = "Y:\\[Zombieland Saga][03][BIG5][1080P].mp4")
    private String path;
    
    @JsonProperty("Size")
    @Schema(description = "文件体积，单位为Byte", example = "518754774")
    private Long size;
    
    @JsonProperty("Rate")
    @Schema(description = "用户对此视频内容的打分，目前全部为0", example = "0")
    private Integer rate;
    
    @JsonProperty("IsStandalone")
    @Schema(description = "是否为独立文件，即不包含在媒体库监视文件夹内的文件", example = "false")
    private Boolean isStandalone;
    
    @JsonProperty("Created")
    @Schema(description = "弹弹play媒体库收录此视频的时间", example = "2018-10-21T23:14:59")
    private LocalDateTime created;
    
    @JsonProperty("LastMatch")
    @Schema(description = "上次尝试匹配的时间", example = "0001-01-01T00:00:00")
    private LocalDateTime lastMatch;
    
    @JsonProperty("LastPlay")
    @Schema(description = "上次使用播放此视频的时间", example = "2019-04-25T18:00:22")
    private LocalDateTime lastPlay;
    
    @JsonProperty("LastThumbnail")
    @Schema(description = "上次生成缩略图的时间", example = "2019-03-05T22:03:20")
    private LocalDateTime lastThumbnail;
    
    @JsonProperty("Duration")
    @Schema(description = "视频时长，单位为秒", example = "1420")
    private Long duration;
}
