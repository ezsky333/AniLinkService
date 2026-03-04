package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于表示从弹弹 API 返回的动漫信息（简化模型）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeInfo {
    private String episodeId;
    private Long animeId;
    private String animeTitle;
    private String episodeTitle;
}
