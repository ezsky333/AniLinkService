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

import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.repository.MediaSubtitleRepository;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
}
