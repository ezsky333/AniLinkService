package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RSS 订阅配置请求")
public class ResourceRssSubscriptionRequest {
    @Schema(description = "订阅源名称")
    private String name;

    @Schema(description = "RSS 地址")
    private String feedUrl;

    @Schema(description = "目标媒体库 ID")
    private Long libraryId;

    @Schema(description = "更新间隔（分钟）")
    private Integer intervalMinutes;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
