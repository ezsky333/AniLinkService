package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新媒体文件信息的请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMediaFileRequest {
    private String episodeId;
    private Long animeId;
    private String animeTitle;
    private String episodeTitle;
}
