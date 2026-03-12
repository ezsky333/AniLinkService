package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资源下载请求")
public class ResourceSearchDownloadRequest {
    @Schema(description = "资源标题")
    private String title;

    @Schema(description = "磁力链接")
    private String magnet;

    @Schema(description = "资源页面链接")
    private String pageUrl;

    @Schema(description = "文件大小")
    private String fileSize;

    @Schema(description = "发布时间")
    private String publishDate;

    @Schema(description = "字幕组名称")
    private String subgroupName;

    @Schema(description = "资源类型名称")
    private String typeName;

    @Schema(description = "媒体库 ID")
    private Long libraryId;
}
