package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 从弹弹play /api/v2/match/batch 接口返回的匹配结果
 * 完全匹配matchResult的结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    /**
     * 是否匹配成功（来自外层的success字段）
     */
    private Boolean success;

    /**
     * 文件哈希值（来自外层的fileHash字段）
     */
    private String fileHash;

    // 以下字段来自matchResult对象

    /**
     * 弹幕库编号（episodeId）- 用于MediaFile
     */
    private String episodeId;

    /**
     * 动漫编号 - 用于Anime表
     */
    private Long animeId;

    /**
     * 动漫主标题 - 用于Anime表的title
     */
    private String animeTitle;

    /**
     * 剧集子标题 - 用于MediaFile
     */
    private String episodeTitle;

    /**
     * 动漫类型（tvseries/movie/ova等）- 用于Anime表
     */
    private String type;

    /**
     * 动漫类型描述 - 用于Anime表
     */
    private String typeDescription;

    /**
     * 时间偏移（秒）
     */
    private Integer shift;

    /**
     * 动漫封面图片URL - 用于Anime表
     */
    private String imageUrl;

    /**
     * 错误消息（如果匹配失败）
     */
    private String errorMessage;
}
