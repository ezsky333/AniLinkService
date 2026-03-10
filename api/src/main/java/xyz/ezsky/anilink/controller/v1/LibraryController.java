package xyz.ezsky.anilink.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.LibraryItemVO;
import xyz.ezsky.anilink.service.DanmakuService;
import xyz.ezsky.anilink.service.MediaFileService;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * API v1 - 媒体库接口
 */
@Tag(name = "API v1 - 媒体库", description = "提供媒体库查询和视频流接口")
@RestController
@RequestMapping("/api/v1")
@Log4j2
public class LibraryController {

    private static final Set<String> V1_SUBTITLE_EXTENSIONS = Set.of("ass", "ssa", "srt");

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private DanmakuService danmakuService;

    /**
     * 获取媒体库中的所有内容
     */
    @Operation(summary = "获取媒体库中的所有内容", description = "返回媒体库中所有视频文件的信息列表")
    @ApiResponse(responseCode = "200", description = "成功", 
        content = @Content(mediaType = "application/json", 
            schema = @Schema(implementation = LibraryItemVO.class)))
    @GetMapping("/library")
    public ResponseEntity<List<LibraryItemVO>> getLibrary() {
        try {
            List<LibraryItemVO> items = mediaFileService.getAllMediaLibraryItems();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            log.error("Failed to get library items", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取指定弹幕库的弹幕（XML 形式，带30分钟缓存）
     */
    @Operation(
        summary = "获取弹幕XML",
        description = "入参为视频ID，先查视频对应的 episodeId，再代理弹弹play /api/v2/comment/{episodeId} 接口并使用30分钟数据库缓存，返回Bilibili XML格式。"
    )
    @ApiResponse(responseCode = "200", description = "成功返回XML")
    @ApiResponse(responseCode = "404", description = "视频或弹幕数据不存在")
    @ApiResponse(responseCode = "400", description = "视频未匹配弹幕库ID")
    @GetMapping(value = "/comment/id/{id}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getCommentXml(
            @Parameter(description = "视频ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "是否包含第三方关联弹幕", required = false)
            @RequestParam(required = false, defaultValue = "true") Boolean withRelated) {
        MediaFile mediaFile = mediaFileService.getMediaFileById(id);
        if (mediaFile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Long episodeId;
        try {
            if (mediaFile.getEpisodeId() == null || mediaFile.getEpisodeId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            episodeId = Long.parseLong(mediaFile.getEpisodeId());
        } catch (NumberFormatException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String xml = danmakuService.getCommentXmlByEpisodeId(episodeId, withRelated);
        if (xml == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=UTF-8")
                .body(xml);
    }

    /**
     * 获取视频文件对应的字幕文件列表。
     */
    @Operation(
        summary = "获取视频文件对应的字幕文件列表",
        description = "返回视频同目录下文件名与视频同名，或文件名包含视频文件名的字幕文件（仅 ass/ssa/srt）。"
    )
    @ApiResponse(responseCode = "200", description = "成功返回字幕文件列表")
    @ApiResponse(responseCode = "404", description = "视频文件不存在")
    @GetMapping("/subtitle/info/{id}")
    public ResponseEntity<SubtitleInfoResponse> getSubtitleInfo(
            @Parameter(description = "视频文件ID", required = true)
            @PathVariable Long id) {
        MediaFile mediaFile = mediaFileService.getMediaFileById(id);
        if (mediaFile == null || mediaFile.getFilePath() == null || mediaFile.getFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path videoPath = Paths.get(mediaFile.getFilePath());
        if (!Files.exists(videoPath) || !Files.isRegularFile(videoPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path parent = videoPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return ResponseEntity.ok(new SubtitleInfoResponse(List.of()));
        }

        String videoBaseName = getFileNameWithoutExtension(videoPath.getFileName().toString()).toLowerCase(Locale.ROOT);
        List<SubtitleFileItem> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }

                String subtitleFileName = path.getFileName().toString();
                String extension = getFileExtension(subtitleFileName).toLowerCase(Locale.ROOT);
                if (!V1_SUBTITLE_EXTENSIONS.contains(extension)) {
                    continue;
                }

                String subtitleBaseName = getFileNameWithoutExtension(subtitleFileName).toLowerCase(Locale.ROOT);
                if (!(subtitleBaseName.equals(videoBaseName) || subtitleBaseName.contains(videoBaseName))) {
                    continue;
                }

                result.add(new SubtitleFileItem(subtitleFileName, Files.size(path)));
            }
        } catch (IOException e) {
            log.error("Failed to list subtitle files for mediaId={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok(new SubtitleInfoResponse(result));
    }

    /**
     * 获取字幕文件内容。
     */
    @Operation(
        summary = "获取字幕文件内容",
        description = "根据视频ID与字幕文件名返回字幕文件内容（text/plain）。"
    )
    @ApiResponse(responseCode = "200", description = "成功返回字幕内容")
    @ApiResponse(responseCode = "400", description = "请求参数非法")
    @ApiResponse(responseCode = "404", description = "视频或字幕文件不存在")
    @GetMapping(value = "/subtitle/file/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Resource> getSubtitleFile(
            @Parameter(description = "视频文件ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "字幕文件名，不包含路径信息", required = true)
            @RequestParam String fileName) {
        MediaFile mediaFile = mediaFileService.getMediaFileById(id);
        if (mediaFile == null || mediaFile.getFilePath() == null || mediaFile.getFilePath().isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (fileName == null || fileName.isBlank() || !isSafePlainFileName(fileName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String extension = getFileExtension(fileName).toLowerCase(Locale.ROOT);
        if (!V1_SUBTITLE_EXTENSIONS.contains(extension)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Path videoPath = Paths.get(mediaFile.getFilePath());
        if (!Files.exists(videoPath) || !Files.isRegularFile(videoPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path parent = videoPath.getParent();
        if (parent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Path subtitlePath = parent.resolve(fileName).normalize();
        Path normalizedParent = parent.normalize();
        if (!subtitlePath.startsWith(normalizedParent)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!Files.exists(subtitlePath) || !Files.isRegularFile(subtitlePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String videoBaseName = getFileNameWithoutExtension(videoPath.getFileName().toString()).toLowerCase(Locale.ROOT);
        String subtitleBaseName = getFileNameWithoutExtension(fileName).toLowerCase(Locale.ROOT);
        if (!(subtitleBaseName.equals(videoBaseName) || subtitleBaseName.contains(videoBaseName))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Resource resource = new FileSystemResource(subtitlePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .body(resource);
    }

    /**
     * 视频流接口，支持 Range 请求
     */
    @Operation(summary = "获取视频文件流", description = "支持 HTTP Range 请求实现视频跳转播放")
    @ApiResponse(responseCode = "200", description = "完整文件")
    @ApiResponse(responseCode = "206", description = "部分内容（Range 请求）")
    @ApiResponse(responseCode = "404", description = "文件不存在")
    @ApiResponse(responseCode = "416", description = "范围无效")
    @GetMapping("/stream/id/{id}")
    public ResponseEntity<Resource> streamVideo(
            @Parameter(description = "视频文件ID")
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        try {
            // 将字符串ID转为Long
            Long fileId = Long.parseLong(id);
            
            // 获取媒体文件信息
            MediaFile mediaFile = mediaFileService.getMediaFileById(fileId);
            if (mediaFile == null) {
                return ResponseEntity.notFound().build();
            }

            // 检查文件是否存在
            Path filePath = Paths.get(mediaFile.getFilePath());
            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

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
                    .body(new RangeFileResource(file, rangeStart, rangeEnd));

        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IOException e) {
            log.error("Failed to stream video: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 范围资源类，支持范围读取
     */
    private static class RangeFileResource extends FileSystemResource {
        private final long start;
        private final long end;

        public RangeFileResource(File file, long start, long end) {
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

    private boolean isSafePlainFileName(String fileName) {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return false;
        }
        Path p = Paths.get(fileName);
        return p.getNameCount() == 1;
    }

    private String getFileNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    public static class SubtitleInfoResponse {
        private final List<SubtitleFileItem> subtitles;

        public SubtitleInfoResponse(List<SubtitleFileItem> subtitles) {
            this.subtitles = subtitles;
        }

        public List<SubtitleFileItem> getSubtitles() {
            return subtitles;
        }
    }

    public static class SubtitleFileItem {
        private final String fileName;
        private final Long fileSize;

        public SubtitleFileItem(String fileName, Long fileSize) {
            this.fileName = fileName;
            this.fileSize = fileSize;
        }

        public String getFileName() {
            return fileName;
        }

        public Long getFileSize() {
            return fileSize;
        }
    }
}
