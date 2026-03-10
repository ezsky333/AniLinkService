package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.dto.MediaSubtitleAdminDTO;
import xyz.ezsky.anilink.model.dto.MediaSubtitleDTO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaSubtitleRepository;
import xyz.ezsky.anilink.service.MediaSubtitleService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 字幕文件管理API
 */
@Tag(name = "字幕文件管理", description = "用于下载和管理字幕文件")
@RestController
@RequestMapping("/api/subtitles")
public class MediaSubtitleController {

    @Autowired
    private MediaSubtitleRepository mediaSubtitleRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaSubtitleService mediaSubtitleService;

    @Value("${media.subtitle.output-dir:./data/subtitles}")
    private String subtitleOutputDir;

    @Operation(summary = "分页查询字幕列表", description = "用于后台全局管理字幕，并展示关联视频与动漫信息")
    @SaCheckRole("super-admin")
    @GetMapping
    public ApiResponseVO<PageVO<MediaSubtitleAdminDTO>> listSubtitles(
            @Parameter(description = "搜索关键词，可匹配字幕名、视频名、动漫名", required = false)
            @RequestParam(required = false) String keyword,
            @Parameter(description = "字幕来源类型：EMBEDDED/EXTERNAL/UPLOADED", required = false)
            @RequestParam(required = false) String sourceType,
            @Parameter(description = "页码，从0开始", required = false)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponseVO.success(mediaSubtitleService.getSubtitlePage(page, pageSize, keyword, sourceType));
    }

