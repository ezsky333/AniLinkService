package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import xyz.ezsky.anilink.model.dto.MediaFileDTO;
import xyz.ezsky.anilink.model.dto.UpdateMediaFileRequest;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.QueueStatusVO;
import xyz.ezsky.anilink.service.MediaFileService;
import xyz.ezsky.anilink.service.MediaMetadataQueueManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    @Operation(summary = "获取视频流", description = "根据媒体文件ID获取视频流，支持HTTP Range请求实现跳转播放")
    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> streamVideo(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        
        // 获取媒体文件信息
        MediaFile mediaFile = mediaFileService.getMediaFileById(id);
        if (mediaFile == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查文件是否存在
        Path filePath = Paths.get(mediaFile.getFilePath());
        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        try {
            long fileSize = file.length();
            
            // 获取文件的 MIME 类型
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "video/mp4"; // 默认类型
            }

            // 如果没有 Range 请求头，返回完整文件
            if (rangeHeader == null || rangeHeader.trim().isEmpty()) {
                Resource resource = new FileSystemResource(file);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .contentLength(fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(resource);
            }

            // 解析 Range 请求头
            String[] ranges = rangeHeader.replace("bytes=", "").split("-");
            long rangeStart = Long.parseLong(ranges[0]);
            long rangeEnd = ranges.length > 1 && !ranges[1].isEmpty() 
                    ? Long.parseLong(ranges[1]) 
                    : fileSize - 1;

            // 验证范围
            if (rangeStart > rangeEnd || rangeEnd >= fileSize) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .build();
            }

            long contentLength = rangeEnd - rangeStart + 1;

            // 创建范围资源
            Resource resource = new FileSystemResource(file) {
                @Override
                public long contentLength() {
                    return contentLength;
                }
            };

            // 返回部分内容 (206)
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_RANGE, 
                            String.format("bytes %d-%d/%d", rangeStart, rangeEnd, fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                    .body(new RangeResource(file, rangeStart, rangeEnd));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * 自定义资源类，支持范围读取
     */
    private static class RangeResource extends FileSystemResource {
        private final long start;
        private final long end;

        public RangeResource(File file, long start, long end) {
            super(file);
            this.start = start;
            this.end = end;
        }

        @Override
        public long contentLength() {
            return end - start + 1;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            RandomAccessFile randomAccessFile = new RandomAccessFile(getFile(), "r");
            randomAccessFile.seek(start);
            
            return new java.io.InputStream() {
                private long position = start;
                
                @Override
                public int read() throws IOException {
                    if (position > end) {
                        return -1;
                    }
                    position++;
                    return randomAccessFile.read();
                }
                
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (position > end) {
                        return -1;
                    }
                    long maxLen = end - position + 1;
                    int actualLen = (int) Math.min(len, maxLen);
                    int bytesRead = randomAccessFile.read(b, off, actualLen);
                    if (bytesRead > 0) {
                        position += bytesRead;
                    }
                    return bytesRead;
                }
                
                @Override
                public void close() throws IOException {
                    randomAccessFile.close();
                }
            };
        }
    }
}
