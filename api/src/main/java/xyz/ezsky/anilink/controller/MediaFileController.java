package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import xyz.ezsky.anilink.model.dto.MediaFileDTO;
import xyz.ezsky.anilink.model.dto.UpdateMediaFileRequest;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.QueueStatusVO;
import xyz.ezsky.anilink.service.MediaFileService;
import xyz.ezsky.anilink.service.MediaMetadataQueueManager;

/**
 * 媒体文件管理API
 */
@Tag(name = "媒体文件管理", description = "用于查询、更新和删除媒体文件记录")
@RestController
@RequestMapping("/api/media-files")
public class MediaFileController {

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaMetadataQueueManager metadataQueueManager;

    @Operation(summary = "分页查询媒体文件列表", description = "查询媒体文件列表，支持按媒体库过滤")
    @SaCheckRole("super-admin")
    @GetMapping
    public ApiResponseVO<PageVO<MediaFileDTO>> getMediaFiles(
            @Parameter(description = "媒体库ID，不提供则查询所有", required = false)
            @RequestParam(required = false) Long libraryId,
            @Parameter(description = "页码，从0开始", required = false)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponseVO.success(mediaFileService.getMediaFiles(libraryId, page, pageSize));
    }

    @Operation(summary = "获取媒体文件详情", description = "根据ID获取单个媒体文件的完整信息，包括所有技术元数据")
    @SaCheckRole("super-admin")
    @GetMapping("/{id}")
    public ApiResponseVO<MediaFileDTO> getMediaFileDetail(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id) {
        MediaFileDTO dto = mediaFileService.getMediaFileDetail(id);
        if (dto == null) {
            return ApiResponseVO.fail("媒体文件不存在");
        }
        return ApiResponseVO.success(dto);
    }

    @Operation(summary = "批量重新获取元数据", description = "对指定媒体库中的所有文件重新触发元数据提取（异步处理）")
    @SaCheckRole("super-admin")
    @PostMapping("/reprocess-metadata/{libraryId}")
    public ApiResponseVO<Void> reprocessMetadata(
            @Parameter(description = "媒体库ID")
            @PathVariable Long libraryId) {
        try {
            mediaFileService.reprocessMetadataForLibrary(libraryId);
            return ApiResponseVO.success(null, "已提交元数据重新获取任务，后台处理中");
        } catch (Exception e) {
            return ApiResponseVO.fail("提交任务失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除媒体文件", description = "删除指定的媒体文件记录，可选是否同时删除硬盘上的文件")
    @SaCheckRole("super-admin")
    @DeleteMapping("/{id}")
    public ApiResponseVO<Void> deleteMediaFile(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id,
            @Parameter(description = "是否同时删除硬盘上的文件", required = false)
            @RequestParam(defaultValue = "false") boolean deleteFile) {
        try {
            mediaFileService.deleteMediaFile(id, deleteFile);
            return ApiResponseVO.success(null, "删除成功");
        } catch (Exception e) {
            return ApiResponseVO.fail("删除失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新文件信息", description = "更新媒体文件的动漫相关信息（episodeId、animeId、标题等）")
    @SaCheckRole("super-admin")
    @PutMapping("/{id}")
    public ApiResponseVO<Void> updateMediaFile(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id,
            @RequestBody UpdateMediaFileRequest request) {
        try {
            mediaFileService.updateMediaFile(id, request);
            return ApiResponseVO.success(null, "更新成功");
        } catch (Exception e) {
            return ApiResponseVO.fail("更新失败: " + e.getMessage());
        }
    }

    @Operation(summary = "查询队列状态", description = "查询当前元数据提取队列的状态，包括待处理任务数和活跃线程数")
    @SaCheckRole("super-admin")
    @GetMapping("/queue/status")
    public ApiResponseVO<QueueStatusVO> getQueueStatus() {
        QueueStatusVO status = QueueStatusVO.builder()
                .pendingTasks(metadataQueueManager.getQueueSize())
                .activeThreads(metadataQueueManager.getActiveThreadCount())
                .maxPoolSize(metadataQueueManager.getMaxPoolSize())
                .build();
        return ApiResponseVO.success(status);
    }
}