    @Operation(summary = "下载字幕文件", description = "根据字幕ID下载对应的字幕文件内容")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSubtitle(
            @Parameter(description = "字幕ID")
            @PathVariable Long id) {
        try {
            Optional<MediaSubtitle> subtitleOpt = mediaSubtitleRepository.findById(id);
            if (!subtitleOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            MediaSubtitle subtitle = subtitleOpt.get();
            Path filePath = Paths.get(subtitle.getFilePath());

            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            FileSystemResource resource = new FileSystemResource(filePath);

            // 确定媒体类型
            String contentType = getContentType(subtitle.getSubtitleFormat());

            // 构建文件名，处理中文字符
            String fileName = subtitle.getTrackName() != null
                    ? subtitle.getTrackName() + "." + subtitle.getSubtitleFormat()
                    : "subtitle." + subtitle.getSubtitleFormat();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType + "; charset=utf-8")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodeFileName(fileName))
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据字幕格式获取媒体类型
     */
    private String getContentType(String subtitleFormat) {
        if (subtitleFormat == null) {
            return "text/plain";
        }

        return switch (subtitleFormat.toLowerCase()) {
            case "ass", "ssa" -> "text/x-ass";
            case "srt" -> "text/plain";
            case "vtt" -> "text/vtt";
            case "sub" -> "text/plain";
            case "sbv" -> "text/plain";
            default -> "text/plain";
        };
    }

    /**
     * RFC 5987 编码文件名，用于Content-Disposition
     */
    private String encodeFileName(String fileName) {
        try {
            byte[] bytes = fileName.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') ||
                        b == '!' || b == '#' || b == '$' || b == '&' || b == '+' || b == '-' ||
                        b == '^' || b == '_' || b == '`' || b == '|' || b == '~' || b == '.' ||
                        b == '(' || b == ')') {
                    sb.append((char) b);
                } else {
                    sb.append(String.format("%%%02X", b & 0xFF));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return fileName;
        }
    }

    @Operation(summary = "上传字幕文件", description = "为指定视频文件上传字幕")
    @SaCheckRole("super-admin")
    @PostMapping("/upload")
    public ApiResponseVO<MediaSubtitle> uploadSubtitle(
            @Parameter(description = "视频文件ID") @RequestParam Long mediaFileId,
            @Parameter(description = "字幕文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "轨道名称") @RequestParam(required = false) String trackName,
            @Parameter(description = "语言") @RequestParam(required = false) String language) {
        try {
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findById(mediaFileId);
            if (!mediaFileOpt.isPresent()) {
                return ApiResponseVO.fail("视频文件不存在");
            }

            MediaFile mediaFile = mediaFileOpt.get();
            String originName = file.getOriginalFilename();
            if (originName == null || originName.isEmpty()) {
                return ApiResponseVO.fail("文件名无效");
            }

            // 验证文件格式
            String ext = getFileExtension(originName);
            if (!isValidSubtitleExtension(ext)) {
                return ApiResponseVO.fail("不支持的字幕格式");
            }

            // 保存到字幕目录
            Path outputDir = Paths.get(subtitleOutputDir);
            Files.createDirectories(outputDir);

            String fileName = mediaFileId + "_uploaded_" + System.currentTimeMillis() + "." + ext;
            Path outputPath = outputDir.resolve(fileName);
            Files.copy(file.getInputStream(), outputPath, StandardCopyOption.REPLACE_EXISTING);

            // 保存到数据库
            MediaSubtitle subtitle = new MediaSubtitle();
            subtitle.setMediaFile(mediaFile);
            subtitle.setStreamIndex(null);
            subtitle.setTrackName(trackName != null ? trackName : "Uploaded");
            subtitle.setLanguage(language);
            subtitle.setCodecName(null);
            subtitle.setSubtitleFormat(ext);
            subtitle.setFileName(fileName);
            subtitle.setFilePath(outputPath.toAbsolutePath().toString());
            subtitle.setFileSize(Files.size(outputPath));
            subtitle.setIsExternal(true);
            subtitle.setSourceType("UPLOADED");
            subtitle.setTimeOffset(0L);

            MediaSubtitle saved = mediaSubtitleRepository.save(subtitle);
            return ApiResponseVO.success(saved);
        } catch (IOException e) {
            return ApiResponseVO.fail("文件上传失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除字幕", description = "删除指定的字幕文件关联")
    @SaCheckRole("super-admin")
    @DeleteMapping("/{id}")
    public ApiResponseVO<Void> deleteSubtitle(@Parameter(description = "字幕ID") @PathVariable Long id) {
        try {
            Optional<MediaSubtitle> subtitleOpt = mediaSubtitleRepository.findById(id);
            if (!subtitleOpt.isPresent()) {
                return ApiResponseVO.fail("字幕不存在");
            }

            MediaSubtitle subtitle = subtitleOpt.get();
            
            // 删除文件（只删除上传的字幕文件，外部字幕和内嵌字幕不删除物理文件）
            if ("UPLOADED".equals(subtitle.getSourceType())) {
                try {
                    Files.deleteIfExists(Paths.get(subtitle.getFilePath()));
                } catch (IOException e) {
                    // 忽略删除文件失败的错误
                }
            }

            mediaSubtitleRepository.delete(subtitle);
            return ApiResponseVO.success(null, "删除成功");
        } catch (Exception e) {
            return ApiResponseVO.fail("删除失败: " + e.getMessage());
        }
    }

    @Operation(summary = "设置字幕偏移量", description = "设置字幕的时间偏移量（毫秒）")
    @SaCheckRole("super-admin")
    @PutMapping("/{id}/offset")
    public ApiResponseVO<MediaSubtitleDTO> setSubtitleOffset(
            @Parameter(description = "字幕ID") @PathVariable Long id,
            @Parameter(description = "时间偏移量（毫秒）") @RequestParam Long offset) {
        try {
            Optional<MediaSubtitle> subtitleOpt = mediaSubtitleRepository.findById(id);
            if (!subtitleOpt.isPresent()) {
                return ApiResponseVO.fail("字幕不存在");
            }

            MediaSubtitle subtitle = subtitleOpt.get();
            subtitle.setTimeOffset(offset);
            MediaSubtitle saved = mediaSubtitleRepository.save(subtitle);
            return ApiResponseVO.success(toDTO(saved));
        } catch (Exception e) {
            return ApiResponseVO.fail("设置失败: " + e.getMessage());
        }
    }

    @Operation(summary = "重新扫描视频字幕", description = "重新扫描指定视频文件的外部字幕")
    @SaCheckRole("super-admin")
    @PostMapping("/rescan/{mediaFileId}")
    public ApiResponseVO<Void> rescanSubtitles(@Parameter(description = "视频文件ID") @PathVariable Long mediaFileId) {
        try {
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findById(mediaFileId);
            if (!mediaFileOpt.isPresent()) {
                return ApiResponseVO.fail("视频文件不存在");
            }

            mediaSubtitleService.scanExternalSubtitles(mediaFileOpt.get());
            return ApiResponseVO.success(null, "重新扫描完成");
        } catch (Exception e) {
            return ApiResponseVO.fail("扫描失败: " + e.getMessage());
        }
    }

    private boolean isValidSubtitleExtension(String ext) {
        return ext.matches("(?i)(srt|ass|ssa|vtt|sub|sbv)");
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    private MediaSubtitleDTO toDTO(MediaSubtitle entity) {
        if (entity == null) {
            return null;
        }

        return MediaSubtitleDTO.builder()
                .id(entity.getId())
                .mediaFileId(entity.getMediaFile() != null ? entity.getMediaFile().getId() : null)
                .streamIndex(entity.getStreamIndex())
                .trackName(entity.getTrackName())
                .language(entity.getLanguage())
                .codecName(entity.getCodecName())
                .subtitleFormat(entity.getSubtitleFormat())
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .fileSize(entity.getFileSize())
                .timeOffset(entity.getTimeOffset())
                .isExternal(entity.getIsExternal())
                .sourceType(entity.getSourceType())
                .build();
    }
}
