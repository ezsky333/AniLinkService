package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新站点配置请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新站点配置请求")
public class UpdateSiteConfigRequest {
    
    @Schema(description = "站点名称", example = "AniLink Service")
    private String siteName;
    
    @Schema(description = "站点描述", example = "番剧识别与弹幕播放服务")
    private String siteDescription;
    
    @Schema(description = "站点URL", example = "http://localhost:8081")
    private String siteUrl;

    @Schema(description = "Dandan 应用 ID", example = "your_app_id")
    private String dandanAppId;

    @Schema(description = "Dandan 应用密钥", example = "your_app_secret")
    private String dandanAppSecret;
}
